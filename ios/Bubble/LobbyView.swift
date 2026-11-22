import AuthenticationServices
import BubbleCore
import SwiftUI

/// 로비: 언어, 상점, 로그인/프로필, 랜덤 매칭, AI 대전, 친구 초대, 랭킹
struct LobbyView: View {
    @ObservedObject var lang = LangStore.shared
    @ObservedObject var online = OnlineService.shared
    @ObservedObject var ads = Ads.shared
    var onStartAi: (AiLevel) -> Void
    var onRandomMatch: () -> Void
    var onFriendBattle: () -> Void
    var onShop: () -> Void

    @State private var showRules = false
    @State private var cardOf: RankedPlayer?

    /// 닉네임: 첫 변경은 무료, 그다음은 변경권이 있어야
    private var canRename: Bool {
        guard let p = online.profile else { return true }
        return !p.freeRenameUsed || p.renameTickets > 0
    }

    private var renameNote: String {
        guard let p = online.profile, p.freeRenameUsed else {
            return tr("🎁 첫 변경은 무료예요!", "🎁 Your first change is free!", zh: "🎁 第一次修改免费！", es: "🎁 ¡El primer cambio es gratis!",
                      fr: "🎁 Le premier changement est gratuit !", de: "🎁 Die erste Änderung ist gratis!", pt: "🎁 A primeira troca é grátis!", ru: "🎁 Первая смена бесплатно!", ja: "🎁 最初の変更は無料！")
        }
        if p.renameTickets > 0 {
            return tr("✏️ 변경권 1장을 써요 (보유 \(p.renameTickets)장)", "✏️ Uses 1 rename ticket (you have \(p.renameTickets))",
                      zh: "✏️ 使用 1 张改名券（拥有 \(p.renameTickets) 张）", es: "✏️ Usa 1 cupón de cambio (tienes \(p.renameTickets))", fr: "✏️ Utilise 1 ticket (tu en as \(p.renameTickets))",
                      de: "✏️ Nutzt 1 Namensticket (du hast \(p.renameTickets))", pt: "✏️ Usa 1 cupom de troca (você tem \(p.renameTickets))", ru: "✏️ Тратится 1 талон (у вас \(p.renameTickets))", ja: "✏️ 変更券を1枚使います（所持\(p.renameTickets)枚）")
        }
        let price = Shop.renamePrice
        return tr("✏️ 닉네임 변경권이 필요해요 · 상점에서 \(price)점", "✏️ You need a rename ticket · \(price) pts in the shop",
                  zh: "✏️ 需要改名券 · 商店 \(price) 分", es: "✏️ Necesitas un cupón de cambio · \(price) pts en la tienda", fr: "✏️ Il faut un ticket de pseudo · \(price) pts en boutique",
                  de: "✏️ Du brauchst ein Namensticket · \(price) Pkt im Shop", pt: "✏️ Precisa de um cupom de troca · \(price) pts na loja", ru: "✏️ Нужен талон смены ника · \(price) очк. в магазине",
                  ja: "✏️ ニックネーム変更券が必要です · ショップで\(price)点")
    }
    @State private var showRanks = false
    @State private var renaming = false
    @State private var newName = ""
    @State private var confirmDelete = false
    @State private var showAdmin = false

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                HStack(spacing: 8) {
                    Menu {
                        ForEach(LangCode.allCases) { code in
                            Button((code == lang.code ? "✓ " : "") + code.label) { lang.code = code }
                        }
                    } label: {
                        Text("🌐 \(lang.code.label) ▾")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(UiColor.text)
                            .padding(.horizontal, 12).padding(.vertical, 6)
                            .background(Capsule().fill(.white.opacity(0.7)))
                    }
                    // 관리자만: 🛠 포인트 수정
                    if online.profile?.admin == true {
                        Button { showAdmin = true } label: {
                            Text("🛠").font(.system(size: 18))
                                .padding(.horizontal, 10).padding(.vertical, 4)
                                .background(Capsule().fill(.white.opacity(0.75)))
                        }
                    }
                    if let n = online.onlineCount {
                        Text(tr("🟢 \(n)명 접속", "🟢 \(n) online", zh: "🟢 在线 \(n)", es: "🟢 \(n) en línea", fr: "🟢 \(n) en ligne",
                                de: "🟢 \(n) online", pt: "🟢 \(n) online", ru: "🟢 \(n) в сети", ja: "🟢 \(n)人接続中"))
                            .font(.system(size: 13, weight: .black)).foregroundColor(Color(hex: 0x2E7D32))
                            .padding(.horizontal, 10).padding(.vertical, 6)
                            .background(Capsule().fill(.white.opacity(0.75)))
                    }
                    Spacer()
                    if online.profile != nil { ShopButton(action: onShop) }
                }

                TitleBanner()
                Button(t("📖 게임 규칙 보기")) { showRules = true }
                    .font(.system(size: 15)).foregroundColor(UiColor.text)

                card { accountSection }

                Button(action: onRandomMatch) {
                    VStack(spacing: 2) {
                        Text(t("🎲  랜덤 매칭")).font(.system(size: 22, weight: .black))
                        Text(tr("익명의 상대와 1:1 대결 · 승리 +\(Shop.winPoints) · 패배 -\(Shop.losePoints)", "1:1 vs an anonymous player · Win +\(Shop.winPoints) · Lose -\(Shop.losePoints)",
                                zh: "与匿名玩家 1:1 对战 · 胜 +\(Shop.winPoints) · 负 -\(Shop.losePoints)", es: "1:1 contra un jugador anónimo · Gana +\(Shop.winPoints) · Pierde -\(Shop.losePoints)",
                                fr: "1:1 contre un joueur anonyme · Victoire +\(Shop.winPoints) · Défaite -\(Shop.losePoints)", de: "1:1 gegen anonyme Gegner · Sieg +\(Shop.winPoints) · Niederlage -\(Shop.losePoints)",
                                pt: "1:1 contra um jogador anônimo · Vitória +\(Shop.winPoints) · Derrota -\(Shop.losePoints)", ru: "1:1 с анонимным игроком · Победа +\(Shop.winPoints) · Поражение -\(Shop.losePoints)",
                                ja: "匿名の相手と1対1 · 勝ち +\(Shop.winPoints) · 負け -\(Shop.losePoints)"))
                            .font(.system(size: 12))
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity, minHeight: 76)
                    .background(RoundedRectangle(cornerRadius: 28).fill(UiColor.primary.opacity(online.profile == nil ? 0.4 : 1)))
                }
                .disabled(online.profile == nil)

                Button(action: onFriendBattle) {
                    VStack(spacing: 2) {
                        Text(tr("👫  친구와 대전", "👫  Battle a Friend", zh: "👫  好友对战", es: "👫  Reta a un amigo", fr: "👫  Défier un ami",
                                de: "👫  Freund herausfordern", pt: "👫  Desafiar um amigo", ru: "👫  Бой с другом", ja: "👫  友だちと対戦"))
                            .font(.system(size: 19, weight: .black))
                        Text(tr("링크를 보내 1:1 · 승리 +\(Shop.friendWinPoints) · 패배 -\(Shop.friendLosePoints)",
                                "Send a link, play 1:1 · Win +\(Shop.friendWinPoints) · Lose -\(Shop.friendLosePoints)",
                                zh: "发送链接 1:1 · 胜 +\(Shop.friendWinPoints) · 负 -\(Shop.friendLosePoints)",
                                es: "Envía un enlace, 1:1 · Gana +\(Shop.friendWinPoints) · Pierde -\(Shop.friendLosePoints)",
                                fr: "Envoie un lien, 1:1 · Victoire +\(Shop.friendWinPoints) · Défaite -\(Shop.friendLosePoints)",
                                de: "Link senden, 1:1 · Sieg +\(Shop.friendWinPoints) · Niederlage -\(Shop.friendLosePoints)",
                                pt: "Envie um link, 1:1 · Vitória +\(Shop.friendWinPoints) · Derrota -\(Shop.friendLosePoints)",
                                ru: "Отправьте ссылку, 1:1 · Победа +\(Shop.friendWinPoints) · Поражение -\(Shop.friendLosePoints)",
                                ja: "リンクを送って1対1 · 勝ち +\(Shop.friendWinPoints) · 負け -\(Shop.friendLosePoints)"))
                            .font(.system(size: 12))
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity, minHeight: 64)
                    .background(RoundedRectangle(cornerRadius: 24).fill(UiColor.accent.opacity(online.profile == nil ? 0.4 : 1)))
                }
                .disabled(online.profile == nil)

                card {
                    Text(online.profile != nil ? t("🤖 AI 대전") : t("🤖 AI와 연습하기"))
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(UiColor.text)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    HStack(spacing: 8) {
                        ForEach(AiLevel.allCases, id: \.self) { lv in
                            VStack(spacing: 4) {
                                Button { onStartAi(lv) } label: {
                                    Text(lv.label)
                                        .font(.system(size: 15, weight: .medium))
                                        .foregroundColor(UiColor.accent)
                                        .lineLimit(1)
                                        .minimumScaleFactor(0.7)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 12)
                                        .overlay(RoundedRectangle(cornerRadius: 16).stroke(UiColor.text.opacity(0.5)))
                                }
                                if online.profile != nil {
                                    Text("🏆 +\(lv.winPoints)   💔 \(lv.losePoints)")
                                        .font(.system(size: 12, weight: .bold)).foregroundColor(UiColor.text.opacity(0.8)).lineLimit(1)
                                }
                                Text("⏱ " + secs(lv.turnSec))
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(UiColor.accent)
                            }
                        }
                    }
                    if online.profile == nil {
                        Text(t("로그인하면 AI 대전도 점수가 걸려요"))
                            .font(.system(size: 12, weight: .semibold)).foregroundColor(UiColor.text.opacity(0.7))
                    }
                }

                if let p = online.profile { DailyCard(profile: p) }
                if let p = online.profile { InviteCard(profile: p) }
                if let i = online.info {
                    Text(i).font(.system(size: 14, weight: .bold)).foregroundColor(UiColor.accent).multilineTextAlignment(.center)
                }
                if ads.privacyOptionsRequired {
                    Button(t("🔒 광고 개인정보 설정")) { ads.showPrivacyOptions() }
                        .font(.system(size: 13)).foregroundColor(UiColor.text.opacity(0.7))
                }

                if !online.leaderboard.isEmpty {
                    card {
                        Text(t("🏆 랭킹")).font(.system(size: 17, weight: .bold)).foregroundColor(UiColor.text)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text(tr("🎁 매주 월요일 0시 TOP 100 보상 · 1위 💰1000 · 2위 700 · 3위 500 · 4~10위 300 · 11~100위 100",
                                "🎁 Every Monday 00:00 (KST), TOP 100 get 💰: #1 1000 · #2 700 · #3 500 · #4–10 300 · #11–100 100",
                                zh: "🎁 每周一 0 点（韩国时间）TOP 100 奖励 💰：第1名 1000 · 第2名 700 · 第3名 500 · 4–10名 300 · 11–100名 100",
                                es: "🎁 Cada lunes 00:00 (KST) el TOP 100 recibe 💰: #1 1000 · #2 700 · #3 500 · #4–10 300 · #11–100 100",
                                fr: "🎁 Chaque lundi 00h (KST), le TOP 100 reçoit 💰 : #1 1000 · #2 700 · #3 500 · #4–10 300 · #11–100 100",
                                de: "🎁 Jeden Montag 0 Uhr (KST) bekommt die TOP 100 💰: #1 1000 · #2 700 · #3 500 · #4–10 300 · #11–100 100",
                                pt: "🎁 Toda segunda 00h (KST) o TOP 100 ganha 💰: #1 1000 · #2 700 · #3 500 · #4–10 300 · #11–100 100",
                                ru: "🎁 Каждый понедельник 00:00 (KST) ТОП-100 получают 💰: #1 1000 · #2 700 · #3 500 · #4–10 300 · #11–100 100",
                                ja: "🎁 毎週月曜0時（韓国時間）TOP100に 💰：1位1000 · 2位700 · 3位500 · 4〜10位300 · 11〜100位100"))
                            .font(.system(size: 11)).foregroundColor(UiColor.text.opacity(0.7))
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text(tr("🔄 1분마다 갱신 · 이름을 누르면 프로필 카드", "🔄 Updates every minute · tap a name for their card",
                                zh: "🔄 每分钟更新 · 点名字查看名片", es: "🔄 Se actualiza cada minuto · toca un nombre", fr: "🔄 Mis à jour chaque minute · touche un nom",
                                de: "🔄 Jede Minute aktualisiert · Namen antippen", pt: "🔄 Atualiza a cada minuto · toque num nome",
                                ru: "🔄 Обновляется каждую минуту · нажмите на имя", ja: "🔄 1分ごとに更新 · 名前をタップでカード"))
                            .font(.system(size: 11)).foregroundColor(UiColor.text.opacity(0.55))
                            .frame(maxWidth: .infinity, alignment: .leading)
                        ForEach(Array(online.leaderboard.enumerated()), id: \.element.uid) { i, p in
                            let r = p.ranked(i + 1)
                            RankingRowView(p: r, me: p.uid == online.profile?.uid) { cardOf = r }
                        }
                        // TOP 100 밖이면 내 순위와 위아래 10명
                        if let r = online.myRank, !online.aroundMe.isEmpty {
                            Text(tr("📍 내 순위: \(r)위", "📍 My rank: #\(r)", zh: "📍 我的排名：第 \(r) 名", es: "📍 Mi puesto: #\(r)",
                                    fr: "📍 Mon rang : #\(r)", de: "📍 Mein Platz: #\(r)", pt: "📍 Minha posição: #\(r)", ru: "📍 Моё место: #\(r)",
                                    ja: "📍 自分の順位：\(r)位"))
                                .font(.system(size: 15, weight: .bold)).foregroundColor(UiColor.text)
                                .frame(maxWidth: .infinity, alignment: .leading).padding(.top, 10)
                            ForEach(online.aroundMe) { row in
                                RankingRowView(p: row.ranked, me: row.uid == online.profile?.uid) { cardOf = row.ranked }
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
        }
        .overlay {
            if let c = cardOf {
                PlayerCardView(p: c, me: c.uid == online.profile?.uid) { cardOf = nil }
            }
        }
        .sheet(isPresented: $showRules) { RulesSheet { showRules = false } }
        .sheet(isPresented: $showAdmin) { AdminSheet { showAdmin = false } }
        .sheet(isPresented: $showRanks) { RanksSheet(myScore: online.profile?.score ?? 0) { showRanks = false } }
        .alert(t("닉네임"), isPresented: $renaming) {
            TextField(t("최대 12자"), text: $newName)
            Button(t("취소"), role: .cancel) {}
            if canRename {
                Button(t("저장")) { online.rename(newName) }
            } else {
                Button(tr("🛒 상점 가기", "🛒 Go to shop", zh: "🛒 去商店", es: "🛒 Ir a la tienda", fr: "🛒 Aller à la boutique", de: "🛒 Zum Shop",
                          pt: "🛒 Ir à loja", ru: "🛒 В магазин", ja: "🛒 ショップへ")) { onShop() }
            }
        } message: {
            Text(renameNote)
        }
        .alert(t("정말 계정을 삭제할까요?"), isPresented: $confirmDelete) {
            Button(t("취소"), role: .cancel) {}
            Button(t("삭제"), role: .destructive) { Task { await online.deleteAccount() } }
        } message: {
            Text(deleteWarning)
        }
        .alert(tr("🏆 주간 랭킹 보상!", "🏆 Weekly ranking reward!", zh: "🏆 每周排行奖励！", es: "🏆 ¡Premio semanal!", fr: "🏆 Récompense hebdo !",
                  de: "🏆 Wochenbelohnung!", pt: "🏆 Prêmio semanal!", ru: "🏆 Недельная награда!", ja: "🏆 週間ランキング報酬！"),
               isPresented: Binding(get: { online.profile?.weeklyReward.map { !$0.seen } ?? false }, set: { if !$0 { online.ackWeeklyReward() } })) {
            Button(tr("받았어요!", "Got it!", zh: "收到！", es: "¡Genial!", fr: "Super !", de: "Danke!", pt: "Valeu!", ru: "Ура!", ja: "受け取った！")) { online.ackWeeklyReward() }
        } message: {
            let w = online.profile?.weeklyReward
            Text(tr("지난주 \(w?.rank ?? 0)위! 💰 상점 포인트 \(w?.coins ?? 0)점을 드렸어요 🎉",
                    "You were #\(w?.rank ?? 0) last week! Here are 💰 \(w?.coins ?? 0) shop points 🎉",
                    zh: "上周第 \(w?.rank ?? 0) 名！获得 💰 \(w?.coins ?? 0) 商店积分 🎉", es: "¡Quedaste #\(w?.rank ?? 0) la semana pasada! Toma 💰 \(w?.coins ?? 0) 🎉",
                    fr: "Tu étais #\(w?.rank ?? 0) ! 💰 \(w?.coins ?? 0) points 🎉", de: "Letzte Woche Platz \(w?.rank ?? 0)! 💰 \(w?.coins ?? 0) 🎉",
                    pt: "Você foi #\(w?.rank ?? 0)! 💰 \(w?.coins ?? 0) pontos 🎉", ru: "На прошлой неделе #\(w?.rank ?? 0)! 💰 \(w?.coins ?? 0) 🎉",
                    ja: "先週は\(w?.rank ?? 0)位！💰 \(w?.coins ?? 0) をプレゼント 🎉"))
        }
        .alert(t("🎉 가입을 환영해요!"), isPresented: Binding(get: { online.welcome != nil }, set: { if !$0 { online.welcome = nil } })) {
            Button(t("시작하기")) { online.welcome = nil }
        } message: {
            Text(welcomeText)
        }
    }

    private var deleteWarning: String {
        tr("점수, 계급, 승패 기록, 부활물약, 동물 친구가 모두 사라지고 되돌릴 수 없어요.\n진행 중인 대전은 기권 처리돼요.\n\n같은 계정으로 다시 로그인하면 0점부터 새로 시작해요.",
           "Your score, rank, record, potions and animal friends will be gone for good.\nAny game in progress counts as a forfeit.\n\nSigning in again with the same account starts fresh at 0 pts.",
           zh: "分数、等级、战绩、药水和动物伙伴都会永久消失。\n进行中的对战将判为弃权。\n\n用同一账号重新登录将从 0 分重新开始。",
           es: "Tu puntuación, rango, récord, pociones y amigos animales desaparecerán para siempre.\nLa partida en curso contará como abandono.\n\nSi vuelves a entrar con la misma cuenta, empezarás de nuevo con 0 pts.",
           fr: "Ton score, ton rang, ton palmarès, tes potions et tes amis animaux disparaîtront pour toujours.\nUne partie en cours compte comme un abandon.\n\nEn te reconnectant avec le même compte, tu repars à 0 pt.",
           de: "Punkte, Rang, Bilanz, Tränke und Tierfreunde sind dann für immer weg.\nEine laufende Partie zählt als Aufgabe.\n\nMeldest du dich mit demselben Konto wieder an, startest du neu mit 0 Pkt.",
           pt: "Sua pontuação, rank, histórico, poções e amigos animais vão sumir para sempre.\nUma partida em andamento conta como desistência.\n\nSe entrar de novo com a mesma conta, você recomeça com 0 pts.",
           ru: "Счёт, ранг, статистика, зелья и друзья-зверята исчезнут навсегда.\nТекущая партия засчитается как поражение.\n\nПри новом входе с тем же аккаунтом вы начнёте заново с 0 очк.",
           ja: "スコア・ランク・戦績・復活薬・どうぶつの仲間がすべて消え、元に戻せません。\n対戦中のゲームは棄権になります。\n\n同じアカウントで再ログインすると0点からやり直しです。")
    }

    private var welcomeText: String {
        guard let w = online.welcome else { return "" }
        let s = w.score
        switch w.kind {
        case "returning":
            return t("다시 오신 걸 환영해요! 탈퇴했던 계정이라 0점부터 시작해요.")
        case "admin":
            return tr("관리자 계정으로 \(s)점이 지급됐어요. 동시 접속자 수가 로비 위에 보여요.", "Admin account: you got \(s) pts. The online player count shows at the top of the lobby.")
        default:
            return tr("시작 점수 \(s)점을 드렸어요. 랜덤 매칭과 AI 대전으로 계급을 올려 보세요!", "You start with \(s) pts. Climb the ranks in random matches and AI battles!",
                      zh: "你获得了 \(s) 分起始分。通过随机匹配和 AI 对战提升等级吧！", es: "Empiezas con \(s) pts. ¡Sube de rango en partidas aleatorias y contra la IA!",
                      fr: "Tu commences avec \(s) pts. Grimpe dans les rangs en match aléatoire et contre l’IA !",
                      de: "Du startest mit \(s) Pkt. Steig in Zufallsmatches und KI-Duellen im Rang auf!",
                      pt: "Você começa com \(s) pts. Suba de rank em partidas aleatórias e contra a IA!",
                      ru: "Вы начинаете с \(s) очк. Повышайте ранг в случайных матчах и боях с ИИ!",
                      ja: "スタート時に\(s)点をプレゼント！ランダムマッチとAI対戦でランクを上げよう！")
        }
    }

    @ViewBuilder private var accountSection: some View {
        if !online.available {
            Text(tr("온라인 대전은 서버 설정(GoogleService-Info.plist)이 들어간 빌드에서 열려요. 지금은 AI 연습을 즐겨 주세요!",
                    "Online play opens in builds with the server config (GoogleService-Info.plist). Enjoy AI practice for now!"))
                .font(.system(size: 14)).foregroundColor(UiColor.text).multilineTextAlignment(.center)
        } else if let p = online.profile {
            HStack(spacing: 10) {
                Critter(side: .cat, skin: p.equipped).frame(width: 52, height: 52)
                VStack(alignment: .leading, spacing: 4) {
                    Text(p.name).font(.system(size: 18, weight: .bold)).foregroundColor(UiColor.text)
                    Button { showRanks = true } label: {
                        HStack(spacing: 0) {
                            RankBadge(score: p.score)
                            Text(t("  계급표 ›")).font(.system(size: 12, weight: .bold)).foregroundColor(UiColor.accent)
                        }
                    }
                    Text(tr("\(p.wins)승 \(p.losses)패 · 💖 \(p.potions) · 💰 \(p.coins)", "\(p.wins)W \(p.losses)L · 💖 \(p.potions) · 💰 \(p.coins)",
                            ja: "\(p.wins)勝 \(p.losses)敗 · 💖 \(p.potions) · 💰 \(p.coins)"))
                        .font(.system(size: 13)).foregroundColor(UiColor.text.opacity(0.7))
                }
                Spacer()
                VStack(alignment: .trailing) {
                    Text("\(p.score)").font(.system(size: 26, weight: .black)).foregroundColor(UiColor.primaryDark)
                    Text(t("점")).font(.system(size: 12)).foregroundColor(UiColor.text.opacity(0.7))
                }
            }
            HStack {
                Button(t("✏️ 닉네임 바꾸기")) { newName = p.name; renaming = true }
                    .font(.system(size: 14)).foregroundColor(UiColor.accent)
                Spacer()
                Button(t("로그아웃")) { online.signOut() }.font(.system(size: 14)).foregroundColor(UiColor.text.opacity(0.6))
            }
            HStack {
                Spacer()
                if online.busy { ProgressView().padding(.trailing, 6) }
                Button(t("계정 삭제")) { confirmDelete = true }
                    .font(.system(size: 12)).foregroundColor(UiColor.danger.opacity(0.7))
                    .disabled(online.busy)
            }
            if let e = online.error {
                Text(e).font(.system(size: 12)).foregroundColor(UiColor.danger).multilineTextAlignment(.center)
            }
        } else if online.loading {
            HStack(spacing: 10) {
                ProgressView()
                Text(t("서버에 접속하는 중…")).foregroundColor(UiColor.text)
            }
        } else {
            VStack(spacing: 8) {
                Text(tr("로그인하면\n점수와 전적이 계정에 저장돼요", "Sign in to save\nyour score and record",
                        zh: "登录后，\n分数和战绩会保存到账号", es: "Inicia sesión para guardar\ntu puntuación y récord",
                        fr: "Connecte-toi pour sauvegarder\nton score et ton palmarès", de: "Melde dich an, um\nPunkte und Bilanz zu speichern",
                        pt: "Entre para salvar\nsua pontuação e histórico", ru: "Войдите, чтобы сохранить\nсчёт и статистику",
                        ja: "ログインすると\nスコアと戦績が保存されます"))
                    .foregroundColor(UiColor.text).multilineTextAlignment(.center)
                Text(tr("상대에게는 계정 이름 대신 닉네임만 보여요", "Opponents only see your nickname, not your account name",
                        zh: "对手只能看到你的昵称，看不到账号名", es: "Los rivales solo ven tu apodo, no el nombre de tu cuenta",
                        fr: "Les adversaires ne voient que ton pseudo, pas le nom de ton compte", de: "Gegner sehen nur deinen Spitznamen, nicht deinen Kontonamen",
                        pt: "Os rivais só veem seu apelido, não o nome da conta", ru: "Соперники видят только ваш ник, а не имя аккаунта",
                        ja: "相手にはアカウント名ではなくニックネームだけが表示されます"))
                    .font(.system(size: 12)).foregroundColor(UiColor.text.opacity(0.6)).multilineTextAlignment(.center)
                SignInWithAppleButton(.continue) { req in
                    online.prepareAppleRequest(req)
                } onCompletion: { result in
                    Task { await online.finishAppleSignIn(result) }
                }
                .signInWithAppleButtonStyle(.black)
                // 버튼 문구를 게임 언어에 맞춤 (안 되면 기기 언어)
                .environment(\.locale, Locale(identifier: lang.code.rawValue))
                .frame(height: 52)
                .clipShape(Capsule())
                Button { Task { await online.signInWithGoogle() } } label: {
                    HStack(spacing: 10) {
                        Text("G").font(.system(size: 20, weight: .black)).foregroundColor(Color(hex: 0x4285F4))
                        Text(t("Google로 시작하기")).font(.system(size: 16, weight: .bold)).foregroundColor(UiColor.text)
                    }
                    .frame(maxWidth: .infinity, minHeight: 52)
                    .background(Capsule().fill(.white))
                    .overlay(Capsule().stroke(Color(hex: 0xDADCE0)))
                }
                if let e = online.error {
                    Text(e).font(.system(size: 12)).foregroundColor(UiColor.danger).multilineTextAlignment(.center)
                }
            }
        }
    }

}

struct TitleBanner: View {
    var body: some View {
        TimelineView(.animation) { tl in
            let hop = (sin(tl.date.timeIntervalSinceReferenceDate * 2 * .pi / 1.4) + 1) / 2
            HStack(alignment: .bottom, spacing: 6) {
                Critter(side: .cat).frame(width: 64, height: 64).offset(y: -hop * 10)
                VStack(spacing: 0) {
                    Text("Bubble").font(.system(size: 44, weight: .black)).foregroundColor(UiColor.primaryDark)
                    Text(t("냥냥 vs 햄햄 방울 대전")).font(.system(size: 14)).foregroundColor(UiColor.text)
                }
                Critter(side: .hamster).frame(width: 64, height: 64).offset(y: -(1 - hop) * 10)
            }
        }
    }
}

/// 로비 오른쪽 위 상점 버튼 (둥실둥실)
struct ShopButton: View {
    let action: () -> Void
    var body: some View {
        TimelineView(.animation) { tl in
            let bob = sin(tl.date.timeIntervalSinceReferenceDate * 2 * .pi / 1.6) * 3
            Button(action: action) {
                Text(t("🛒 상점")).font(.system(size: 16, weight: .black)).foregroundColor(UiColor.text)
                    .padding(.horizontal, 14).padding(.vertical, 8)
                    .background(Capsule().fill(UiColor.gold))
            }
            .offset(y: bob)
        }
    }
}
