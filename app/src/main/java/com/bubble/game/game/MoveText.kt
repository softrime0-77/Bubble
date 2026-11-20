package com.bubble.game.game

import com.bubble.game.i18n.tr

/** 한 수의 정보. 화면 언어로 설명 문장을 만들 때 씁니다 (서버도 같은 정보를 보냄). */
data class MoveInfo(
    val side: Side,
    val move: Move,
    /** 더한 마리 수 (Add), 분할 전 마리 수 (Split) */
    val power: Int,
    /** 더한 결과 (터졌으면 터지기 전 합) */
    val sum: Int,
    val boom: Boolean,
    /** 분할 뒤 남은 분할 횟수 */
    val splitsLeft: Int,
)

object MoveText {
    fun describe(i: MoveInfo): String {
        val who = i.side.label
        return when (val m = i.move) {
            is Move.Add -> {
                val a = GameLogic.setName(m.from)
                val b = GameLogic.setName(m.to)
                val p = i.power
                val head = if (m.toOpponent) {
                    tr(
                        "$who: $a(${p})로 상대 $b 공격", "$who: $a ($p) attacks their $b",
                        zh = "${who}：${a}（${p}）攻击对手的$b", hi = "$who: $a ($p) ने विरोधी के $b पर हमला किया",
                        es = "$who: $a ($p) ataca su $b", fr = "$who : $a ($p) attaque son $b",
                        ar = "$who: $a ($p) يهاجم $b للخصم", de = "$who: $a ($p) greift gegnerische $b an",
                        pt = "$who: $a ($p) ataca o $b rival", ru = "$who: $a ($p) атакует $b соперника",
                        ja = "${who}：${a}（${p}）で相手の${b}を攻撃",
                    )
                } else {
                    tr(
                        "$who: $a(${p})로 내 $b 강화", "$who: $a ($p) boosts own $b",
                        zh = "${who}：${a}（${p}）强化自己的$b", hi = "$who: $a ($p) ने अपने $b को बूस्ट किया",
                        es = "$who: $a ($p) refuerza su propio $b", fr = "$who : $a ($p) renforce son propre $b",
                        ar = "$who: $a ($p) يقوّي $b الخاص به", de = "$who: $a ($p) stärkt eigene $b",
                        pt = "$who: $a ($p) reforça o próprio $b", ru = "$who: $a ($p) усиливает свою $b",
                        ja = "${who}：${a}（${p}）で自分の${b}を強化",
                    )
                }
                val s = i.sum
                head + if (i.boom) {
                    tr(
                        " → 💥 펑!", " → 💥 POP!", zh = " → 💥 啪！", hi = " → 💥 फट!", es = " → 💥 ¡PUM!",
                        fr = " → 💥 POP !", ar = " → 💥 بوب!", de = " → 💥 PLOPP!", pt = " → 💥 POP!",
                        ru = " → 💥 ХЛОП!", ja = " → 💥 パン！",
                    )
                } else {
                    tr(" → ${s}마리", " → $s", zh = " → $s 只", ja = " → ${s}匹")
                }
            }
            is Move.Split -> {
                val a = GameLogic.setName(m.from)
                val h = i.power / 2
                val l = i.splitsLeft
                tr(
                    "$who: $a 분할 → ${h}마리, ${h}마리 ✂️ (분할 -1, 남은 ${l}회)",
                    "$who: split $a → $h, $h ✂️ (split -1, $l left)",
                    zh = "${who}：分裂$a → $h 只、$h 只 ✂️（分裂 -1，剩 $l 次）",
                    hi = "$who: $a को बाँटा → $h, $h ✂️ (स्प्लिट -1, $l बाकी)",
                    es = "$who: divide $a → $h, $h ✂️ (división -1, quedan $l)",
                    fr = "$who : divise $a → $h, $h ✂️ (division -1, $l restantes)",
                    ar = "$who: قسّم $a ← ${h}، $h ✂️ (تقسيم -1، متبقٍ $l)",
                    de = "$who: teilt $a → $h, $h ✂️ (Teilung -1, noch $l)",
                    pt = "$who: divide $a → $h, $h ✂️ (divisão -1, restam $l)",
                    ru = "$who: делит $a → $h, $h ✂️ (деление -1, осталось $l)",
                    ja = "${who}：${a}を分割 → ${h}匹、${h}匹 ✂️（分割 -1、残り${l}回）",
                )
            }
            is Move.Revive -> {
                val a = GameLogic.setName(m.index)
                tr(
                    "$who: 💖 부활물약으로 $a 부활!", "$who: 💖 revived $a with a potion!",
                    zh = "${who}：💖 用复活药水复活了${a}！", hi = "$who: 💖 पोशन से $a वापस आया!",
                    es = "$who: 💖 ¡revivió $a con una poción!", fr = "$who : 💖 a ranimé $a avec une potion !",
                    ar = "$who: 💖 أحيا $a بجرعة!", de = "$who: 💖 hat $a mit einem Trank wiederbelebt!",
                    pt = "$who: 💖 reviveu $a com uma poção!", ru = "$who: 💖 оживил(а) $a зельем!",
                    ja = "${who}：💖 復活薬で${a}が復活！",
                )
            }
        }
    }
}
