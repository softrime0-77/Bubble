import BubbleCore
import SwiftUI

/// 🎓 첫 접속 튜토리얼 (안드로이드 ui/Tutorial.kt와 같은 판·순서·문구).
/// 정해진 판에서 손가락 안내를 따라 5수를 둬요. 내가 두면 상대(연습 친구)도 정해진 수로 공격해요.
/// 1) 공격 → (상대도 공격) 2) 커진 방울로 펑 → (내 방울 펑) 3) ✂️ 분할 → (또 펑) 4) 💖 부활물약 → (또 펑) 5) 1마리로 상대 4를 펑 = 승리
enum TutorialScript {
    static let board = Board(cat: [1, 2], ham: [1, 4], catRevives: 1)
    /// 내가 둘 수 (손가락 안내)
    static let moves: [Move] = [
        .add(from: 1, toOpponent: true, to: 0),   // 2로 상대 1 공격 → 3
        .add(from: 0, toOpponent: true, to: 0),   // (상대 공격으로 4가 된) 4로 상대 3 공격 → 펑!
        .split(from: 1),                          // 남은 2를 1 + 1로 분할
        .revive(index: 0),                        // 💖 부활물약으로 1마리 살리기
        .add(from: 0, toOpponent: true, to: 1),   // 1로 상대 4 공격 → 펑! 승리
    ]
    /// 내 수 다음에 상대가 두는 수 (nil이면 안 둠)
    static let replies: [Move?] = [
        .add(from: 0, toOpponent: true, to: 0),   // 상대 3으로 내 1 공격 → 4
        .add(from: 1, toOpponent: true, to: 0),   // 상대 4로 내 4 공격 → 펑
        .add(from: 1, toOpponent: true, to: 0),   // 상대 4로 내 1 공격 → 펑
        .add(from: 1, toOpponent: true, to: 1),   // 상대 4로 내 1 공격 → 펑
        nil,
    ]
    static let coins = 20

    /// 이 단계에서 💖 부활 버튼을 보여 줄지 (다른 단계에선 헷갈리지 않게 숨김)
    static func allowsRevive(_ step: Int?) -> Bool {
        guard let step = step else { return true }
        guard step < moves.count, case .revive = moves[step] else { return false }
        return true
    }

    static func text(_ step: Int, selected: Bool) -> String {
        switch step {
        case 0: return selected
            ? tr("👆 이제 상대 방울을 콕! 내 방울 수만큼 더해져요 (1+2=3)", "👆 Now tap a rival bubble! Your count gets added (1+2=3)", zh: "👆 点对手的泡泡！会加上你的数量（1+2=3）", ja: "👆 相手のバブルをタップ！数が足されるよ (1+2=3)")
            : tr("👆 내 방울(2마리)을 콕 눌러 골라요", "👆 Tap your bubble with 2 to pick it", zh: "👆 点一下自己的泡泡（2只）选中它", ja: "👆 自分のバブル(2匹)をタップして選ぼう")
        case 1: return selected
            ? tr("👆 상대 3마리 방울을 콕! 3+4=7, 5 이상이면 💥 펑!", "👆 Tap their bubble with 3! 3+4=7, 5 or more = 💥 POP!", zh: "👆 点对手3只的泡泡！3+4=7，达到5就💥砰！", ja: "👆 相手の3匹のバブルをタップ！3+4=7、5以上で💥パン！")
            : tr("😮 상대도 공격했어요! 내 방울이 1+3=4로 커졌어요.\n👆 4마리 방울을 골라요", "😮 They attacked back! Your bubble grew to 1+3=4.\n👆 Pick your bubble with 4", zh: "😮 对手也攻击了！你的泡泡变成 1+3=4。\n👆 选4只的泡泡", ja: "😮 相手も攻撃してきた！自分のバブルが1+3=4に。\n👆 4匹のバブルを選んで")
        case 2: return selected
            ? tr("✂️ 분할은 남은 방울이 짝수(2·4마리)이고 다른 방울이 비었을 때만 돼요. 한 판에 \(GameLogic.maxSplits)번까지!\n👆 아래 ✂️ 분할하기 버튼을 눌러요", "✂️ You can split only when the bubble is even (2 or 4) and the other is empty. Up to \(GameLogic.maxSplits) per game!\n👆 Tap the ✂️ Split button below", zh: "✂️ 只有剩下的泡泡是偶数（2或4只）且另一个为空时才能分裂，每局最多\(GameLogic.maxSplits)次！\n👆 点下面的 ✂️ 分裂按钮", ja: "✂️ 分割できるのは残りが偶数(2·4匹)で、もう片方が空の時だけ。1試合\(GameLogic.maxSplits)回まで！\n👆 下の✂️分割ボタンをタップ")
            : tr("💥 앗, 상대가 내 방울을 터뜨렸어요!\n👆 남은 2마리 방울을 골라요", "💥 Oops, they popped your bubble!\n👆 Pick your remaining bubble with 2", zh: "💥 哎呀，你的泡泡被打爆了！\n👆 选剩下的2只泡泡", ja: "💥 あっ、自分のバブルが割られた！\n👆 残った2匹のバブルを選んで")
        case 3: return tr("😱 또 터졌어요! 💖 부활물약이면 터진 방울을 1마리로 살려요.\n물약은 상점에서 사는 소모품이라 쓰면 1개 없어져요 (지금은 연습용!)\n👆 아래 💖 버튼을 눌러요", "😱 Popped again! A 💖 revive potion brings it back with 1.\nPotions are consumables bought in the shop, used up when you drink one (this one is for practice!)\n👆 Tap the 💖 button below", zh: "😱 又被打爆了！💖 复活药水能让它以1只复活。\n药水是在商店购买的消耗品，用一次少一个（这次是练习用的！）\n👆 点下面的 💖 按钮", ja: "😱 また割られた！💖 復活薬で1匹に戻せるよ。\n復活薬はショップで買う消耗品で、使うと1個なくなるよ（今は練習用！）\n👆 下の💖ボタンをタップ")
        case 4: return selected
            ? tr("👆 상대 4마리 방울을 콕! 4+1=5 💥 펑! 다 터뜨리면 승리!", "👆 Tap their bubble with 4! 4+1=5 💥 POP! Pop them all to win!", zh: "👆 点对手4只的泡泡！4+1=5 💥砰！全部打爆就赢了！", ja: "👆 相手の4匹のバブルをタップ！4+1=5 💥パン！全部割れば勝ち！")
            : tr("😤 또 터졌지만 괜찮아요! 상대도 4마리 하나뿐.\n👆 남은 1마리 방울을 골라요", "😤 Popped again, but it's fine! They only have one bubble of 4.\n👆 Pick your bubble with 1", zh: "😤 又被打爆了，没关系！对手也只剩一个4只的泡泡。\n👆 选剩下的1只泡泡", ja: "😤 また割られたけど大丈夫！相手も4匹のバブル1つだけ。\n👆 残った1匹のバブルを選んで")
        default: return ""
        }
    }

    /// 상대 차례에 보여 줄 말
    static var opponentText: String {
        tr("🤔 이번엔 상대 차례! 상대도 나를 공격해요…", "🤔 Their turn now! They attack you too…", zh: "🤔 轮到对手了！对手也会攻击你…", ja: "🤔 今度は相手の番！相手も攻撃してくるよ…")
    }
}

/// 게임 화면 위 안내: 말풍선 + 가리키는 손가락 + 건너뛰기.
/// [centers]의 키: 방울은 "cat0" 처럼, 버튼은 "split" · "revive".
struct TutorialGuide: View {
    let step: Int
    let mySide: Side
    let myTurn: Bool
    let selected: Int?
    let centers: [String: CGPoint]
    let onSkip: () -> Void

    private var picked: Bool {
        switch TutorialScript.moves[step] {
        case let .add(from, _, _): return selected == from
        case let .split(from): return selected == from
        case .revive: return false
        }
    }

    private var targetKey: String? {
        guard myTurn else { return nil }
        switch TutorialScript.moves[step] {
        case let .add(from, toOpp, to): return picked ? "\(toOpp ? mySide.opponent : mySide)\(to)" : "\(mySide)\(from)"
        case let .split(from): return picked ? "split" : "\(mySide)\(from)"
        case .revive: return "revive"
        }
    }

    var body: some View {
        guard step < TutorialScript.moves.count else { return AnyView(EmptyView()) }
        let key = targetKey
        let isButton = key == "split" || key == "revive"
        let total = TutorialScript.moves.count
        return AnyView(ZStack(alignment: .topTrailing) {
            if let key, let c = centers[key] {
                TimelineView(.animation) { tl in
                    let bob = CGFloat(0.5 + 0.5 * sin(tl.date.timeIntervalSinceReferenceDate * 5))
                    Text("👆").font(.system(size: 52)).position(x: c.x + 4, y: c.y + (isButton ? 30 : 40) + bob * 14)
                }
                .allowsHitTesting(false)
            }
            VStack(spacing: 6) {
                Text(tr("🎓 튜토리얼 \(step + 1)/\(total)", "🎓 Tutorial \(step + 1)/\(total)", zh: "🎓 教程 \(step + 1)/\(total)", ja: "🎓 チュートリアル \(step + 1)/\(total)"))
                    .font(.system(size: 13, weight: .black)).foregroundColor(.white)
                    .padding(.horizontal, 12).padding(.vertical, 4)
                    .background(Capsule().fill(UiColor.accent))
                Text(myTurn ? TutorialScript.text(step, selected: picked) : TutorialScript.opponentText)
                    .font(.system(size: 17, weight: .black)).foregroundColor(UiColor.text).multilineTextAlignment(.center)
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(RoundedRectangle(cornerRadius: 18).fill(.white))
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 20).padding(.top, 290)
            .allowsHitTesting(false)
            Button(action: onSkip) {
                Text(tr("건너뛰기 ›", "Skip ›", zh: "跳过 ›", ja: "スキップ ›"))
                    .font(.system(size: 14, weight: .bold)).foregroundColor(UiColor.text.opacity(0.7))
                    .padding(.horizontal, 12).padding(.vertical, 6)
                    .background(Capsule().fill(.white.opacity(0.8)))
            }
            .buttonStyle(.plain)
            .padding(.top, 4).padding(.trailing, 72)
        })
    }
}

/// 끝난 뒤 안내 한 장
private struct TutorialPage {
    let emoji: String
    let title: String
    let lines: [String]
    var showCritters = false
}

private func tutorialPages() -> [TutorialPage] {
    let levels = AiLevel.allCases.map { $0.label }.joined(separator: " · ")
    let splits = GameLogic.maxSplits, revives = Shop.maxRevivesPerGame
    return [
        TutorialPage(emoji: "🎉", title: tr("승리! 기본은 끝!", "Victory! You know the basics!", zh: "胜利！基础学会了！", ja: "勝利！基本はばっちり！"), lines: [
            tr("⚔️ 공격하면 상대 방울에 더해져요. 상대도 똑같이 나를 공격해요", "⚔️ Attacking adds to their bubble. They attack you the same way",
               zh: "⚔️ 攻击会加到对手的泡泡上，对手也会这样攻击你", ja: "⚔️ 攻撃すると相手のバブルに足される。相手も同じように攻撃してくるよ"),
            tr("💥 5마리 이상이 되면 펑! 방울 2개가 다 터지면 져요", "💥 5 or more = POP! Lose both bubbles and you lose",
               zh: "💥 达到5只就砰！两个泡泡都破了就输了", ja: "💥 5匹以上でパン！バブルが2つとも割れたら負け"),
            tr("✂️ 분할: 남은 방울이 짝수(2·4마리)이고 다른 방울이 0일 때만 반으로 나눠요. 한 판에 \(splits)번까지!",
               "✂️ Split: only when your bubble is even (2 or 4) and the other is empty, halve it. Up to \(splits) per game!",
               zh: "✂️ 分裂：只有剩下的泡泡是偶数（2或4只）且另一个为0时才能对半分，每局最多\(splits)次！", ja: "✂️ 分割：残りが偶数(2·4匹)で、もう片方が0の時だけ半分に。1試合\(splits)回まで！"),
            tr("💖 부활물약: 상점에서 사는 소모품이에요. 쓸 때마다 1개씩 없어지고, 터진 방울을 1마리로 살려요. 한 판 최대 \(revives)번 (랜덤 매칭 1번)",
               "💖 Revive potion: a consumable you buy in the shop. Each use spends one and brings a popped bubble back with 1. Up to \(revives) per game (1 in random matches)",
               zh: "💖 复活药水：在商店购买的消耗品，每用一次少一个，让破掉的泡泡以1只复活。每局最多\(revives)次（随机匹配1次）", ja: "💖 復活薬：ショップで買う消耗品。使うたびに1個減り、割れたバブルを1匹で復活。1試合最大\(revives)回（ランダムは1回）"),
            tr("⏱ 온라인 대전은 한 턴 20초! 시간을 넘기면 패배예요", "⏱ Online turns are 20s. Run out of time and you lose!",
               zh: "⏱ 在线对战每回合20秒，超时判负！", ja: "⏱ オンライン対戦は1ターン20秒！時間切れで負け"),
        ]),
        TutorialPage(emoji: "🎮", title: tr("이렇게 대전해요", "Ways to play", zh: "对战方式", ja: "対戦のしかた"), lines: [
            tr("🎲 랜덤 매칭: 전 세계의 모르는 상대와 1:1! 이기면 +\(Shop.winPoints)점, 지면 -\(Shop.losePoints)점",
               "🎲 Random Match: 1:1 with a stranger anywhere in the world! Win +\(Shop.winPoints), lose -\(Shop.losePoints)",
               zh: "🎲 随机匹配：和世界各地的陌生人 1:1！胜 +\(Shop.winPoints)，负 -\(Shop.losePoints)", ja: "🎲 ランダムマッチ：世界のだれかと1対1！勝ち +\(Shop.winPoints)点、負け -\(Shop.losePoints)点"),
            tr("👫 친구와 대전: 링크를 보내면 친구가 눌러 바로 1:1! (+\(Shop.friendWinPoints) / -\(Shop.friendLosePoints)점)",
               "👫 Battle a Friend: send a link, they tap it, and you're 1:1! (+\(Shop.friendWinPoints) / -\(Shop.friendLosePoints))",
               zh: "👫 好友对战：发送链接，好友点开马上 1:1！（+\(Shop.friendWinPoints) / -\(Shop.friendLosePoints)）", ja: "👫 友だちと対戦：リンクを送ればタップですぐ1対1！（+\(Shop.friendWinPoints) / -\(Shop.friendLosePoints)点）"),
            tr("🤖 AI 대전: \(levels) 중에 골라 언제든! 로그인하면 점수도 걸려요", "🤖 AI Battle: pick \(levels) and play anytime! Sign in to earn points too",
               zh: "🤖 AI 对战：从 \(levels) 中选择，随时开打！登录后还能得分", ja: "🤖 AI対戦：\(levels) から選んでいつでも！ログインすればポイントも"),
            tr("💡 처음엔 AI 쉬움으로 연습하고, 자신 있으면 랜덤 매칭에 도전해 보세요!", "💡 Warm up against the Easy AI, then try a Random Match when you're ready!",
               zh: "💡 先和简单 AI 练习，有信心了再挑战随机匹配！", ja: "💡 まずはAIのやさしいで練習して、自信がついたらランダムマッチへ！"),
        ]),
        TutorialPage(emoji: "🏆", title: tr("랭킹과 계급", "Rankings & ranks", zh: "排行榜与等级", ja: "ランキングとランク"), lines: [
            tr("📈 이기면 점수가 올라 로비의 🏆 랭킹에 올라가요 (1분마다 갱신)", "📈 Wins raise your score and your spot on the lobby 🏆 leaderboard (updates every minute)",
               zh: "📈 获胜会提升分数，登上大厅的 🏆 排行榜（每分钟更新）", ja: "📈 勝つとスコアが上がり、ロビーの🏆ランキングに載るよ（1分ごとに更新）"),
            tr("🎁 매주 월요일 0시 TOP 100에게 💰 보상! 1위 1000 · 2위 700 · 3위 500", "🎁 Every Monday 00:00 (KST) the TOP 100 get 💰! #1 1000 · #2 700 · #3 500",
               zh: "🎁 每周一 0 点（韩国时间）TOP 100 获得 💰！第1名 1000 · 第2名 700 · 第3名 500", ja: "🎁 毎週月曜0時（韓国時間）TOP100に💰！1位1000 · 2位700 · 3位500"),
            tr("🎖 점수에 따라 계급이 올라가요. 계급이 높을수록 이길 때 보너스 점수가 더 붙어요!", "🎖 Your rank goes up with your score. Higher ranks earn bonus points on every win!",
               zh: "🎖 分数越高等级越高，等级越高获胜时的奖励分越多！", ja: "🎖 スコアでランクが上がる。ランクが高いほど勝った時のボーナスが増えるよ！"),
            Rank.allCases.map { $0.emoji }.joined(separator: " → "),
        ]),
        TutorialPage(emoji: "🛒", title: tr("상점과 캐릭터", "Shop & characters", zh: "商店与角色", ja: "ショップとキャラ"), lines: [
            tr("💖 부활물약 · 🚀 ×3 부스터 · ✏️ 닉네임 변경권을 💰 상점 포인트나 점수로 사요", "💖 Revive potions · 🚀 x3 boosters · ✏️ rename tickets, bought with 💰 shop points or score",
               zh: "💖 复活药水 · 🚀 ×3 加成 · ✏️ 改名券，可用 💰 商店积分或分数购买", ja: "💖 復活薬 · 🚀 ×3ブースター · ✏️ ニックネーム変更券を💰ポイントやスコアで買えるよ"),
            tr("🐑🐶🦒 소모품 동물: 랜덤 매칭에서 상대의 턴 시간을 줄여요 (1판에 1개)", "🐑🐶🦒 Consumable animals: shorten your opponent's turn in random matches (1 per game)",
               zh: "🐑🐶🦒 消耗品动物：随机匹配中缩短对手的回合时间（每局1个）", ja: "🐑🐶🦒 消耗品どうぶつ：ランダムマッチで相手のターン時間を短く（1試合1個）"),
            tr("✨ 스킨은 한 번 사면 계속! 💰 복냥이 · 🐼 판다 · 🐉 아기 용은 포인트 보너스, 🐰 연승 토끼는 연승 리본",
               "✨ Skins are yours forever! 💰 Lucky Cat · 🐼 Panda · 🐉 Dragon give point bonuses, 🐰 Streak Bunny wears win-streak ribbons",
               zh: "✨ 皮肤买一次永久拥有！💰 招财猫 · 🐼 熊猫 · 🐉 小龙有积分奖励，🐰 连胜兔戴连胜丝带", ja: "✨ スキンは買い切り！💰 まねきネコ · 🐼 パンダ · 🐉 ドラゴンはポイントボーナス、🐰 れんしょうウサギは連勝リボン"),
            tr("🦄 유니콘은 팔지 않아요. 다이아 계급이 되면 무료!", "🦄 The Unicorn isn't for sale. Reach Diamond rank to get it free!",
               zh: "🦄 独角兽不出售，达到钻石等级即可免费获得！", ja: "🦄 ユニコーンは非売品。ダイヤランクになれば無料！"),
            tr("👆 산 캐릭터는 로비에서 내 캐릭터를 눌러 바로 바꿔요", "👆 Tap your character in the lobby to switch to one you own",
               zh: "👆 在大厅点你的角色即可换成已拥有的角色", ja: "👆 買ったキャラはロビーで自分のキャラをタップしてすぐ変更"),
        ], showCritters: true),
    ]
}

/// 다 끝나면: 대전 방법 · 랭킹 · 상점 안내를 한 장씩 넘겨 본 뒤 보상 받기.
/// [canShop]이면 마지막 장에서 상점으로 바로 들어가 볼 수 있어요 ([onClaim]의 인자 true).
struct TutorialDone: View {
    /// 이미 보상을 받은 계정이면 보상 버튼은 비활성
    var alreadyRewarded = false
    var canShop = true
    let onClaim: (_ toShop: Bool) -> Void
    @State private var page = 0

    var body: some View {
        let pages = tutorialPages()
        let p = pages[min(page, pages.count - 1)]
        let last = page >= pages.count - 1
        let claim = alreadyRewarded ? "" : tr("보상 받고 ", "Claim & ", zh: "领取奖励并", ja: "報酬を受け取って")
        ZStack {
            Color(hex: 0x302040, alpha: 0.8).ignoresSafeArea()
            VStack(spacing: 8) {
                Text(p.emoji).font(.system(size: 44))
                Text(p.title)
                    .font(.system(size: 24, weight: .black)).foregroundColor(UiColor.primaryDark).multilineTextAlignment(.center)
                if p.showCritters {
                    HStack(spacing: 0) {
                        ForEach([Animal.luckyCat, .panda, .dragon, .rabbit, .unicorn], id: \.self) { a in
                            Critter(side: a.looksLikeCat ? .cat : .hamster, skin: a, ribbons: a == .rabbit ? 3 : 0).frame(width: 52, height: 52)
                        }
                    }
                }
                ScrollView {
                    VStack(alignment: .leading, spacing: 8) {
                        ForEach(p.lines, id: \.self) { Text($0).frame(maxWidth: .infinity, alignment: .leading) }
                    }
                    .font(.system(size: 14)).foregroundColor(UiColor.text)
                    .padding(.vertical, 6)
                }
                .frame(maxHeight: 320)
                // 몇 번째 장인지 점으로
                HStack(spacing: 6) {
                    ForEach(0..<pages.count, id: \.self) { i in
                        Circle().fill(i == page ? UiColor.primary : UiColor.text.opacity(0.2)).frame(width: 8, height: 8)
                    }
                }
                .padding(.bottom, 4)
                if !last {
                    HStack(spacing: 8) {
                        if page > 0 {
                            Button { page -= 1 } label: {
                                Text(tr("‹ 이전", "‹ Back", zh: "‹ 上一页", ja: "‹ 戻る"))
                                    .font(.system(size: 15, weight: .bold)).foregroundColor(UiColor.text)
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(Capsule().fill(Color.black.opacity(0.08)))
                            }
                            .buttonStyle(.plain)
                        }
                        Button { page += 1 } label: {
                            Text(tr("다음 ›", "Next ›", zh: "下一页 ›", ja: "次へ ›"))
                                .font(.system(size: 16, weight: .black)).foregroundColor(.white)
                                .frame(maxWidth: .infinity).padding(.vertical, 12)
                                .background(Capsule().fill(LinearGradient(colors: [UiColor.primary, UiColor.accent], startPoint: .leading, endPoint: .trailing)))
                        }
                        .buttonStyle(.plain)
                        .layoutPriority(1)
                    }
                } else {
                    if alreadyRewarded {
                        Text(tr("✅ 보상은 이미 받았어요", "✅ Reward already claimed", zh: "✅ 奖励已领取", ja: "✅ 報酬は受け取り済み"))
                            .font(.system(size: 15, weight: .bold)).foregroundColor(UiColor.text.opacity(0.45))
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(Capsule().fill(Color.black.opacity(0.08)))
                    } else {
                        Text(tr("🎁 보상: 💰 +\(TutorialScript.coins) · 💖 +1", "🎁 Reward: 💰 +\(TutorialScript.coins) · 💖 +1",
                                zh: "🎁 奖励：💰 +\(TutorialScript.coins) · 💖 +1", ja: "🎁 報酬：💰 +\(TutorialScript.coins) · 💖 +1"))
                            .font(.system(size: 16, weight: .black)).foregroundColor(Color(hex: 0xC88A00))
                    }
                    if canShop {
                        Button { onClaim(true) } label: {
                            Text("🛒 " + claim + tr("상점 들어가 보기", "visit the shop", zh: "逛逛商店", ja: "ショップをのぞく"))
                                .font(.system(size: 16, weight: .black)).foregroundColor(.white)
                                .frame(maxWidth: .infinity).padding(.vertical, 13)
                                .background(Capsule().fill(LinearGradient(colors: [Color(hex: 0xFFB300), UiColor.primary, UiColor.accent], startPoint: .leading, endPoint: .trailing)))
                        }
                        .buttonStyle(.plain)
                    }
                    Button { onClaim(false) } label: {
                        Text(claim + tr("로비로", "back to lobby", zh: "返回大厅", ja: "ロビーへ"))
                            .font(.system(size: 15, weight: .black)).foregroundColor(canShop ? UiColor.text : .white)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(Capsule().fill(canShop
                                ? AnyShapeStyle(Color.black.opacity(0.08))
                                : AnyShapeStyle(LinearGradient(colors: [UiColor.primary, UiColor.accent], startPoint: .leading, endPoint: .trailing))))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(20)
            .background(RoundedRectangle(cornerRadius: 28).fill(LinearGradient(colors: [.white, Color(hex: 0xF6EEFF)], startPoint: .top, endPoint: .bottom)))
            .padding(20)
        }
    }
}

/// 🛒 튜토리얼에서 상점으로 들어오면 맨 위에 보여 주는 캐릭터 안내
struct ShopTourCard: View {
    let onDismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(tr("🎓 상점 둘러보기", "🎓 Shop tour", zh: "🎓 商店导览", ja: "🎓 ショップ案内"))
                .font(.system(size: 18, weight: .black)).foregroundColor(UiColor.primaryDark)
            Group {
                Text(tr("1️⃣ 위쪽: 💖 부활물약 · ✏️ 닉네임 변경권 · 🚀 ×3 부스터", "1️⃣ Up top: 💖 revive potions · ✏️ rename tickets · 🚀 x3 boosters",
                        zh: "1️⃣ 上方：💖 复活药水 · ✏️ 改名券 · 🚀 ×3 加成", ja: "1️⃣ 上：💖 復活薬 · ✏️ ニックネーム変更券 · 🚀 ×3ブースター"))
                Text(tr("2️⃣ 아래로 내리면 캐릭터들! 🐹 햄스터는 무료, ✨ 스킨은 한 번 사면 계속", "2️⃣ Scroll down for characters! 🐹 Hamster is free, ✨ skins are yours forever",
                        zh: "2️⃣ 往下滑就是角色！🐹 仓鼠免费，✨ 皮肤买一次永久拥有", ja: "2️⃣ 下にスクロールするとキャラ！🐹 ハムスターは無料、✨ スキンは買い切り"))
                Text(tr("3️⃣ 카드마다 능력(💰 보너스·리본 등)과 맞은 방울에 남는 흔적이 적혀 있어요", "3️⃣ Each card lists its power (💰 bonuses, ribbons…) and the mark it leaves on bubbles",
                        zh: "3️⃣ 每张卡片都写着能力（💰 奖励、丝带等）和留在泡泡上的痕迹", ja: "3️⃣ カードごとに能力（💰ボーナス・リボンなど）と残る跡が書いてあるよ"))
                Text(tr("4️⃣ 🐑🐶🦒 소모품 동물은 랜덤 매칭에서 상대 턴을 줄여요", "4️⃣ 🐑🐶🦒 consumable animals shorten your opponent's turn in random matches",
                        zh: "4️⃣ 🐑🐶🦒 消耗品动物在随机匹配中缩短对手回合", ja: "4️⃣ 🐑🐶🦒 消耗品どうぶつはランダムマッチで相手のターンを短く"))
                Text(tr("5️⃣ 산 캐릭터는 로비에서 내 캐릭터를 눌러 바꿀 수 있어요", "5️⃣ Tap your character in the lobby to switch to one you own",
                        zh: "5️⃣ 在大厅点你的角色即可更换已拥有的角色", ja: "5️⃣ 買ったキャラはロビーで自分のキャラをタップして変更"))
            }
            .font(.system(size: 13)).foregroundColor(UiColor.text)
            .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: onDismiss) {
                Text(tr("알겠어요!", "Got it!", zh: "知道了！", ja: "わかった！"))
                    .font(.system(size: 15, weight: .black)).foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 10)
                    .background(Capsule().fill(LinearGradient(colors: [UiColor.primary, UiColor.accent], startPoint: .leading, endPoint: .trailing)))
            }
            .buttonStyle(.plain)
            .padding(.top, 4)
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 20).fill(LinearGradient(colors: [Color(hex: 0xFFF4D6), Color(hex: 0xF6EEFF)], startPoint: .top, endPoint: .bottom)))
    }
}
