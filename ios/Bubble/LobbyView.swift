import BubbleCore
import SwiftUI

/// 1단계 로비: 제목, 언어 선택, AI 연습. (로그인·랜덤 매칭·상점은 다음 단계)
struct LobbyView: View {
    @ObservedObject var lang = LangStore.shared
    var onStartAi: (AiLevel) -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                HStack {
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
                    Spacer()
                }

                TitleBanner()

                card {
                    Text(t("🤖 AI와 연습하기"))
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
                                Text("⏱ " + secs(lv.turnSec))
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(UiColor.accent)
                            }
                        }
                    }
                }

                card {
                    Text(tr("온라인 대전·랭킹·상점은 곧 열려요! 🫧", "Online battles, ranks and the shop are coming soon! 🫧",
                            zh: "在线对战、排行榜和商店即将开放！🫧", es: "¡Pronto: batallas en línea, rangos y tienda! 🫧",
                            fr: "Bientôt : combats en ligne, rangs et boutique ! 🫧", de: "Bald: Online-Duelle, Ränge und Shop! 🫧",
                            pt: "Em breve: batalhas online, ranks e loja! 🫧", ru: "Скоро: онлайн-бои, ранги и магазин! 🫧",
                            ja: "オンライン対戦・ランキング・ショップはもうすぐ！🫧"))
                        .font(.system(size: 14))
                        .foregroundColor(UiColor.text.opacity(0.75))
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
        }
    }

    private func card<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        VStack(spacing: 8) { content() }
            .padding(16)
            .frame(maxWidth: .infinity)
            .background(RoundedRectangle(cornerRadius: 24).fill(UiColor.card))
            .shadow(color: .black.opacity(0.06), radius: 3, y: 1)
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
