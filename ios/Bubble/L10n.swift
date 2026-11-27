import BubbleCore
import Foundation
import SwiftUI

/// 화면 언어. 안드로이드 앱과 같은 11개 언어. 처음에는 기기 언어, 로비 메뉴에서 바꾸면 저장.
enum LangCode: String, CaseIterable, Identifiable {
    case ko, en, zh, hi, es, fr, ar, de, pt, ru, ja

    var id: String { rawValue }

    var label: String {
        switch self {
        case .ko: return "한국어"
        case .en: return "English"
        case .zh: return "中文"
        case .hi: return "हिन्दी"
        case .es: return "Español"
        case .fr: return "Français"
        case .ar: return "العربية"
        case .de: return "Deutsch"
        case .pt: return "Português"
        case .ru: return "Русский"
        case .ja: return "日本語"
        }
    }

    var isRTL: Bool { self == .ar }

    static func detect() -> LangCode {
        let code = Locale.preferredLanguages.first.map { String($0.prefix(2)) } ?? "en"
        return LangCode(rawValue: code) ?? .en
    }
}

final class LangStore: ObservableObject {
    static let shared = LangStore()
    private static let key = "lang"

    @Published var code: LangCode {
        didSet { UserDefaults.standard.set(code.rawValue, forKey: Self.key) }
    }

    private init() {
        code = UserDefaults.standard.string(forKey: Self.key).flatMap(LangCode.init(rawValue:)) ?? LangCode.detect()
    }
}

/// 한국어 문장을 키로 현재 언어 번역을 찾음 (Strings.swift 표). 없으면 영어, 그것도 없으면 한국어.
func t(_ ko: String) -> String {
    let code = LangStore.shared.code
    if code == .ko { return ko }
    let row = staticStrings[ko]
    return row?[code.rawValue] ?? row?["en"] ?? ko
}

/// 값이 들어가는 문장용: 언어별 문장을 직접 넘김. 빠진 언어는 영어.
func tr(
    _ ko: String, _ en: String,
    zh: String? = nil, hi: String? = nil, es: String? = nil, fr: String? = nil, ar: String? = nil,
    de: String? = nil, pt: String? = nil, ru: String? = nil, ja: String? = nil
) -> String {
    switch LangStore.shared.code {
    case .ko: return ko
    case .en: return en
    case .zh: return zh ?? en
    case .hi: return hi ?? en
    case .es: return es ?? en
    case .fr: return fr ?? en
    case .ar: return ar ?? en
    case .de: return de ?? en
    case .pt: return pt ?? en
    case .ru: return ru ?? en
    case .ja: return ja ?? en
    }
}

/// 언어 순서(ko, en, zh, hi, es, fr, ar, de, pt, ru, ja)대로 된 목록에서 고르기
func pick(_ names: [String]) -> String {
    let i = LangCode.allCases.firstIndex(of: LangStore.shared.code) ?? 1
    return names.indices.contains(i) ? names[i] : names[1]
}

func secs(_ n: Int) -> String {
    tr("\(n)초", "\(n)s", zh: "\(n)秒", hi: "\(n)से", ar: "\(n)ث", ru: "\(n)с", ja: "\(n)秒")
}

func pts(_ n: Int) -> String {
    tr("\(n)점", "\(n) pts", zh: "\(n) 分", hi: "\(n) अंक", ar: "\(n) نقطة", de: "\(n) Pkt", ru: "\(n) очк.", ja: "\(n)点")
}

extension Side {
    /// 화면용 이름 (대전 중에는 [SideLook]: 나 = 햄스터, 상대 = 고양이)
    var label: String { SideLook.of(self) == .cat ? t("고양이") : t("햄스터") }
}

extension AiLevel {
    var label: String {
        switch self {
        case .easy: return pick(["쉬움", "Easy", "简单", "आसान", "Fácil", "Facile", "سهل", "Leicht", "Fácil", "Легко", "かんたん"])
        case .normal: return pick(["보통", "Normal", "普通", "सामान्य", "Normal", "Normal", "عادي", "Normal", "Normal", "Нормально", "ふつう"])
        case .hard: return pick(["어려움", "Hard", "困难", "कठिन", "Difícil", "Difficile", "صعب", "Schwer", "Difícil", "Сложно", "むずかしい"])
        }
    }
}

extension Rank {
    var label: String {
        pick([
            ["흙", "Dirt", "泥土", "मिट्टी", "Tierra", "Terre", "تراب", "Erde", "Terra", "Земля", "土"],
            ["구리", "Copper", "铜", "कॉपर", "Cobre", "Cuivre", "نحاس", "Kupfer", "Cobre", "Медь", "銅"],
            ["은", "Silver", "银", "सिल्वर", "Plata", "Argent", "فضة", "Silber", "Prata", "Серебро", "銀"],
            ["금", "Gold", "金", "गोल्ड", "Oro", "Or", "ذهب", "Gold", "Ouro", "Золото", "金"],
            ["루비", "Ruby", "红宝石", "रूबी", "Rubí", "Rubis", "ياقوت أحمر", "Rubin", "Rubi", "Рубин", "ルビー"],
            ["사파이어", "Sapphire", "蓝宝石", "नीलम", "Zafiro", "Saphir", "ياقوت أزرق", "Saphir", "Safira", "Сапфир", "サファイア"],
            ["다이아", "Diamond", "钻石", "डायमंड", "Diamante", "Diamant", "ماس", "Diamant", "Diamante", "Алмаз", "ダイヤ"],
            ["핑크다이아", "Pink Diamond", "粉钻", "पिंक डायमंड", "Diamante rosa", "Diamant rose", "ماس وردي", "Pinker Diamant", "Diamante rosa", "Розовый алмаз", "ピンクダイヤ"],
        ][rawValue])
    }
}

extension Animal {
    var label: String {
        switch self {
        case .sheep: return pick(["시계 햄스터 1", "Time Hamster 1", "时间仓鼠 1", "टाइम हैम्स्टर 1", "Hámster del tiempo 1", "Hamster du temps 1", "هامستر الوقت 1", "Zeithamster 1", "Hamster do tempo 1", "Хомяк времени 1", "とけいハムスター1"])
        case .dog: return pick(["시계 햄스터 2", "Time Hamster 2", "时间仓鼠 2", "टाइम हैम्स्टर 2", "Hámster del tiempo 2", "Hamster du temps 2", "هامستر الوقت 2", "Zeithamster 2", "Hamster do tempo 2", "Хомяк времени 2", "とけいハムスター2"])
        case .giraffe: return pick(["시계 햄스터 3", "Time Hamster 3", "时间仓鼠 3", "टाइम हैम्स्टर 3", "Hámster del tiempo 3", "Hamster du temps 3", "هامستر الوقت 3", "Zeithamster 3", "Hamster do tempo 3", "Хомяк времени 3", "とけいハムスター3"])
        case .hamster: return pick(["햄스터", "Hamster", "仓鼠", "हैम्स्टर", "Hámster", "Hamster", "هامستر", "Hamster", "Hamster", "Хомяк", "ハムスター"])
        case .pinkCat: return pick(["핑크 햄스터", "Pink Hamster", "粉红仓鼠", "गुलाबी हैम्स्टर", "Hámster rosa", "Hamster rose", "هامستر وردي", "Pinker Hamster", "Hamster rosa", "Розовый хомяк", "ピンクハムスター"])
        case .blackCat: return pick(["까망 햄스터", "Black Hamster", "小黑仓鼠", "काला हैम्स्टर", "Hámster negro", "Hamster noir", "هامستر أسود", "Schwarzer Hamster", "Hamster preto", "Чёрный хомяк", "くろハムスター"])
        case .snowHamster: return pick(["눈송이 햄스터", "Snow Hamster", "雪花仓鼠", "स्नो हैम्स्टर", "Hámster nieve", "Hamster neige", "هامستر الثلج", "Schneehamster", "Hamster neve", "Снежный хомяк", "ゆきハムスター"])
        case .goldHamster: return pick(["황금 햄스터", "Golden Hamster", "黄金仓鼠", "गोल्डन हैम्स्टर", "Hámster dorado", "Hamster doré", "هامستر ذهبي", "Goldhamster", "Hamster dourado", "Золотой хомяк", "きんハムスター"])
        case .octopus: return pick(["물햄스터", "Water Hamster", "水仓鼠", "पानी हैम्स्टर", "Hámster de agua", "Hamster d’eau", "هامستر الماء", "Wasserhamster", "Hamster d’água", "Водяной хомяк", "みずハムスター"])
        case .penguin: return pick(["펭귄", "Penguin", "企鹅", "पेंगुइन", "Pingüino", "Pingouin", "بطريق", "Pinguin", "Pinguim", "Пингвин", "ペンギン"])
        case .panda: return pick(["출석 햄스터", "Check-in Hamster", "签到仓鼠", "हाज़िरी हैम्स्टर", "Hámster asistente", "Hamster de présence", "هامستر الحضور", "Check-in-Hamster", "Hamster da presença", "Хомяк-отметка", "しゅっせきハムスター"])
        case .dragon: return pick(["불꽃 햄스터", "Fire Hamster", "火焰仓鼠", "आग हैम्स्टर", "Hámster de fuego", "Hamster de feu", "هامستر النار", "Feuerhamster", "Hamster de fogo", "Огненный хомяк", "ほのおハムスター"])
        case .rabbit: return pick(["연승 햄스터", "Streak Hamster", "连胜仓鼠", "जीत हैम्स्टर", "Hámster racha", "Hamster série", "هامستر الانتصارات", "Siegeshamster", "Hamster da sequência", "Хомяк-серия", "れんしょうハムスター"])
        case .unicorn: return pick(["유니콘 햄스터", "Unicorn Hamster", "独角兽仓鼠", "यूनिकॉर्न हैम्स्टर", "Hámster unicornio", "Hamster licorne", "هامستر وحيد القرن", "Einhorn-Hamster", "Hamster unicórnio", "Хомяк-единорог", "ユニコーンハムスター"])
        case .luckyCat: return pick(["복햄이", "Lucky Hamster", "招财仓鼠", "लकी हैम्स्टर", "Hámster de la suerte", "Hamster porte-bonheur", "هامستر الحظ", "Glückshamster", "Hamster da sorte", "Хомяк удачи", "まねきハムスター"])
        }
    }
}

func setName(_ i: Int) -> String {
    tr("\(i + 1)번 세트", "Set \(i + 1)", zh: "第 \(i + 1) 组", hi: "सेट \(i + 1)", es: "Grupo \(i + 1)", fr: "Groupe \(i + 1)",
       ar: "المجموعة \(i + 1)", de: "Gruppe \(i + 1)", pt: "Grupo \(i + 1)", ru: "Группа \(i + 1)", ja: "\(i + 1)番セット")
}

/// 한 수 설명 문장 (안드로이드 MoveText와 같은 문구)
enum MoveText {
    static func describe(_ i: MoveInfo) -> String {
        let who = i.side.label
        switch i.move {
        case let .add(from, toOpponent, to):
            let a = setName(from), b = setName(to), p = i.power
            let head = toOpponent
                ? tr("\(who): \(a)(\(p))로 상대 \(b) 공격", "\(who): \(a) (\(p)) attacks their \(b)",
                     zh: "\(who)：\(a)（\(p)）攻击对手的\(b)", hi: "\(who): \(a) (\(p)) ने विरोधी के \(b) पर हमला किया",
                     es: "\(who): \(a) (\(p)) ataca su \(b)", fr: "\(who) : \(a) (\(p)) attaque son \(b)",
                     ar: "\(who): \(a) (\(p)) يهاجم \(b) للخصم", de: "\(who): \(a) (\(p)) greift gegnerische \(b) an",
                     pt: "\(who): \(a) (\(p)) ataca o \(b) rival", ru: "\(who): \(a) (\(p)) атакует \(b) соперника",
                     ja: "\(who)：\(a)（\(p)）で相手の\(b)を攻撃")
                : tr("\(who): \(a)(\(p))로 내 \(b) 강화", "\(who): \(a) (\(p)) boosts own \(b)",
                     zh: "\(who)：\(a)（\(p)）强化自己的\(b)", hi: "\(who): \(a) (\(p)) ने अपने \(b) को बूस्ट किया",
                     es: "\(who): \(a) (\(p)) refuerza su propio \(b)", fr: "\(who) : \(a) (\(p)) renforce son propre \(b)",
                     ar: "\(who): \(a) (\(p)) يقوّي \(b) الخاص به", de: "\(who): \(a) (\(p)) stärkt eigene \(b)",
                     pt: "\(who): \(a) (\(p)) reforça o próprio \(b)", ru: "\(who): \(a) (\(p)) усиливает свою \(b)",
                     ja: "\(who)：\(a)（\(p)）で自分の\(b)を強化")
            let tail = i.boom
                ? tr(" → 💥 펑!", " → 💥 POP!", zh: " → 💥 啪！", hi: " → 💥 फट!", es: " → 💥 ¡PUM!", fr: " → 💥 POP !",
                     ar: " → 💥 بوب!", de: " → 💥 PLOPP!", pt: " → 💥 POP!", ru: " → 💥 ХЛОП!", ja: " → 💥 パン！")
                : tr(" → \(i.sum)마리", " → \(i.sum)", zh: " → \(i.sum) 只", ja: " → \(i.sum)匹")
            return head + tail
        case let .split(from):
            let a = setName(from), h = i.power / 2, l = i.splitsLeft
            return tr("\(who): \(a) 분할 → \(h)마리, \(h)마리 ✂️ (분할 -1, 남은 \(l)회)", "\(who): split \(a) → \(h), \(h) ✂️ (split -1, \(l) left)",
                      zh: "\(who)：分裂\(a) → \(h) 只、\(h) 只 ✂️（分裂 -1，剩 \(l) 次）", hi: "\(who): \(a) को बाँटा → \(h), \(h) ✂️ (स्प्लिट -1, \(l) बाकी)",
                      es: "\(who): divide \(a) → \(h), \(h) ✂️ (división -1, quedan \(l))", fr: "\(who) : divise \(a) → \(h), \(h) ✂️ (division -1, \(l) restantes)",
                      ar: "\(who): قسّم \(a) ← \(h)، \(h) ✂️ (تقسيم -1، متبقٍ \(l))", de: "\(who): teilt \(a) → \(h), \(h) ✂️ (Teilung -1, noch \(l))",
                      pt: "\(who): divide \(a) → \(h), \(h) ✂️ (divisão -1, restam \(l))", ru: "\(who): делит \(a) → \(h), \(h) ✂️ (деление -1, осталось \(l))",
                      ja: "\(who)：\(a)を分割 → \(h)匹、\(h)匹 ✂️（分割 -1、残り\(l)回）")
        case let .revive(index):
            let a = setName(index)
            return tr("\(who): 💖 부활물약으로 \(a) 부활!", "\(who): 💖 revived \(a) with a potion!",
                      zh: "\(who)：💖 用复活药水复活了\(a)！", hi: "\(who): 💖 पोशन से \(a) वापस आया!",
                      es: "\(who): 💖 ¡revivió \(a) con una poción!", fr: "\(who) : 💖 a ranimé \(a) avec une potion !",
                      ar: "\(who): 💖 أحيا \(a) بجرعة!", de: "\(who): 💖 hat \(a) mit einem Trank wiederbelebt!",
                      pt: "\(who): 💖 reviveu \(a) com uma poção!", ru: "\(who): 💖 оживил(а) \(a) зельем!",
                      ja: "\(who)：💖 復活薬で\(a)が復活！")
        }
    }
}

/// 값이 들어가는 문장 (Templates.swift 표, 안드로이드 번역에서 자동 생성).
/// 예: tf("보유 {0}개", 3) → "Owned: 3". 표에 없으면 한국어 문장에 값을 넣어 돌려줌.
func tf(_ ko: String, _ args: Any...) -> String {
    let code = LangStore.shared.code
    let row = templateStrings[ko]
    let tpl = code == .ko ? ko : (row?[code.rawValue] ?? row?["en"] ?? ko)
    var out = ""
    var i = tpl.startIndex
    while i < tpl.endIndex {
        let c = tpl[i]
        let next = tpl.index(after: i)
        if c == "{", next < tpl.endIndex, tpl[next] == "{" {
            out.append("{"); i = tpl.index(after: next); continue
        }
        if c == "}", next < tpl.endIndex, tpl[next] == "}" {
            out.append("}"); i = tpl.index(after: next); continue
        }
        if c == "{", let close = tpl[next...].firstIndex(of: "}"), let n = Int(tpl[next..<close]), args.indices.contains(n) {
            out.append("\(args[n])"); i = tpl.index(after: close); continue
        }
        out.append(c)
        i = next
    }
    return out
}
