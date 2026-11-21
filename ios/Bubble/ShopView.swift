import BubbleCore
import SwiftUI

/// 상점: 💰 포인트 충전(App Store), 💖 부활물약, ×3 부스터, 동물 친구들 (안드로이드 ShopScreen과 같은 구성)
struct ShopView: View {
    @ObservedObject var online = OnlineService.shared
    @ObservedObject var store = PointStore.shared
    @ObservedObject var ads = Ads.shared
    let onClose: () -> Void

    @State private var confirm: (item: String, label: String, price: Int)? = nil

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                HStack {
                    Button(t("← 로비"), action: onClose).foregroundColor(UiColor.text)
                    Spacer()
                    if let p = online.profile {
                        Text(t("내 점수 ")).font(.system(size: 14)).foregroundColor(UiColor.text)
                        Text(pts(p.score)).font(.system(size: 18, weight: .black)).foregroundColor(UiColor.primaryDark)
                        Text("  💰 \(p.coins)").font(.system(size: 18, weight: .black)).foregroundColor(Color(hex: 0xC88A00))
                    }
                }
                Text(t("🛒 상점")).font(.system(size: 34, weight: .black)).foregroundColor(UiColor.primaryDark)
                Text(t("💰 상점 포인트를 먼저 쓰고, 모자라면 점수에서 써요 (점수가 줄면 계급이 내려갈 수 있어요)"))
                    .font(.system(size: 12)).foregroundColor(UiColor.text.opacity(0.7)).multilineTextAlignment(.center)
                if let i = online.info {
                    Text(i).font(.system(size: 15, weight: .bold)).foregroundColor(UiColor.accent).multilineTextAlignment(.center)
                }
                if let e = online.error {
                    Text(e).font(.system(size: 14, weight: .bold)).foregroundColor(UiColor.danger).multilineTextAlignment(.center)
                }
                if let p = online.profile {
                    pointsCard
                    itemUsageCard
                    shopCard(title: t("💖 부활물약"),
                             desc: tf("내 차례에 터진(0마리) 방울 하나를 1마리로 살려요.\n한 판에 최대 {0}번 · 쓸 때마다 1개 소모 · 온라인 대전 전용", Shop.maxRevivesPerGame),
                             price: Shop.potionPrice, owned: tf("보유 {0}개", p.potions), art: AnyView(PotionArt().frame(width: 84, height: 84))) {
                        VStack(alignment: .leading, spacing: 6) {
                            buyButton("POTION", t("부활물약"), Shop.potionPrice, p)
                            potionAdButton(p)
                        }
                    }
                    shopCard(title: t("×3 점수 부스터"),
                             desc: t("켜두면 다음 랜덤 매칭에서 1개를 쓰고, 이기면 얻는 점수가 3배! (AI 대전에는 쓰지 않아요)"),
                             price: Shop.boostPrice,
                             owned: p.boosts > 0 ? tf("보유 {0}개", p.boosts) + (p.boostOn ? t("  ·  🔥 사용 중") : "") : nil,
                             art: AnyView(BoostArt())) {
                        HStack(spacing: 6) {
                            buyButton("BOOST", t("×3 부스터"), Shop.boostPrice, p)
                            if p.boosts > 0 {
                                outlineButton(p.boostOn ? t("끄기") : t("켜기")) { Task { await online.setBoost(!p.boostOn) } }
                            }
                        }
                    }
                    Text(t("🐾 동물 친구들")).font(.system(size: 20, weight: .black)).foregroundColor(UiColor.text)
                        .frame(maxWidth: .infinity, alignment: .leading).padding(.top, 6)
                    Text(t("소모품: 1개 = 랜덤 매칭 1판. 장착한 채 매칭되면 1개를 쓰고, 그 판에 내 캐릭터가 바뀌고 상대의 턴이 짧아져요"))
                        .font(.system(size: 12)).foregroundColor(UiColor.text.opacity(0.7))
                        .frame(maxWidth: .infinity, alignment: .leading)
                    // 무료 햄스터: 사지 않고 바로 장착 (모습만 바뀌고 효과 없음)
                    let hamOn = p.equipped == .hamster
                    shopCard(title: tr("🐹 햄스터", "🐹 Hamster", zh: "🐹 仓鼠", es: "🐹 Hámster", fr: "🐹 Hamster", de: "🐹 Hamster", pt: "🐹 Hamster", ru: "🐹 Хомяк", ja: "🐹 ハムスター"),
                             desc: tr("🎁 무료! 내 캐릭터를 햄스터로 바꿔요.\n효과는 없고 몇 판을 해도 줄지 않아요 (모든 대전에서 모습만)",
                                      "🎁 Free! Turn your character into a hamster.\nNo effect and never used up (look only, in every battle)",
                                      zh: "🎁 免费！把你的角色换成仓鼠。\n没有效果，也不会消耗（所有对战中仅外观）",
                                      es: "🎁 ¡Gratis! Convierte tu personaje en hámster.\nSin efecto y nunca se gasta (solo aspecto, en todas las batallas)",
                                      fr: "🎁 Gratuit ! Transforme ton perso en hamster.\nAucun effet, jamais consommé (look seulement, dans tous les combats)",
                                      de: "🎁 Gratis! Mach deine Figur zum Hamster.\nKein Effekt, wird nie verbraucht (nur Look, in jedem Duell)",
                                      pt: "🎁 Grátis! Transforme seu personagem em hamster.\nSem efeito e nunca acaba (só visual, em todas as batalhas)",
                                      ru: "🎁 Бесплатно! Превратите персонажа в хомяка.\nБез эффекта и не тратится (только вид, в любом бою)",
                                      ja: "🎁 無料！自分のキャラをハムスターに。\n効果なし・減りません（どの対戦でも見た目だけ）"),
                             price: nil,
                             owned: hamOn ? tr("✅ 장착 중", "✅ Equipped", zh: "✅ 已装备", es: "✅ Equipado", fr: "✅ Équipé", de: "✅ Ausgerüstet", pt: "✅ Equipado", ru: "✅ Надето", ja: "✅ 装備中") : nil,
                             art: AnyView(Critter(side: .hamster, skin: .hamster, happy: hamOn).frame(width: 84, height: 84))) {
                        if hamOn {
                            outlineButton(t("장착 해제")) { Task { await online.equip(nil) } }
                        } else {
                            Button { Task { await online.equip(.hamster) } } label: {
                                Text(tr("무료로 장착", "Equip for free", zh: "免费装备", es: "Equipar gratis", fr: "Équiper gratuitement", de: "Gratis ausrüsten", pt: "Equipar grátis", ru: "Надеть бесплатно", ja: "無料で装備"))
                                    .font(.system(size: 14, weight: .bold)).foregroundColor(.white)
                                    .padding(.horizontal, 12).padding(.vertical, 8)
                                    .background(Capsule().fill(UiColor.accent))
                            }
                            .disabled(online.busy)
                        }
                    }
                    skinsSection(p)
                    Text(tr("🐾 소모품 동물", "🐾 Consumable animals", zh: "🐾 消耗品动物", es: "🐾 Animales consumibles", fr: "🐾 Animaux consommables",
                            de: "🐾 Verbrauchs-Tiere", pt: "🐾 Animais consumíveis", ru: "🐾 Расходные зверята", ja: "🐾 消耗品どうぶつ"))
                        .font(.system(size: 17, weight: .black)).foregroundColor(UiColor.text)
                        .frame(maxWidth: .infinity, alignment: .leading).padding(.top, 8)
                    ForEach(Animal.allCases.filter { $0.consumable }, id: \.self) { a in
                        let count = p.animalCount(a)
                        let on = p.equipped == a && count > 0
                        shopCard(title: a.label,
                                 desc: tf("⏱ 상대 턴 시간 {0}초 → {1}초", Shop.defaultTurnSec, a.opponentTurnSec),
                                 price: a.price,
                                 owned: count > 0 ? tf("보유 {0}개", count) + (on ? t("  ·  ✅ 장착 중") : "") : nil,
                                 art: AnyView(Critter(side: .cat, skin: a, happy: on).frame(width: 84, height: 84))) {
                            HStack(spacing: 6) {
                                buyButton(a.rawValue, a.label, a.price, p)
                                if on {
                                    outlineButton(t("장착 해제")) { Task { await online.equip(nil) } }
                                } else if count > 0 {
                                    Button { Task { await online.equip(a) } } label: {
                                        Text(t("장착하기")).font(.system(size: 14, weight: .bold)).foregroundColor(.white)
                                            .padding(.horizontal, 12).padding(.vertical, 8)
                                            .background(Capsule().fill(UiColor.accent))
                                    }
                                    .disabled(online.busy)
                                }
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
        }
        .onAppear {
            online.error = nil
            Task { await store.loadProducts() }
        }
        .alert(confirm.map { tf("{0} 살까요?", $0.label) } ?? "", isPresented: Binding(get: { confirm != nil }, set: { if !$0 { confirm = nil } })) {
            Button(t("취소"), role: .cancel) { confirm = nil }
            Button(t("살게요")) {
                if let c = confirm { Task { await online.buy(c.item, label: c.label) } }
                confirm = nil
            }
        } message: {
            Text(confirmText)
        }
    }

    /// 상점 포인트 먼저, 모자라면 점수 (서버 splitPayment와 같은 규칙)
    private var confirmText: String {
        guard let c = confirm, let p = online.profile else { return "" }
        let fromCoins = min(max(p.coins, 0), c.price)
        let fromScore = c.price - fromCoins
        let after = p.score - fromScore
        var text = tf("💰 상점 포인트 {0} + 점수 {1} 사용\n남는 점수: {2}점", fromCoins, fromScore, after)
        if Rank.of(after) != p.rank {
            text += "\n\n" + tf("⚠️ 계급이 {0} → {1}(으)로 내려가요", p.rank.label, Rank.of(after).label)
        }
        return text
    }

    /// 아이템이 어떤 대전에서 효과가 있는지 한눈에 (안드로이드 ItemUsageCard와 같은 내용)
    private var itemUsageCard: some View {
        let yes = Color(hex: 0x2E9E5B)
        let on = tr("✓ 효과", "✓ Works", zh: "✓ 有效", es: "✓ Sí", fr: "✓ Oui", de: "✓ Ja", pt: "✓ Sim", ru: "✓ Да", ja: "✓ 効果")
        let off = tr("— 없음", "— None", zh: "— 无", es: "— No", fr: "— Non", de: "— Nein", pt: "— Não", ru: "— Нет", ja: "— なし")
        let look = tr("👀 모습만", "👀 Look only", zh: "👀 仅外观", es: "👀 Solo look", fr: "👀 Look seul", de: "👀 Nur Look", pt: "👀 Só visual", ru: "👀 Только вид", ja: "👀 見た目だけ")
        let rows: [(String, [Bool?])] = [
            (tr("💖 부활물약", "💖 Potion", zh: "💖 药水", es: "💖 Poción", fr: "💖 Potion", de: "💖 Trank", pt: "💖 Poção", ru: "💖 Зелье", ja: "💖 復活薬"), [true, true, true]),
            (tr("🚀 ×3 부스터", "🚀 x3 Booster", zh: "🚀 ×3 加成", es: "🚀 x3", fr: "🚀 Booster x3", de: "🚀 x3-Booster", pt: "🚀 x3", ru: "🚀 Бустер x3", ja: "🚀 ×3"), [true, false, false]),
            (tr("🐾 동물 친구", "🐾 Animals", zh: "🐾 动物", es: "🐾 Animales", fr: "🐾 Animaux", de: "🐾 Tiere", pt: "🐾 Animais", ru: "🐾 Зверята", ja: "🐾 どうぶつ"), [true, nil, false]),
        ]
        let heads = [
            tr("🎲 랜덤", "🎲 Random", zh: "🎲 随机", es: "🎲 Aleatoria", fr: "🎲 Aléatoire", de: "🎲 Zufall", pt: "🎲 Aleatória", ru: "🎲 Случайный", ja: "🎲 ランダム"),
            tr("🤖 AI", "🤖 AI", es: "🤖 IA", fr: "🤖 IA", de: "🤖 KI", pt: "🤖 IA", ru: "🤖 ИИ"),
            tr("👫 친구", "👫 Friend", zh: "👫 好友", es: "👫 Amigo", fr: "👫 Ami", de: "👫 Freund", pt: "👫 Amigo", ru: "👫 Друг", ja: "👫 友だち"),
        ]
        func cell(_ c: Bool?) -> some View {
            let text = c == true ? on : c == false ? off : look
            let fg = c == true ? yes : c == false ? UiColor.text.opacity(0.35) : UiColor.accent
            let bg = c == true ? yes.opacity(0.13) : c == false ? UiColor.text.opacity(0.06) : UiColor.accent.opacity(0.12)
            return Text(text).font(.system(size: 12, weight: .black)).foregroundColor(fg).lineLimit(1).minimumScaleFactor(0.7)
                .frame(maxWidth: .infinity).padding(.vertical, 6)
                .background(RoundedRectangle(cornerRadius: 10).fill(bg))
                .padding(.horizontal, 3)
        }
        return card {
            Text(tr("✨ 아이템은 어디서 효과가 있나요?", "✨ Where do items work?", zh: "✨ 道具在哪里有效？", es: "✨ ¿Dónde funcionan los objetos?",
                    fr: "✨ Où marchent les objets ?", de: "✨ Wo wirken Items?", pt: "✨ Onde os itens funcionam?", ru: "✨ Где работают предметы?",
                    ja: "✨ アイテムはどこで効く？"))
                .font(.system(size: 16, weight: .black)).foregroundColor(UiColor.text)
                .frame(maxWidth: .infinity, alignment: .leading)
            HStack(spacing: 0) {
                Color.clear.frame(maxWidth: .infinity, maxHeight: 1)
                ForEach(heads, id: \.self) { h in
                    Text(h).font(.system(size: 11, weight: .bold)).foregroundColor(UiColor.text.opacity(0.75))
                        .lineLimit(1).minimumScaleFactor(0.7).frame(maxWidth: .infinity)
                }
            }
            ForEach(0..<rows.count, id: \.self) { r in
                let row = rows[r]
                HStack(spacing: 0) {
                    Text(row.0).font(.system(size: 13, weight: .bold)).foregroundColor(UiColor.text)
                        .lineLimit(1).minimumScaleFactor(0.7).frame(maxWidth: .infinity, alignment: .leading)
                    ForEach(0..<3, id: \.self) { i in cell(row.1[i]) }
                }
            }
            Text(tr("🤖 AI 대전과 👫 친구 대전에서는 부스터와 동물이 쓰이지 않아요. 개수도 줄지 않으니 안심하세요!",
                    "🤖 AI and 👫 friend battles don't use boosters or animals, so you won't lose any!",
                    zh: "🤖 AI 对战和 👫 好友对战不会使用加成和动物，数量也不会减少，放心吧！",
                    es: "🤖 En batallas contra la IA y 👫 entre amigos no se usan potenciadores ni animales, ¡no pierdes ninguno!",
                    fr: "🤖 Les combats contre l’IA et 👫 entre amis n’utilisent ni boosters ni animaux : tu n’en perds aucun !",
                    de: "🤖 KI- und 👫 Freundesduelle verbrauchen keine Booster oder Tiere – du verlierst also nichts!",
                    pt: "🤖 Batalhas contra a IA e 👫 entre amigos não usam impulsos nem animais, então você não perde nenhum!",
                    ru: "🤖 В боях с ИИ и 👫 с друзьями бустеры и зверята не тратятся — ничего не потеряете!",
                    ja: "🤖 AI対戦と 👫 友だち対戦ではブースターとどうぶつは使われません。数も減らないので安心！"))
                .font(.system(size: 12)).foregroundColor(UiColor.text.opacity(0.75))
                .frame(maxWidth: .infinity, alignment: .leading).padding(.top, 4)
        }
    }

    private var pointsCard: some View {
        card {
            Text(t("💰 포인트 충전")).font(.system(size: 20, weight: .black)).foregroundColor(UiColor.text)
                .frame(maxWidth: .infinity, alignment: .leading)
            Text(t("결제한 포인트는 💰 상점 포인트로 들어가요. 상점 전용이라 계급·랭킹 점수와는 별개예요."))
                .font(.system(size: 12)).foregroundColor(UiColor.text.opacity(0.75))
                .frame(maxWidth: .infinity, alignment: .leading)
            if firstBuy {
                Text(tr("🎁 첫 구매 2배! 처음 충전하는 포인트는 두 배로 드려요", "🎁 First purchase x2! Your first top-up is doubled",
                        zh: "🎁 首充双倍！第一次充值的积分翻倍", es: "🎁 ¡Primera compra x2! Tu primera recarga vale el doble",
                        fr: "🎁 Premier achat x2 ! Ta première recharge est doublée", de: "🎁 Erster Kauf x2! Deine erste Aufladung wird verdoppelt",
                        pt: "🎁 Primeira compra x2! Sua primeira recarga vale em dobro", ru: "🎁 Первая покупка x2! Первое пополнение удваивается",
                        ja: "🎁 初回購入2倍！最初のチャージは2倍になります"))
                    .font(.system(size: 14, weight: .black)).foregroundColor(UiColor.danger)
                    .padding(8).frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: 12).fill(UiColor.danger.opacity(0.08)))
            }
            // 상품 4개: 2칸씩 2줄
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
                ForEach(PointStore.products, id: \.self) { prod in
                    Button { Task { await store.buy(prod.id) } } label: {
                        VStack(spacing: 2) {
                            if store.purchasing == prod.id {
                                ProgressView()
                            } else {
                                Text("💰 \(prod.points)").font(.system(size: 17, weight: .black))
                            }
                            Text(store.prices[prod.id] ?? "₩\(prod.points)").font(.system(size: 13, weight: .bold))
                            if firstBuy {
                                Text("🎁 → 💰 \(prod.points * 2)").font(.system(size: 11, weight: .black)).foregroundColor(UiColor.danger)
                            }
                        }
                        .foregroundColor(UiColor.text)
                        .frame(maxWidth: .infinity, minHeight: 56)
                        .background(RoundedRectangle(cornerRadius: 16).fill(UiColor.gold))
                    }
                    .disabled(store.purchasing != nil)
                }
            }
            removeAdsRow
        }
    }

    private var firstBuy: Bool { online.profile?.firstBuyDone == false }

    /// 🚫 광고 제거 (비소모품) + 구매 복원
    @ViewBuilder
    private var removeAdsRow: some View {
        if online.profile?.noAds == true {
            Text(tr("✅ 광고 제거됨 · 전면 광고가 나오지 않아요", "✅ Ads removed · no full-screen ads", zh: "✅ 已去除广告", es: "✅ Anuncios eliminados",
                    fr: "✅ Pubs supprimées", de: "✅ Werbung entfernt", pt: "✅ Anúncios removidos", ru: "✅ Реклама отключена", ja: "✅ 広告削除済み"))
                .font(.system(size: 13, weight: .bold)).foregroundColor(UiColor.accent)
                .frame(maxWidth: .infinity, alignment: .leading).padding(.top, 6)
        } else {
            Button { Task { await store.buy(PointStore.removeAds) } } label: {
                VStack(spacing: 2) {
                    Text(tr("🚫 광고 제거", "🚫 Remove ads", zh: "🚫 去除广告", es: "🚫 Quitar anuncios", fr: "🚫 Supprimer les pubs",
                            de: "🚫 Werbung entfernen", pt: "🚫 Remover anúncios", ru: "🚫 Убрать рекламу", ja: "🚫 広告を削除")
                         + "  " + (store.prices[PointStore.removeAds] ?? "₩3,000"))
                        .font(.system(size: 15, weight: .black)).foregroundColor(UiColor.text)
                    Text(tr("한 번 사면 계속 · 게임 사이 전면 광고 없음 (원할 때 보는 보상 광고는 그대로)",
                            "One-time · no ads between games (optional reward ads stay)",
                            zh: "一次购买永久有效 · 对局间不再有全屏广告（自愿观看的奖励广告保留）",
                            es: "Pago único · sin anuncios entre partidas (los anuncios con recompensa opcionales siguen)",
                            fr: "Achat unique · plus de pubs entre les parties (les pubs récompensées facultatives restent)",
                            de: "Einmalig · keine Werbung zwischen Partien (freiwillige Belohnungs-Werbung bleibt)",
                            pt: "Compra única · sem anúncios entre partidas (anúncios opcionais com recompensa continuam)",
                            ru: "Один раз · без рекламы между играми (добровольная реклама за награду остаётся)",
                            ja: "買い切り · 対戦の合間の広告なし（任意のリワード広告は残ります）"))
                        .font(.system(size: 11)).foregroundColor(UiColor.text.opacity(0.7)).multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity).padding(.vertical, 8)
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(UiColor.text.opacity(0.35)))
            }
            .disabled(store.purchasing != nil)
            .padding(.top, 6)
        }
        Button(tr("구매 복원", "Restore purchases", zh: "恢复购买", es: "Restaurar compras", fr: "Restaurer les achats",
                  de: "Käufe wiederherstellen", pt: "Restaurar compras", ru: "Восстановить покупки", ja: "購入を復元")) {
            Task { await store.restore() }
        }
        .font(.system(size: 12)).foregroundColor(UiColor.text.opacity(0.6))
    }

    /// 💖 부활물약: 광고 보고 1개 받기 (하루 3번). 시청 확인은 AdMob → 서버
    private func potionAdButton(_ p: Profile) -> some View {
        let used = p.potionAdDay == OnlineService.today() ? p.potionAds : 0
        let left = max(0, OnlineService.potionAdsPerDay - used)
        let total = OnlineService.potionAdsPerDay
        let label = left > 0
            ? tr("📺 광고 보고 1개 받기 (\(left)/\(total))", "📺 Watch ad for 1 (\(left)/\(total))", zh: "📺 看广告领 1 个（\(left)/\(total)）",
                 es: "📺 Anuncio = 1 gratis (\(left)/\(total))", fr: "📺 Pub = 1 gratuite (\(left)/\(total))", de: "📺 Werbung = 1 gratis (\(left)/\(total))",
                 pt: "📺 Anúncio = 1 grátis (\(left)/\(total))", ru: "📺 Реклама = 1 даром (\(left)/\(total))", ja: "📺 広告を見て1個もらう（\(left)/\(total)）")
            : tr("📺 오늘은 다 받았어요 (내일 또!)", "📺 All used today (more tomorrow!)", zh: "📺 今天已领完（明天再来！）", es: "📺 Hoy ya no quedan",
                 fr: "📺 Plus rien aujourd’hui", de: "📺 Heute aufgebraucht", pt: "📺 Acabou por hoje", ru: "📺 На сегодня всё", ja: "📺 今日はもう受け取りました")
        return outlineButton(label) {
            guard let uid = online.uid else { return }
            ads.showRewarded(uid: uid, gameId: "potion", earned: {
                online.show(tr("📺 확인 중… 곧 💖 부활물약이 들어와요", "📺 Checking… your 💖 potion arrives soon", zh: "📺 确认中… 💖 复活药水马上到账",
                               es: "📺 Comprobando… tu 💖 poción llega pronto", fr: "📺 Vérification… ta 💖 potion arrive", de: "📺 Wird geprüft… dein 💖 Trank kommt gleich",
                               pt: "📺 Verificando… sua 💖 poção chega logo", ru: "📺 Проверяем… 💖 зелье скоро придёт", ja: "📺 確認中… まもなく 💖 復活薬が届きます"))
            }, done: {})
        }
        .disabled(!ads.rewardedReady || left == 0)
    }

    /// ✨ 유료 스킨 (한 번 사면 계속, 모습만)
    @ViewBuilder
    private func skinsSection(_ p: Profile) -> some View {
        Text(tr("✨ 스킨 (한 번 사면 계속)", "✨ Skins (yours forever)", zh: "✨ 皮肤（买一次永久拥有）", es: "✨ Aspectos (para siempre)",
                fr: "✨ Skins (à vie)", de: "✨ Skins (für immer)", pt: "✨ Skins (para sempre)", ru: "✨ Скины (навсегда)", ja: "✨ スキン（買い切り）"))
            .font(.system(size: 20, weight: .black)).foregroundColor(UiColor.text)
            .frame(maxWidth: .infinity, alignment: .leading).padding(.top, 6)
        Text(tr("모습만 바뀌고 효과는 없어요. 모든 대전에서 보여요", "Look only, no effect. Shown in every battle",
                zh: "只改变外观，没有效果。所有对战都会显示", es: "Solo aspecto, sin efecto. Se ve en todas las batallas",
                fr: "Look seulement, aucun effet", de: "Nur Look, kein Effekt", pt: "Só visual, sem efeito", ru: "Только вид, без эффекта",
                ja: "見た目だけで効果なし。すべての対戦で表示"))
            .font(.system(size: 12)).foregroundColor(UiColor.text.opacity(0.7))
            .frame(maxWidth: .infinity, alignment: .leading)
        ForEach(Animal.allCases.filter { $0.cosmetic }, id: \.self) { a in
            skinCard(a, p)
        }
    }

    private func skinCard(_ a: Animal, _ p: Profile) -> some View {
        let have = p.skins.contains(a)
        let on = p.equipped == a
        let owned: String? = on ? tr("✅ 장착 중", "✅ Equipped", zh: "✅ 已装备", es: "✅ Equipado", fr: "✅ Équipé", de: "✅ Ausgerüstet", pt: "✅ Equipado", ru: "✅ Надето", ja: "✅ 装備中")
            : have ? tr("💎 보유 중", "💎 Owned", zh: "💎 已拥有", es: "💎 Tuyo", fr: "💎 Possédé", de: "💎 Im Besitz", pt: "💎 Seu", ru: "💎 Есть", ja: "💎 所持中") : nil
        return shopCard(title: a.label,
                        desc: tr("✨ 영구 스킨 · 효과 없음", "✨ Permanent skin · no effect", zh: "✨ 永久皮肤 · 无效果", es: "✨ Aspecto permanente · sin efecto",
                                 fr: "✨ Skin permanent · aucun effet", de: "✨ Dauerhafter Skin · kein Effekt", pt: "✨ Skin permanente · sem efeito",
                                 ru: "✨ Навсегда · без эффекта", ja: "✨ 永久スキン · 効果なし"),
                        price: have ? nil : a.price, owned: owned,
                        art: AnyView(Critter(side: a.looksLikeCat ? .cat : .hamster, skin: a, happy: on).frame(width: 84, height: 84))) {
            if on {
                outlineButton(t("장착 해제")) { Task { await online.equip(nil) } }
            } else if have {
                Button { Task { await online.equip(a) } } label: {
                    Text(t("장착하기")).font(.system(size: 14, weight: .bold)).foregroundColor(.white)
                        .padding(.horizontal, 12).padding(.vertical, 8)
                        .background(Capsule().fill(UiColor.accent))
                }
                .disabled(online.busy)
            } else {
                buyButton(a.rawValue, a.label, a.price, p)
            }
        }
    }

    private func buyButton(_ item: String, _ label: String, _ price: Int, _ p: Profile) -> some View {
        Button { confirm = (item, label, price) } label: {
            Text(tf("{0}점에 사기", price)).font(.system(size: 14, weight: .bold)).foregroundColor(.white)
                .lineLimit(1).minimumScaleFactor(0.7)
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(Capsule().fill(UiColor.primary.opacity(p.score + p.coins >= price ? 1 : 0.4)))
        }
        .disabled(online.busy || p.score + p.coins < price)
    }

    private func outlineButton(_ label: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(.system(size: 14)).foregroundColor(UiColor.text)
                .padding(.horizontal, 12).padding(.vertical, 8)
                .overlay(Capsule().stroke(UiColor.text.opacity(0.4)))
        }
        .disabled(online.busy)
    }

    private func shopCard<A: View>(title: String, desc: String, price: Int?, owned: String?, art: AnyView, @ViewBuilder action: () -> A) -> some View {
        card {
            HStack(alignment: .center, spacing: 12) {
                art
                    .frame(width: 96, height: 96)
                    .background(RoundedRectangle(cornerRadius: 24).fill(UiColor.bgTop))
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(title).font(.system(size: 20, weight: .black)).foregroundColor(UiColor.text).lineLimit(1).minimumScaleFactor(0.7)
                        if let price {
                            Text(pts(price)).font(.system(size: 14, weight: .bold)).foregroundColor(UiColor.primaryDark)
                        }
                    }
                    Text(desc).font(.system(size: 13)).foregroundColor(UiColor.text.opacity(0.8)).fixedSize(horizontal: false, vertical: true)
                    if let owned {
                        Text(owned).font(.system(size: 13, weight: .bold)).foregroundColor(UiColor.accent)
                    }
                    action().padding(.top, 4)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }
}

/// 둥근 카드 (로비·상점 공통)
func card<C: View>(@ViewBuilder _ content: () -> C) -> some View {
    VStack(spacing: 8) { content() }
        .padding(16)
        .frame(maxWidth: .infinity)
        .background(RoundedRectangle(cornerRadius: 24).fill(UiColor.card))
        .shadow(color: .black.opacity(0.06), radius: 3, y: 1)
}

/// 하트 모양 병에 담긴 부활물약 (흔들흔들 + 반짝)
struct PotionArt: View {
    var body: some View {
        TimelineView(.animation) { tl in
            let time = tl.date.timeIntervalSinceReferenceDate
            let wobble = sin(time * 2 * .pi / 1.8) * 5
            let tw = (time / 1.2).truncatingRemainder(dividingBy: 1)
            Canvas { ctx, size in
                let w = size.width, h = size.height
                let c = CGPoint(x: w / 2, y: h * 0.6)
                let r = w * 0.3
                ctx.fill(Path(CGRect(x: w * 0.42, y: h * 0.18, width: w * 0.16, height: h * 0.2)), with: .color(Color(hex: 0xE3F2FD)))
                ctx.fill(Path(roundedRect: CGRect(x: w * 0.4, y: h * 0.1, width: w * 0.2, height: h * 0.1), cornerRadius: 3), with: .color(Color(hex: 0xB7794A)))
                let bottle = Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
                ctx.fill(bottle, with: .color(Color(hex: 0xE3F2FD)))
                var heart = Path()
                let hx = c.x, hy = c.y - r * 0.15, k = r * 0.7
                heart.move(to: CGPoint(x: hx, y: hy + k * 0.9))
                heart.addCurve(to: CGPoint(x: hx, y: hy - k * 0.3), control1: CGPoint(x: hx - k * 1.3, y: hy), control2: CGPoint(x: hx - k * 0.6, y: hy - k * 0.9))
                heart.addCurve(to: CGPoint(x: hx, y: hy + k * 0.9), control1: CGPoint(x: hx + k * 0.6, y: hy - k * 0.9), control2: CGPoint(x: hx + k * 1.3, y: hy))
                ctx.fill(heart, with: .color(Color(hex: 0xFF5C8A)))
                let shine = r * 0.14
                ctx.fill(Path(ellipseIn: CGRect(x: c.x - r * 0.45 - shine, y: c.y - r * 0.45 - shine, width: shine * 2, height: shine * 2)), with: .color(.white.opacity(0.8)))
                ctx.stroke(bottle, with: .color(.white), lineWidth: 2)
                let a = sin(tw * .pi)
                ctx.fill(sparklePath(CGPoint(x: c.x + r * 0.9, y: c.y - r * 0.9), 5 * a + 1), with: .color(UiColor.gold.opacity(a)))
                ctx.fill(sparklePath(CGPoint(x: c.x - r, y: c.y + r * 0.2), 3.5 * (1 - a) + 1), with: .color(.white.opacity(1 - a)))
            }
            .rotationEffect(.degrees(wobble))
        }
    }
}

/// ×3 부스터 그림 (로켓 배지)
struct BoostArt: View {
    var body: some View {
        TimelineView(.animation) { tl in
            let bob = sin(tl.date.timeIntervalSinceReferenceDate * 2 * .pi / 1.2) * 4
            ZStack {
                Circle().fill(LinearGradient(colors: [Color(hex: 0xFFE082), Color(hex: 0xFFB300)], startPoint: .top, endPoint: .bottom))
                    .frame(width: 70, height: 70)
                VStack(spacing: -4) {
                    Text("🚀").font(.system(size: 26))
                    Text("×3").font(.system(size: 22, weight: .black)).foregroundColor(Color(hex: 0xB23C00))
                }
            }
            .offset(y: bob)
        }
    }
}
