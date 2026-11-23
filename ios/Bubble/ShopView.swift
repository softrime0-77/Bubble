import BubbleCore
import SwiftUI

/// 상점: 💰 포인트 충전(App Store), 💖 부활물약, ×3 부스터, 동물 친구들 (안드로이드 ShopScreen과 같은 구성)
struct ShopView: View {
    @ObservedObject var online = OnlineService.shared
    @ObservedObject var store = PointStore.shared
    @ObservedObject var ads = Ads.shared
    let onClose: () -> Void

    @State private var confirm: (item: String, label: String, price: Int, qty: Int)? = nil

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
                             desc: tf("내 차례에 터진(0마리) 방울 하나를 1마리로 살려요.\n한 판에 최대 {0}번 (랜덤 매칭 1번) · 쓸 때마다 1개 소모 · 온라인 대전 전용", Shop.maxRevivesPerGame),
                             price: Shop.potionPrice, owned: tf("보유 {0}개", p.potions), art: AnyView(PotionArt().frame(width: 84, height: 84))) {
                        VStack(alignment: .leading, spacing: 6) {
                            // 묶음 할인: 1 · 3 · 5 · 10개
                            bundleButtons("POTION", t("부활물약"), p)
                            potionAdButton(p)
                        }
                    }
                    // ✏️ 닉네임 변경권
                    shopCard(title: tr("✏️ 닉네임 변경권", "✏️ Rename Ticket", zh: "✏️ 改名券", es: "✏️ Cupón de cambio de apodo", fr: "✏️ Ticket de pseudo",
                                       de: "✏️ Namensticket", pt: "✏️ Cupom de troca de apelido", ru: "✏️ Талон смены ника", ja: "✏️ ニックネーム変更券"),
                             desc: tr("닉네임을 한 번 바꿀 수 있어요. 첫 변경은 무료!", "Change your nickname once. Your first change is free!",
                                      zh: "可以修改一次昵称。第一次免费！", es: "Cambia tu apodo una vez. ¡El primer cambio es gratis!", fr: "Change ton pseudo une fois. Le premier est gratuit !",
                                      de: "Ändere deinen Namen einmal. Die erste Änderung ist gratis!", pt: "Troque seu apelido uma vez. A primeira é grátis!",
                                      ru: "Смените ник один раз. Первая смена бесплатно!", ja: "ニックネームを1回変更できます。最初の変更は無料！")
                                + (p.freeRenameUsed ? "" : "\n" + tr("🎁 아직 무료 변경이 남아 있어요", "🎁 You still have a free change", zh: "🎁 你还有一次免费修改",
                                                                     es: "🎁 Aún tienes un cambio gratis", fr: "🎁 Il te reste un changement gratuit", de: "🎁 Du hast noch eine Gratis-Änderung",
                                                                     pt: "🎁 Você ainda tem uma troca grátis", ru: "🎁 У вас ещё есть бесплатная смена", ja: "🎁 無料変更がまだ残っています")),
                             price: Shop.renamePrice,
                             owned: p.renameTickets > 0 ? tr("보유 \(p.renameTickets)장", "Owned: \(p.renameTickets)", zh: "拥有 \(p.renameTickets) 张", es: "Tienes: \(p.renameTickets)",
                                                            fr: "Possédés : \(p.renameTickets)", de: "Im Besitz: \(p.renameTickets)", pt: "Você tem: \(p.renameTickets)", ru: "Есть: \(p.renameTickets)", ja: "所持 \(p.renameTickets)枚") : nil,
                             art: AnyView(Text("✏️").font(.system(size: 54)))) {
                        buyButton("RENAME", tr("닉네임 변경권", "Rename Ticket", zh: "改名券", es: "Cupón de cambio", fr: "Ticket de pseudo", de: "Namensticket",
                                              pt: "Cupom de troca", ru: "Талон смены ника", ja: "変更券"), Shop.renamePrice, p)
                    }
                    shopCard(title: t("×3 점수 부스터"),
                             desc: t("켜두면 랜덤 매칭에서 이길 때 얻는 점수가 3배! 이긴 판에만 1개를 쓰고, 지면 그대로 남아요 (AI 대전에는 쓰지 않아요)"),
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
                                 desc: tf("⏱ 상대 턴 시간 {0}초 → {1}초", Shop.defaultTurnSec, a.opponentTurnSec) + (a == .sheep ? tr(" · 이기면 구름 둥실", " · win: floating clouds") : a == .dog ? tr(" · 이기면 발자국 콩콩", " · win: paw prints") : tr(" · 이기면 잎사귀 회오리", " · win: leaf whirl")),
                                 price: a.price,
                                 owned: count > 0 ? tf("보유 {0}개", count) + (on ? t("  ·  ✅ 장착 중") : "") : nil,
                                 art: AnyView(Critter(side: .cat, skin: a, happy: on).frame(width: 84, height: 84))) {
                            // 묶음 할인: 1 · 3개
                            bundleButtons(a.rawValue, a.label, p)
                            HStack(spacing: 6) {
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
                if let c = confirm { Task { await online.buy(c.item, label: c.label, qty: c.qty) } }
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
                        ja: "🎁 初回購入2倍！最初のチャージは2倍になります") + "\n" + firstBuyLeftText)
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

    /// 첫 구매 2배는 가입 후 7일 동안만
    private var firstBuy: Bool { OnlineService.firstBuyLeft(online.profile) != nil }

    /// "⏰ 가입 후 7일 한정 · N일 M시간 남음"
    private var firstBuyLeftText: String {
        let sec = Int(OnlineService.firstBuyLeft(online.profile) ?? 0)
        let h = sec / 3600, d = h / 24, hh = h % 24, m = (sec / 60) % 60
        if d > 0 {
            return tr("⏰ 가입 후 7일 한정 · \(d)일 \(hh)시간 남음", "⏰ First 7 days only · \(d)d \(hh)h left", zh: "⏰ 注册后 7 天内有效 · 剩 \(d) 天 \(hh) 小时",
                      es: "⏰ Solo los 7 primeros días · quedan \(d) d \(hh) h", fr: "⏰ 7 premiers jours seulement · encore \(d) j \(hh) h",
                      de: "⏰ Nur in den ersten 7 Tagen · noch \(d) T \(hh) Std", pt: "⏰ Só nos 7 primeiros dias · faltam \(d) d \(hh) h",
                      ru: "⏰ Только первые 7 дней · осталось \(d) д \(hh) ч", ja: "⏰ 登録から7日間限定 · 残り\(d)日\(hh)時間")
        }
        return tr("⏰ 가입 후 7일 한정 · \(hh)시간 \(m)분 남음!", "⏰ First 7 days only · \(hh)h \(m)m left!", zh: "⏰ 注册后 7 天内有效 · 剩 \(hh) 小时 \(m) 分！",
                  es: "⏰ Solo los 7 primeros días · ¡quedan \(hh) h \(m) min!", fr: "⏰ 7 premiers jours seulement · plus que \(hh) h \(m) min !",
                  de: "⏰ Nur in den ersten 7 Tagen · noch \(hh) Std \(m) Min!", pt: "⏰ Só nos 7 primeiros dias · faltam \(hh) h \(m) min!",
                  ru: "⏰ Только первые 7 дней · осталось \(hh) ч \(m) мин!", ja: "⏰ 登録から7日間限定 · 残り\(hh)時間\(m)分！")
    }

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
        Text(tr("승패에는 영향이 없어요. 💰 복냥이 · 🐼 판다 · 🐉 아기 용은 상점 포인트 보너스가 있어요",
                "No effect on winning. 💰 Lucky Cat, 🐼 Panda and 🐉 Dragon give shop-point bonuses",
                zh: "不影响胜负。💰 招财猫 · 🐼 熊猫 · 🐉 小龙有商店积分奖励", es: "No afectan al resultado. 💰 Gato, 🐼 Panda y 🐉 Dragón dan puntos de tienda",
                fr: "Aucun effet sur la victoire. 💰 Chat, 🐼 Panda et 🐉 Dragon donnent des points boutique", de: "Kein Einfluss auf Sieg. 💰 Katze, 🐼 Panda und 🐉 Drache geben Shop-Punkte",
                pt: "Não afetam a vitória. 💰 Gato, 🐼 Panda e 🐉 Dragão dão pontos da loja", ru: "На победу не влияют. 💰 Котик, 🐼 Панда и 🐉 Дракон дают очки магазина",
                ja: "勝敗には影響なし。💰 まねきネコ · 🐼 パンダ · 🐉 ドラゴンはポイントボーナスあり"))
            .font(.system(size: 12)).foregroundColor(UiColor.text.opacity(0.7))
            .frame(maxWidth: .infinity, alignment: .leading)
        ForEach(Animal.allCases.filter { $0.cosmetic }, id: \.self) { a in
            skinCard(a, p)
        }
        ForEach(Animal.allCases.filter { $0.rankOnly }, id: \.self) { a in
            rankCard(a, p)
        }
    }

    private func skinCard(_ a: Animal, _ p: Profile) -> some View {
        let have = p.skins.contains(a)
        let on = p.equipped == a
        let owned: String? = on ? tr("✅ 장착 중", "✅ Equipped", zh: "✅ 已装备", es: "✅ Equipado", fr: "✅ Équipé", de: "✅ Ausgerüstet", pt: "✅ Equipado", ru: "✅ Надето", ja: "✅ 装備中")
            : have ? tr("💎 보유 중", "💎 Owned", zh: "💎 已拥有", es: "💎 Tuyo", fr: "💎 Possédé", de: "💎 Im Besitz", pt: "💎 Seu", ru: "💎 Есть", ja: "💎 所持中") : nil
        return shopCard(title: a.label,
                        desc: skinCardDesc(a, p),
                        price: have ? nil : a.price, owned: owned,
                        art: AnyView(Critter(side: a.looksLikeCat ? .cat : .hamster, skin: a, happy: on,
                                             ribbons: a == .rabbit ? (have ? min(p.winStreak, Shop.rabbitMaxRibbons) : 3) : 0).frame(width: 84, height: 84))) {
            VStack(alignment: .leading, spacing: 4) {
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
                if have { LobbyMusicButton(animal: a) }
            }
        }
    }

    /// 🐙 🐧 🐼 🐉 🐰 효과 설명 (조건은 숫자 그대로)
    private func specialDesc(_ a: Animal, _ p: Profile) -> String {
        switch a {
        case .pinkCat:
            return tr("✨ 영구 스킨 · 💗 하트 타이머, 이기면 하트 비 (승패 영향 없음)", "✨ Permanent skin · 💗 Heart timer, heart rain when you win (no effect on winning)",
                      zh: "✨ 永久皮肤 · 💗 获胜时下起爱心雨（不影响胜负）", es: "✨ Aspecto permanente · 💗 Lluvia de corazones al ganar", fr: "✨ Skin permanent · 💗 Pluie de cœurs à la victoire",
                      de: "✨ Dauerhafter Skin · 💗 Herzregen beim Sieg", pt: "✨ Skin permanente · 💗 Chuva de corações ao vencer", ru: "✨ Навсегда · 💗 Дождь из сердечек при победе", ja: "✨ 永久スキン · 💗 勝つとハートの雨")
        case .blackCat:
            return tr("✨ 영구 스킨 · 🌙 밤하늘 타이머, 이기면 달과 별이 쏟아져요 (승패 영향 없음)", "✨ Permanent skin · 🌙 Night-sky timer, moons and stars rain when you win (no effect on winning)",
                      zh: "✨ 永久皮肤 · 🌙 获胜时月亮星星落下（不影响胜负）", es: "✨ Aspecto permanente · 🌙 Lluvia de lunas y estrellas al ganar", fr: "✨ Skin permanent · 🌙 Pluie de lunes et d’étoiles à la victoire",
                      de: "✨ Dauerhafter Skin · 🌙 Monde und Sterne beim Sieg", pt: "✨ Skin permanente · 🌙 Chuva de luas e estrelas ao vencer", ru: "✨ Навсегда · 🌙 Луны и звёзды при победе", ja: "✨ 永久スキン · 🌙 勝つと月と星が降る")
        case .snowHamster:
            return tr("✨ 영구 스킨 · ❄️ 내 타이머가 얼음 게이지로, 이기면 눈송이 비 (승패 영향 없음)", "✨ Permanent skin · ❄️ Icy timer, snow rain when you win (no effect on winning)",
                      zh: "✨ 永久皮肤 · ❄️ 冰雪计时条，获胜时下雪（不影响胜负）", es: "✨ Aspecto permanente · ❄️ Temporizador de hielo y nieve al ganar", fr: "✨ Skin permanent · ❄️ Minuteur de glace, neige à la victoire",
                      de: "✨ Dauerhafter Skin · ❄️ Eis-Timer, Schnee beim Sieg", pt: "✨ Skin permanente · ❄️ Timer de gelo e neve ao vencer", ru: "✨ Навсегда · ❄️ Ледяной таймер, снег при победе", ja: "✨ 永久スキン · ❄️ 氷のタイマー、勝つと雪")
        case .goldHamster:
            return tr("✨ 영구 스킨 · 🏆 황금 타이머, 이기면 금가루 폭죽이 팡팡 (승패 영향 없음)", "✨ Permanent skin · 🏆 Golden timer, gold fireworks when you win (no effect on winning)",
                      zh: "✨ 永久皮肤 · 🏆 黄金计时条，获胜时金粉烟花（不影响胜负）", es: "✨ Aspecto permanente · 🏆 Temporizador dorado y fuegos de oro al ganar", fr: "✨ Skin permanent · 🏆 Minuteur doré, feux d’or à la victoire",
                      de: "✨ Dauerhafter Skin · 🏆 Gold-Timer, Goldfeuerwerk beim Sieg", pt: "✨ Skin permanente · 🏆 Timer dourado e fogos de ouro ao vencer", ru: "✨ Навсегда · 🏆 Золотой таймер, золотой салют при победе", ja: "✨ 永久スキン · 🏆 黄金タイマー、勝つと金粉の花火")
        case .octopus:
            return tr("🐙 분할하면 먹물이 퍽! · 이기면 화면에 먹물이 퍽퍽 · 승패 영향 없음", "🐙 Ink splash when you split · ink splats when you win · no effect on winning", zh: "🐙 分裂时喷墨！· 仅外观",
                      es: "🐙 ¡Tinta al dividir! · solo aspecto", fr: "🐙 Jet d’encre en divisant · look seulement", de: "🐙 Tinte beim Teilen · nur Look",
                      pt: "🐙 Tinta ao dividir · só visual", ru: "🐙 Чернила при делении · только вид", ja: "🐙 分割でスミがぷしゅっ · 見た目だけ")
        case .penguin:
            return tr("🐧 공격하면 빨간 썰매를 타고 쌩! 눈가루·얼음 조각이 튀고 부딪히면 얼음이 와장창\n이기면 썰매 퍼레이드와 눈보라 · 승패 영향 없음",
                      "🐧 Attacks ride a red sled — snow spray, ice shards and an icy crash\nWin: sled parade in a snowstorm · no effect on winning",
                      zh: "🐧 攻击时坐红色雪橇冲过去！雪粉冰片飞溅，撞上时冰块碎裂\n获胜：雪橇游行与暴风雪 · 不影响胜负",
                      es: "🐧 Ataca en trineo rojo: nieve, esquirlas de hielo y choque helado\nVictoria: desfile en trineo con nevada · sin efecto",
                      fr: "🐧 Attaque en luge rouge : neige, éclats de glace et choc glacé\nVictoire : parade en luge sous la neige · aucun effet",
                      de: "🐧 Angriff auf rotem Schlitten: Schnee, Eissplitter, eisiger Aufprall\nSieg: Schlittenparade im Schneesturm · kein Effekt",
                      pt: "🐧 Ataca de trenó vermelho: neve, lascas de gelo e choque gelado\nVitória: desfile de trenó na nevasca · sem efeito",
                      ru: "🐧 Атака на красных санках: снег, осколки льда, ледяной удар\nПобеда: парад на санках в метель · без эффекта",
                      ja: "🐧 攻撃は赤いソリでシュー！雪と氷のかけら、当たると氷がパリーン\n勝利：ソリのパレードと吹雪 · 勝敗に影響なし")
        case .panda:
            let b = Shop.pandaCheckinBonus
            return tr("🐼 장착하고 출석하면 💰 +\(b) 더 (하루 1번) · 이기면 대나무잎 회오리 · 영구", "🐼 Check in while equipped for 💰 +\(b) more (once a day) · permanent",
                      zh: "🐼 装备后签到多得 💰 +\(b)（每天 1 次）· 永久", es: "🐼 Equipado: 💰 +\(b) extra al registrarte (1 al día) · permanente",
                      fr: "🐼 Équipé : 💰 +\(b) en plus à la présence (1/jour) · permanent", de: "🐼 Ausgerüstet: 💰 +\(b) beim Check-in (1×/Tag) · dauerhaft",
                      pt: "🐼 Equipado: 💰 +\(b) na presença (1 por dia) · permanente", ru: "🐼 Надета: +\(b) 💰 за вход (1 раз в день) · навсегда",
                      ja: "🐼 装備して出席すると 💰 +\(b)（1日1回）· 永久")
        case .dragon:
            let today = p.dragonDay == OnlineService.today() ? p.dragonCoins : 0
            let c = Shop.dragonPopCoins, mx = Shop.dragonDailyMax
            return tr("🐉 상대 방울을 터뜨릴 때마다 💰 +\(c)\n(하루 최대 \(mx) · 오늘 \(today)/\(mx)) · 이기면 불꽃이 활활 · 영구",
                      "🐉 💰 +\(c) every time you pop a rival bubble\n(max \(mx) a day · today \(today)/\(mx)) · permanent",
                      zh: "🐉 每次打爆对手泡泡 💰 +\(c)\n（每天最多 \(mx) · 今天 \(today)/\(mx)）· 永久",
                      es: "🐉 💰 +\(c) cada vez que revientas una burbuja rival\n(máx. \(mx) al día · hoy \(today)/\(mx)) · permanente",
                      fr: "🐉 💰 +\(c) à chaque bulle adverse éclatée\n(max \(mx) par jour · aujourd’hui \(today)/\(mx)) · permanent",
                      de: "🐉 💰 +\(c) für jede geplatzte Gegnerblase\n(max. \(mx) pro Tag · heute \(today)/\(mx)) · dauerhaft",
                      pt: "🐉 💰 +\(c) a cada bolha rival estourada\n(máx. \(mx) por dia · hoje \(today)/\(mx)) · permanente",
                      ru: "🐉 💰 +\(c) за каждый лопнутый пузырь соперника\n(макс. \(mx) в день · сегодня \(today)/\(mx)) · навсегда",
                      ja: "🐉 相手の泡を割るたびに 💰 +\(c)\n（1日最大\(mx) · 今日 \(today)/\(mx)）· 永久")
        case .rabbit:
            let today = p.rabbitDay == OnlineService.today() ? p.rabbitCoins : 0
            let mx = Shop.rabbitDailyMax, n = p.winStreak
            let a3 = Shop.rabbit3Coins, a5 = Shop.rabbit5Coins, more = Shop.rabbitMoreCoins
            return tr("🐰 연승할수록 💰 보상!\n· 3연승 +\(a3) · 5연승 +\(a5) · 6연승부터 이길 때마다 +\(more)\n· 지면 연승이 처음부터 (다시 3연승하면 또 +\(a3))\n· 하루 최대 \(mx) (오늘 \(today)/\(mx))\n· 연승마다 리본이 하나씩, 5연승이면 금빛 리본\n· 랜덤 매칭·점수 걸린 친구 대전만 · 지금 \(n)연승",
                      "🐰 Win streaks pay 💰!\n· 3 wins +\(a3) · 5 wins +\(a5) · from 6, +\(more) per win\n· A loss resets the streak (3 again = +\(a3) again)\n· Max \(mx) a day (today \(today)/\(mx))\n· A ribbon per win, golden ribbons at 5\n· Random & scored friend matches · streak \(n)",
                      zh: "🐰 连胜越多 💰 越多！\n· 3连胜 +\(a3) · 5连胜 +\(a5) · 6连胜起每胜 +\(more)\n· 输了连胜重来（再3连胜又 +\(a3)）\n· 每天最多 \(mx)（今天 \(today)/\(mx)）\n· 每胜一个蝴蝶结，5连胜变金色\n· 随机匹配和计分好友对战 · 当前 \(n) 连胜",
                      es: "🐰 ¡Las rachas dan 💰!\n· 3 seguidas +\(a3) · 5 seguidas +\(a5) · desde 6, +\(more) por victoria\n· Perder reinicia la racha\n· Máx. \(mx) al día (hoy \(today)/\(mx))\n· Un lazo por victoria, dorados a las 5 · racha \(n)",
                      fr: "🐰 Les séries rapportent 💰 !\n· 3 victoires +\(a3) · 5 victoires +\(a5) · dès 6, +\(more) par victoire\n· Une défaite remet la série à zéro\n· Max \(mx) par jour (aujourd’hui \(today)/\(mx))\n· Un ruban par victoire, dorés à 5 · série \(n)",
                      de: "🐰 Siegesserien bringen 💰!\n· 3 Siege +\(a3) · 5 Siege +\(a5) · ab 6 +\(more) pro Sieg\n· Eine Niederlage setzt die Serie zurück\n· Max. \(mx) pro Tag (heute \(today)/\(mx))\n· Eine Schleife pro Sieg, golden ab 5 · Serie \(n)",
                      pt: "🐰 Sequências dão 💰!\n· 3 vitórias +\(a3) · 5 vitórias +\(a5) · a partir de 6, +\(more) por vitória\n· Perder zera a sequência\n· Máx. \(mx) por dia (hoje \(today)/\(mx))\n· Um laço por vitória, dourados aos 5 · sequência \(n)",
                      ru: "🐰 Серии побед дают 💰!\n· 3 победы +\(a3) · 5 побед +\(a5) · с 6-й +\(more) за победу\n· Поражение обнуляет серию\n· Макс. \(mx) в день (сегодня \(today)/\(mx))\n· Бантик за победу, золотые на 5 · серия \(n)",
                      ja: "🐰 連勝するほど 💰！\n· 3連勝 +\(a3) · 5連勝 +\(a5) · 6連勝からは勝つたびに +\(more)\n· 負けると連勝はリセット（また3連勝で +\(a3)）\n· 1日最大\(mx)（今日 \(today)/\(mx)）\n· 連勝ごとにリボン、5連勝で金色 · 今\(n)連勝")
        default:
            return tr("✨ 영구 스킨 · 효과 없음", "✨ Permanent skin · no effect")
        }
    }

    /// 스킨 카드 설명: 효과 설명 + 맞은 방울 흔적
    private func skinCardDesc(_ a: Animal, _ p: Profile) -> String {
        let base: String = [.octopus, .penguin, .panda, .dragon, .rabbit, .pinkCat, .blackCat, .snowHamster, .goldHamster].contains(a) ? specialDesc(a, p) : a.lucky ? luckyDesc(p) : tr("✨ 영구 스킨 · 효과 없음", "✨ Permanent skin · no effect", zh: "✨ 永久皮肤 · 无效果", es: "✨ Aspecto permanente · sin efecto",
                                 fr: "✨ Skin permanent · aucun effet", de: "✨ Dauerhafter Skin · kein Effekt", pt: "✨ Skin permanente · sem efeito",
                                 ru: "✨ Навсегда · без эффекта", ja: "✨ 永久スキン · 効果なし")
        return base + "\n" + markDesc(a)
    }

    /// 🦄 계급 캐릭터: 팔지 않고 다이아 계급이면 무료로 장착
    private func rankCard(_ a: Animal, _ p: Profile) -> some View {
        let unlocked = p.score >= a.rankMin
        let on = p.equipped == a
        let need = a.rankMin - p.score
        let owned: String = on ? tr("✅ 장착 중", "✅ Equipped", zh: "✅ 已装备", es: "✅ Equipado", fr: "✅ Équipé", de: "✅ Ausgerüstet", pt: "✅ Equipado", ru: "✅ Надето", ja: "✅ 装備中")
            : unlocked ? tr("💎 해금됨", "💎 Unlocked", zh: "💎 已解锁", es: "💎 Desbloqueado", fr: "💎 Débloqué", de: "💎 Freigeschaltet", pt: "💎 Desbloqueado", ru: "💎 Открыто", ja: "💎 解放済み")
            : tr("🔒 앞으로 \(need)점", "🔒 \(need) pts to go", zh: "🔒 还差 \(need) 分", es: "🔒 Faltan \(need) pts", fr: "🔒 Encore \(need) pts",
                 de: "🔒 Noch \(need) Pkt", pt: "🔒 Faltam \(need) pts", ru: "🔒 Ещё \(need) очк.", ja: "🔒 あと\(need)点")
        let today = p.unicornDay == OnlineService.today() ? p.unicornCoins : 0
        let c = Shop.unicornWinCoins, mx = Shop.unicornDailyMax, mn = a.rankMin
        let desc = tr("🌈 무지개 축복!\n· 이길 때마다 💰 +\(c) (하루 최대 \(mx) · 오늘 \(today)/\(mx))\n· 매칭되면 상대에게 \"다이아 전용 유니콘 등장!\"\n· 랭킹에서 닉네임이 무지개색 · 이기면 무지개와 별 폭죽\n· 판매하지 않아요: 💎 다이아 계급(\(mn)점)부터 무료로 장착\n· 랜덤 매칭·점수 걸린 친구 대전만",
                      "🌈 Rainbow blessing!\n· 💰 +\(c) per win (max \(mx) a day · today \(today)/\(mx))\n· Rivals see \"A Diamond-only Unicorn appears!\"\n· Rainbow name on the leaderboard · rainbow & star fireworks when you win\n· Not for sale: free from 💎 Diamond (\(mn) pts)\n· Random & scored friend matches",
                      zh: "🌈 彩虹祝福！\n· 每胜 💰 +\(c)（每天最多 \(mx) · 今天 \(today)/\(mx)）\n· 匹配时对手会看到“钻石专属独角兽登场！”\n· 排行榜上名字变彩虹色\n· 非卖品：💎 钻石（\(mn) 分）起免费装备",
                      es: "🌈 ¡Bendición arcoíris!\n· 💰 +\(c) por victoria (máx. \(mx) al día · hoy \(today)/\(mx))\n· Tu rival verá el aviso del unicornio\n· Nombre arcoíris en la clasificación\n· No se vende: gratis desde 💎 Diamante (\(mn) pts)",
                      fr: "🌈 Bénédiction arc-en-ciel !\n· 💰 +\(c) par victoire (max \(mx) par jour · aujourd’hui \(today)/\(mx))\n· L’adversaire voit l’arrivée de la licorne\n· Pseudo arc-en-ciel au classement\n· Pas à vendre : gratuit dès 💎 Diamant (\(mn) pts)",
                      de: "🌈 Regenbogensegen!\n· 💰 +\(c) pro Sieg (max. \(mx) pro Tag · heute \(today)/\(mx))\n· Gegner sehen: Diamant-Einhorn erscheint\n· Regenbogenname in der Bestenliste\n· Nicht käuflich: gratis ab 💎 Diamant (\(mn) Pkt)",
                      pt: "🌈 Bênção arco-íris!\n· 💰 +\(c) por vitória (máx. \(mx) por dia · hoje \(today)/\(mx))\n· O rival vê o aviso do unicórnio\n· Nome arco-íris no ranking\n· Não vende: grátis a partir de 💎 Diamante (\(mn) pts)",
                      ru: "🌈 Радужное благословение!\n· 💰 +\(c) за победу (макс. \(mx) в день · сегодня \(today)/\(mx))\n· Соперник увидит появление единорога\n· Радужное имя в рейтинге\n· Не продаётся: бесплатно с 💎 Алмаза (\(mn) очк.)",
                      ja: "🌈 にじの祝福！\n· 勝つたびに 💰 +\(c)（1日最大\(mx) · 今日 \(today)/\(mx)）\n· マッチすると相手に「ダイヤ専用ユニコーン登場！」\n· ランキングで名前が虹色\n· 非売品：💎 ダイヤ（\(mn)点）から無料で装備")
        return shopCard(title: a.label, desc: desc + "\n" + markDesc(a), price: nil, owned: owned,
                        art: AnyView(Critter(side: .hamster, skin: a, happy: on).frame(width: 84, height: 84).opacity(unlocked ? 1 : 0.55))) {
            if on {
                outlineButton(t("장착 해제")) { Task { await online.equip(nil) } }
            } else {
                Button { Task { await online.equip(a) } } label: {
                    Text(unlocked ? t("장착하기") : "🔒").font(.system(size: 14, weight: .bold)).foregroundColor(.white)
                        .padding(.horizontal, 12).padding(.vertical, 8)
                        .background(Capsule().fill(UiColor.accent.opacity(unlocked ? 1 : 0.4)))
                }
                .disabled(online.busy || !unlocked)
            }
        }
    }

    /// 💰 복냥이 설명: 확률·하루 최대를 숫자 그대로 (확률 정보 공개) + 오늘 받은 양
    private func luckyDesc(_ p: Profile) -> String {
        let today = p.luckyDay == OnlineService.today() ? p.luckyCoins : 0
        let pct = Shop.luckyChancePct, mx = Shop.luckyDailyMax
        return tr("💰 상대를 공격할 때마다 \(pct)% 확률로 상점 포인트 +1\n(하루 최대 \(mx) · 오늘 \(today)/\(mx)) · 이기면 금화 폭죽 · 영구 캐릭터",
                  "💰 Each attack has a \(pct)% chance to give +1 shop point\n(max \(mx) a day · today \(today)/\(mx)) · permanent",
                  zh: "💰 每次攻击有 \(pct)% 概率获得 1 商店积分\n（每天最多 \(mx) · 今天 \(today)/\(mx)）· 永久",
                  es: "💰 Cada ataque tiene un \(pct)% de dar +1 punto de tienda\n(máx. \(mx) al día · hoy \(today)/\(mx)) · permanente",
                  fr: "💰 Chaque attaque a \(pct)% de chances de donner +1 point boutique\n(max \(mx) par jour · aujourd’hui \(today)/\(mx)) · permanent",
                  de: "💰 Jeder Angriff: \(pct)% Chance auf +1 Shop-Punkt\n(max. \(mx) pro Tag · heute \(today)/\(mx)) · dauerhaft",
                  pt: "💰 Cada ataque tem \(pct)% de chance de dar +1 ponto da loja\n(máx. \(mx) por dia · hoje \(today)/\(mx)) · permanente",
                  ru: "💰 Каждая атака: \(pct)% шанс +1 очко магазина\n(макс. \(mx) в день · сегодня \(today)/\(mx)) · навсегда",
                  ja: "💰 攻撃するたびに\(pct)%の確率でショップポイント+1\n（1日最大\(mx) · 今日 \(today)/\(mx)）· 永久")
    }

    private func buyButton(_ item: String, _ label: String, _ price: Int, _ p: Profile) -> some View {
        Button { confirm = (item, label, price, 1) } label: {
            Text(tf("{0}점에 사기", price)).font(.system(size: 14, weight: .bold)).foregroundColor(.white)
                .lineLimit(1).minimumScaleFactor(0.7)
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(Capsule().fill(UiColor.primary.opacity(p.score + p.coins >= price ? 1 : 0.4)))
        }
        .disabled(online.busy || p.score + p.coins < price)
    }

    /// 묶음 할인 버튼들: "1개 · 50점", "3개 · 135점 🔥−10%" … 두 개씩 한 줄
    private func bundleButtons(_ item: String, _ label: String, _ p: Profile) -> some View {
        let list = Shop.bundles(item)
        let unit = list.first?.price ?? 1
        let rows: [[(qty: Int, price: Int)]] = stride(from: 0, to: list.count, by: 2).map { Array(list[$0..<min($0 + 2, list.count)]) }
        return VStack(alignment: .leading, spacing: 6) {
            ForEach(0..<rows.count, id: \.self) { r in
                HStack(spacing: 6) {
                    ForEach(0..<rows[r].count, id: \.self) { k in
                        bundleButton(item, label, rows[r][k].qty, rows[r][k].price, unit, p)
                    }
                }
            }
        }
    }

    private func bundleButton(_ item: String, _ label: String, _ qty: Int, _ price: Int, _ unit: Int, _ p: Profile) -> some View {
        let off = 100 - price * 100 / (unit * qty)
        let canBuy = p.score + p.coins >= price
        // 글자 크기를 키운 폰에서도 잘리지 않게: 개수 / 가격 / 할인을 줄을 나눠 표시
        let title = tr("\(qty)개", "×\(qty)", zh: "\(qty) 个", ja: "\(qty)個")
        let cost = tr("\(price)점", "\(price) pts", zh: "\(price) 分", es: "\(price) pts", fr: "\(price) pts", de: "\(price) Pkt", pt: "\(price) pts", ru: "\(price) очк.", ja: "\(price)点")
        return Button { confirm = (item, qty == 1 ? label : "\(label) ×\(qty)", price, qty) } label: {
            VStack(spacing: 1) {
                Text(title).font(.system(size: 14, weight: .black)).lineLimit(1).minimumScaleFactor(0.7)
                Text(cost).font(.system(size: 13, weight: .bold)).lineLimit(1).minimumScaleFactor(0.7)
                if off > 0 {
                    Text("🔥 −\(off)%").font(.system(size: 11, weight: .black)).foregroundColor(UiColor.gold)
                }
            }
            .foregroundColor(.white)
            .frame(maxWidth: .infinity, minHeight: 40)
            .padding(.horizontal, 6).padding(.vertical, 4)
            .background(RoundedRectangle(cornerRadius: 14).fill((qty == 1 ? UiColor.primary : UiColor.accent).opacity(canBuy ? 1 : 0.4)))
        }
        .disabled(online.busy || !canBuy)
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
                            Text(pts(price)).font(.system(size: 14, weight: .bold)).foregroundColor(UiColor.primaryDark).lineLimit(1).fixedSize()
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
