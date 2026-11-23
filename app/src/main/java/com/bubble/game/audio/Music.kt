package com.bubble.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bubble.game.R
import com.bubble.game.game.Animal
import com.bubble.game.game.Side

/**
 * 배경음악. 로비와 게임(내 캐릭터별)에 다른 곡을 반복 재생합니다.
 * 곡은 tools/music/make_music.py로 직접 합성한 것이라 저작권 걱정이 없습니다.
 * 앱이 뒤로 가면 멈추고, 켜고 끄기 설정은 기기에 저장합니다.
 */
object Music {
    private const val PREFS = "bubble_settings"
    private const val KEY = "music_on"
    private const val LOBBY_KEY = "lobby_track"
    private const val VOLUME = 0.3f
    /** 큰 효과음(펑·승리 등)이 날 때 배경음을 잠깐 줄이는 크기 */
    private const val DUCK = 0.1f

    /** 음악 켜짐 (화면의 🔊/🔇 버튼이 이 값을 봄) */
    var enabled by mutableStateOf(true)
        private set

    private var app: Context? = null
    private var player: MediaPlayer? = null
    private var playing: Int? = null
    private var wanted: Int? = null
    private var foreground = true
    private val main = Handler(Looper.getMainLooper())
    private var fade: Runnable? = null

    /** 🎵 로비 음악으로 고른 캐릭터 이름 (null이면 기본 로비 곡). 기기에 저장 */
    var lobbyChoice by mutableStateOf<String?>(null)
        private set

    fun chooseLobby(id: String?) {
        lobbyChoice = id
        app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString(LOBBY_KEY, id)?.apply()
    }

    /** 로비 곡: 고른 캐릭터를 가지고 있으면 그 캐릭터 곡, 아니면 기본 로비 곡 */
    fun lobbyTrack(owned: (Animal?) -> Boolean): Int {
        val id = lobbyChoice ?: return lobby
        if (id == "CAT") return R.raw.bgm_cat
        val a = Animal.of(id) ?: return lobby
        return if (owned(a)) gameTrack(a, if (a == Animal.HAMSTER || !a.looksLikeCat) Side.HAMSTER else Side.CAT) else lobby
    }

    fun init(context: Context) {
        app = context.applicationContext
        enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)
        lobbyChoice = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LOBBY_KEY, null)
        if (pool == null) {
            pool = SoundPool.Builder()
                .setMaxStreams(6)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .build()
                .also { p -> Sfx.entries.forEach { s -> sfxIds[s] = p.load(context.applicationContext, s.res, 1) } }
        }
    }

    /** 효과음 (tools/music/make_sfx.py로 합성). 🔇이면 소리 없음 */
    enum class Sfx(val res: Int, val volume: Float) {
        SELECT(R.raw.sfx_select, 0.7f),
        MATCH(R.raw.sfx_match, 1.0f),
        TURN(R.raw.sfx_turn, 0.8f),
        TICK(R.raw.sfx_tick, 0.7f),
        CHECKIN(R.raw.sfx_checkin, 1.0f),
        MISSION(R.raw.sfx_mission, 0.95f),
        RANK_UP(R.raw.sfx_rank_up, 1.0f),
        RANK_DOWN(R.raw.sfx_rank_down, 0.9f),
        WEEKLY(R.raw.sfx_weekly, 1.0f),
        BUY(R.raw.sfx_buy, 1.0f),
        EQUIP(R.raw.sfx_equip, 0.8f),
        SHOOT(R.raw.sfx_shoot, 0.75f),
        HIT(R.raw.sfx_hit, 0.9f),
        POP(R.raw.sfx_pop, 1.0f),
        SPLIT(R.raw.sfx_split, 0.9f),
        SPLIT_INK(R.raw.sfx_split_ink, 0.9f),
        SPLIT_ICE(R.raw.sfx_split_ice, 0.9f),
        SPLIT_FIRE(R.raw.sfx_split_fire, 0.9f),
        SPLIT_MAGIC(R.raw.sfx_split_magic, 0.9f),
        SPLIT_BAMBOO(R.raw.sfx_split_bamboo, 0.9f),
        SPLIT_BOING(R.raw.sfx_split_boing, 0.9f),
        SPLIT_GOLD(R.raw.sfx_split_gold, 0.9f),
        POTION(R.raw.sfx_potion, 1.0f),
        COIN(R.raw.sfx_coin, 0.85f),
        COIN_DRAGON(R.raw.sfx_coin_dragon, 0.85f),
        COIN_RABBIT(R.raw.sfx_coin_rabbit, 0.95f),
        COIN_UNICORN(R.raw.sfx_coin_unicorn, 0.95f),
        MARK_ICE(R.raw.sfx_mark_ice, 0.85f),
        MARK_GOLD(R.raw.sfx_mark_gold, 0.8f),
        MARK_HEART(R.raw.sfx_mark_heart, 0.8f),
        MARK_CLAW(R.raw.sfx_mark_claw, 0.8f),
        MARK_STAMP(R.raw.sfx_mark_stamp, 0.9f),
        MARK_INK(R.raw.sfx_mark_ink, 0.85f),
        MARK_SNOW(R.raw.sfx_mark_snow, 0.8f),
        MARK_BAMBOO(R.raw.sfx_mark_bamboo, 0.85f),
        MARK_FIRE(R.raw.sfx_mark_fire, 0.85f),
        MARK_HOP(R.raw.sfx_mark_hop, 0.85f),
        MARK_MAGIC(R.raw.sfx_mark_magic, 0.8f),
        WIN(R.raw.sfx_win, 1.0f),
        LOSE(R.raw.sfx_lose, 0.9f),
    }

    private var pool: SoundPool? = null
    private val sfxIds = mutableMapOf<Sfx, Int>()

    /** 배경음을 줄였다가 되돌릴 큰 효과음 */
    private val loud = setOf(Sfx.POP, Sfx.WIN, Sfx.LOSE, Sfx.POTION, Sfx.MATCH, Sfx.RANK_UP, Sfx.RANK_DOWN, Sfx.WEEKLY, Sfx.CHECKIN, Sfx.BUY, Sfx.COIN_RABBIT, Sfx.COIN_UNICORN)

    fun sfx(s: Sfx) {
        if (!enabled || !foreground) return
        val id = sfxIds[s] ?: return
        pool?.play(id, s.volume, s.volume, 2, 0, 1f)
        if (s in loud) duck(if (s == Sfx.POP) 600L else 1200L)
    }

    /** 효과음이 묻히지 않게 배경음을 잠깐 줄였다가 천천히 되돌림 */
    private fun duck(ms: Long) {
        val p = player ?: return
        fade?.let(main::removeCallbacks)
        runCatching { p.setVolume(DUCK, DUCK) }
        val back = Runnable { if (p === player) fadeIn(p, from = DUCK) }
        fade = back
        main.postDelayed(back, ms)
    }

    fun toggle() {
        enabled = !enabled
        app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean(KEY, enabled)?.apply()
        apply()
    }

    /** 이 곡을 틀어 둠 (이미 같은 곡이면 그대로) */
    fun play(res: Int) {
        wanted = res
        apply()
    }

    fun onPause() {
        foreground = false
        apply()
    }

    fun onResume() {
        foreground = true
        apply()
    }

    private fun apply() {
        val ctx = app ?: return
        val res = wanted
        if (!enabled || !foreground || res == null) {
            runCatching { player?.takeIf { it.isPlaying }?.pause() }
            return
        }
        if (playing != res) {
            release()
            player = runCatching {
                MediaPlayer.create(ctx, res)?.apply { isLooping = true; setVolume(0f, 0f) }
            }.getOrNull()
            playing = if (player != null) res else null
        }
        val p = player ?: return
        runCatching { if (!p.isPlaying) p.start() }
        fadeIn(p)
    }

    /** 0에서 살짝 커지며 시작 (곡이 바뀔 때 툭 끊기지 않게) */
    private fun fadeIn(p: MediaPlayer, from: Float = 0f) {
        fade?.let(main::removeCallbacks)
        var v = from
        val step = object : Runnable {
            override fun run() {
                v = (v + 0.03f).coerceAtMost(VOLUME)
                runCatching { p.setVolume(v, v) }
                if (v < VOLUME && p === player) main.postDelayed(this, 40)
            }
        }
        fade = step
        main.post(step)
    }

    private fun release() {
        fade?.let(main::removeCallbacks)
        runCatching { player?.release() }
        player = null
        playing = null
    }

    /** 캐릭터별 분할 소리 */
    fun splitSfx(skin: Animal?): Sfx = when (skin) {
        Animal.OCTOPUS -> Sfx.SPLIT_INK
        Animal.PENGUIN, Animal.SNOW_HAMSTER -> Sfx.SPLIT_ICE
        Animal.DRAGON -> Sfx.SPLIT_FIRE
        Animal.UNICORN, Animal.PINK_CAT -> Sfx.SPLIT_MAGIC
        Animal.PANDA -> Sfx.SPLIT_BAMBOO
        Animal.RABBIT -> Sfx.SPLIT_BOING
        Animal.LUCKY_CAT, Animal.GOLD_HAMSTER -> Sfx.SPLIT_GOLD
        else -> Sfx.SPLIT
    }

    /** 맞은 방울 흔적 소리 (기본 고양이·햄스터는 통!) */
    fun markSfx(kind: com.bubble.game.ui.MarkKind): Sfx = when (kind) {
        com.bubble.game.ui.MarkKind.ICE -> Sfx.MARK_ICE
        com.bubble.game.ui.MarkKind.GOLD -> Sfx.MARK_GOLD
        com.bubble.game.ui.MarkKind.HEART -> Sfx.MARK_HEART
        com.bubble.game.ui.MarkKind.CLAW -> Sfx.MARK_CLAW
        com.bubble.game.ui.MarkKind.STAMP -> Sfx.MARK_STAMP
        com.bubble.game.ui.MarkKind.INK -> Sfx.MARK_INK
        com.bubble.game.ui.MarkKind.SNOW -> Sfx.MARK_SNOW
        com.bubble.game.ui.MarkKind.BAMBOO -> Sfx.MARK_BAMBOO
        com.bubble.game.ui.MarkKind.FIRE -> Sfx.MARK_FIRE
        com.bubble.game.ui.MarkKind.HOP -> Sfx.MARK_HOP
        com.bubble.game.ui.MarkKind.MAGIC -> Sfx.MARK_MAGIC
        com.bubble.game.ui.MarkKind.PAW, com.bubble.game.ui.MarkKind.SEED -> Sfx.HIT
    }

    /** 캐릭터별 💰 받는 소리 (게임 중: 복냥이·아기 용) */
    fun coinSfx(skin: Animal?): Sfx = if (skin == Animal.DRAGON) Sfx.COIN_DRAGON else Sfx.COIN

    val lobby: Int get() = R.raw.bgm_lobby

    /** 매칭·친구 기다리는 동안 */
    val matching: Int get() = R.raw.bgm_matching

    /** 게임 곡: 내 캐릭터별. 소모품 동물(양·강아지·기린)이나 기본이면 고양이/햄스터 곡 */
    fun gameTrack(skin: Animal?, side: Side): Int = when (skin) {
        Animal.PINK_CAT -> R.raw.bgm_pink_cat
        Animal.BLACK_CAT -> R.raw.bgm_black_cat
        Animal.SNOW_HAMSTER -> R.raw.bgm_snow_hamster
        Animal.GOLD_HAMSTER -> R.raw.bgm_gold_hamster
        Animal.LUCKY_CAT -> R.raw.bgm_lucky_cat
        Animal.OCTOPUS -> R.raw.bgm_octopus
        Animal.PENGUIN -> R.raw.bgm_penguin
        Animal.PANDA -> R.raw.bgm_panda
        Animal.DRAGON -> R.raw.bgm_dragon
        Animal.RABBIT -> R.raw.bgm_rabbit
        Animal.UNICORN -> R.raw.bgm_unicorn
        Animal.HAMSTER -> R.raw.bgm_hamster
        else -> if (side == Side.CAT) R.raw.bgm_cat else R.raw.bgm_hamster
    }
}
