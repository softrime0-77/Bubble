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
    private const val VOLUME = 0.45f

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

    fun init(context: Context) {
        app = context.applicationContext
        enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)
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
        SELECT(R.raw.sfx_select, 0.5f),
        BUY(R.raw.sfx_buy, 0.8f),
        EQUIP(R.raw.sfx_equip, 0.6f),
        SHOOT(R.raw.sfx_shoot, 0.55f),
        HIT(R.raw.sfx_hit, 0.7f),
        POP(R.raw.sfx_pop, 0.9f),
        SPLIT(R.raw.sfx_split, 0.7f),
        SPLIT_INK(R.raw.sfx_split_ink, 0.7f),
        SPLIT_ICE(R.raw.sfx_split_ice, 0.7f),
        SPLIT_FIRE(R.raw.sfx_split_fire, 0.7f),
        SPLIT_MAGIC(R.raw.sfx_split_magic, 0.7f),
        SPLIT_BAMBOO(R.raw.sfx_split_bamboo, 0.7f),
        SPLIT_BOING(R.raw.sfx_split_boing, 0.7f),
        SPLIT_GOLD(R.raw.sfx_split_gold, 0.7f),
        POTION(R.raw.sfx_potion, 0.8f),
        COIN(R.raw.sfx_coin, 0.6f),
        COIN_DRAGON(R.raw.sfx_coin_dragon, 0.65f),
        COIN_RABBIT(R.raw.sfx_coin_rabbit, 0.7f),
        COIN_UNICORN(R.raw.sfx_coin_unicorn, 0.7f),
        WIN(R.raw.sfx_win, 0.8f),
        LOSE(R.raw.sfx_lose, 0.7f),
    }

    private var pool: SoundPool? = null
    private val sfxIds = mutableMapOf<Sfx, Int>()

    fun sfx(s: Sfx) {
        if (!enabled || !foreground) return
        val id = sfxIds[s] ?: return
        pool?.play(id, s.volume, s.volume, 1, 0, 1f)
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
    private fun fadeIn(p: MediaPlayer) {
        fade?.let(main::removeCallbacks)
        var v = 0f
        val step = object : Runnable {
            override fun run() {
                v = (v + 0.05f).coerceAtMost(VOLUME)
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

    /** 캐릭터별 💰 받는 소리 (게임 중: 복냥이·아기 용) */
    fun coinSfx(skin: Animal?): Sfx = if (skin == Animal.DRAGON) Sfx.COIN_DRAGON else Sfx.COIN

    val lobby: Int get() = R.raw.bgm_lobby

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
