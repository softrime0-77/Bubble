import CryptoKit
import Foundation
import StoreKit

/// 💰 상점 포인트 충전 (App Store 인앱 결제, StoreKit 2).
/// 결제 → Apple이 서명한 거래(JWS)를 서버(verifyApplePurchase)에 보냄 → 서버가 확인하고 포인트 지급 → 거래 마무리(finish).
/// 서버 확인 전에 앱이 꺼져도 거래가 남아 있어서, 다음 실행 때 다시 보냄.
@MainActor
final class PointStore: ObservableObject {
    static let shared = PointStore()

    /// App Store Connect에 만들 소모성 상품 ID → 포인트 (안드로이드·서버와 같은 ID)
    struct Pack: Hashable {
        let id: String
        let points: Int
    }
    static let products = [Pack(id: "points_500", points: 500), Pack(id: "points_1000", points: 1000), Pack(id: "points_2000", points: 2000), Pack(id: "points_5000", points: 5000)]

    @Published private(set) var prices: [String: String] = [:]
    @Published private(set) var purchasing: String? = nil

    private var loaded: [String: Product] = [:]
    private var updatesTask: Task<Void, Never>? = nil

    private init() {}

    /// 로그인 후 한 번: 가격 불러오기, 밀린 거래 처리, 새 거래 감시
    func start() {
        Task { await loadProducts() }
        guard updatesTask == nil else { return }
        updatesTask = Task {
            for await result in Transaction.updates { await deliver(result) }
        }
        Task {
            for await result in Transaction.unfinished { await deliver(result) }
        }
    }

    func loadProducts() async {
        guard let list = try? await Product.products(for: Self.products.map { $0.id }) else { return }
        for p in list {
            loaded[p.id] = p
            prices[p.id] = p.displayPrice
        }
    }

    func buy(_ id: String) async {
        let online = OnlineService.shared
        guard let uid = online.uid, purchasing == nil else { return }
        if loaded[id] == nil { await loadProducts() }
        guard let product = loaded[id] else {
            online.error = tr("지금은 App Store에서 상품을 불러올 수 없어요. 잠시 후 다시 시도해 주세요",
                              "Can't load products from the App Store right now. Please try again later",
                              zh: "暂时无法从 App Store 加载商品，请稍后再试", es: "Ahora no se pueden cargar los productos de la App Store. Inténtalo más tarde",
                              fr: "Impossible de charger les produits de l’App Store pour l’instant. Réessaie plus tard",
                              de: "Produkte aus dem App Store können gerade nicht geladen werden. Bitte später erneut versuchen",
                              pt: "Não foi possível carregar os produtos da App Store agora. Tente mais tarde",
                              ru: "Сейчас не удаётся загрузить товары из App Store. Попробуйте позже",
                              ja: "現在 App Store から商品を読み込めません。しばらくしてからお試しください")
            return
        }
        purchasing = id
        defer { purchasing = nil }
        do {
            switch try await product.purchase(options: [.appAccountToken(Self.accountToken(uid))]) {
            case let .success(result):
                await deliver(result)
            case .pending:
                online.show(tr("결제 승인을 기다리는 중이에요. 승인되면 포인트가 들어가요",
                               "Waiting for payment approval. Points arrive once it's approved",
                               zh: "正在等待付款批准，批准后积分到账", es: "Esperando la aprobación del pago. Los puntos llegarán al aprobarse",
                               fr: "Paiement en attente d’approbation. Les points arriveront ensuite",
                               de: "Zahlung wartet auf Freigabe. Die Punkte kommen danach",
                               pt: "Aguardando aprovação do pagamento. Os pontos chegam depois",
                               ru: "Ожидаем подтверждения оплаты. Очки придут после него",
                               ja: "支払いの承認待ちです。承認されるとポイントが届きます"))
            case .userCancelled:
                break
            @unknown default:
                break
            }
        } catch {
            online.error = error.localizedDescription
        }
    }

    /// 서버에 거래를 보내 포인트를 받고, 성공하면 거래를 마무리
    private func deliver(_ result: VerificationResult<Transaction>) async {
        let online = OnlineService.shared
        guard online.uid != nil else { return }
        let transaction: Transaction
        switch result {
        case let .verified(t): transaction = t
        case let .unverified(t, _): transaction = t // 최종 확인은 서버가 Apple 서명으로 함
        }
        guard Self.products.contains(where: { $0.id == transaction.productID }) else {
            await transaction.finish()
            return
        }
        do {
            let r = try await online.call("verifyApplePurchase", ["jws": result.jwsRepresentation])
            await transaction.finish()
            let granted = (r["granted"] as? NSNumber)?.intValue ?? 0
            if granted > 0 { online.show(tf("결제 완료! 💰 상점 포인트 +{0} 🎉", granted)) }
        } catch {
            // 다른 계정의 결제 등: 다음에 같은 계정으로 로그인하면 다시 시도됨
            online.error = error.localizedDescription
        }
    }

    /// 결제를 계정에 묶는 표시. 서버 apple.ts appAccountToken과 같은 방법: SHA-256(uid) 앞 16바이트 → UUID v4
    static func accountToken(_ uid: String) -> UUID {
        var b = Array(SHA256.hash(data: Data(uid.utf8)).prefix(16))
        b[6] = (b[6] & 0x0F) | 0x40
        b[8] = (b[8] & 0x3F) | 0x80
        return UUID(uuid: (b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7], b[8], b[9], b[10], b[11], b[12], b[13], b[14], b[15]))
    }
}
