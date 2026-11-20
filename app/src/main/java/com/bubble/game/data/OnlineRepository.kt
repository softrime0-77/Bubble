package com.bubble.game.data

import com.bubble.game.i18n.Lang
import com.bubble.game.i18n.tr

import android.content.Context
import com.bubble.game.game.AiLevel
import com.bubble.game.game.Animal
import com.bubble.game.game.Board
import com.bubble.game.game.GameLogic
import com.bubble.game.game.Move
import com.bubble.game.game.MoveInfo
import com.bubble.game.game.MoveText
import com.bubble.game.game.Rank
import com.bubble.game.game.Side
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class Profile(
    val uid: String,
    val name: String,
    val score: Int,
    val wins: Int,
    val losses: Int,
    /** 가진 부활물약 수 */
    val potions: Int = 0,
    /** 산 동물 캐릭터들 */
    val owned: Set<Animal> = emptySet(),
    /** 장착한 동물 (null이면 기본 고양이/햄스터) */
    val equipped: Animal? = null,
) {
    val rank: Rank get() = Rank.of(score)
}

data class OnlineGame(
    val id: String,
    val catUid: String,
    val hamUid: String,
    val catName: String,
    val hamName: String,
    val catScore: Int,
    val hamScore: Int,
    val board: Board,
    val turn: Side,
    val finished: Boolean,
    val winner: Side?,
    val reason: String,
    val lastDesc: String,
    val explodedSide: Side?,
    val explodedIndex: Int,
    val moveNo: Int,
    val turnStartedAt: Long,
    val catDelta: Int?,
    val hamDelta: Int?,
    /** 이긴 쪽이 받은 계급 보너스 점수 (catDelta/hamDelta에 포함됨) */
    val catBonus: Int,
    val hamBonus: Int,
    val catSkin: Animal?,
    val hamSkin: Animal?,
    /** 각자 자기 차례에 쓸 수 있는 시간 */
    val catTurnMs: Long,
    val hamTurnMs: Long,
    /** AI 대전이면 난이도 (점수가 걸린 서버 AI 대전) */
    val vsAi: AiLevel?,
) {
    fun skinOf(side: Side): Animal? = if (side == Side.CAT) catSkin else hamSkin

    fun turnMsOf(side: Side): Long = if (side == Side.CAT) catTurnMs else hamTurnMs

    fun sideOf(uid: String): Side? = when (uid) {
        catUid -> Side.CAT
        hamUid -> Side.HAMSTER
        else -> null
    }

    fun deltaFor(side: Side): Int? = if (side == Side.CAT) catDelta else hamDelta

    fun bonusFor(side: Side): Int = if (side == Side.CAT) catBonus else hamBonus
}

/** 서버가 거절한 요청. [message]는 사용자에게 그대로 보여줘도 되는 한국어 문장입니다. */
class ServerException(message: String) : Exception(message)

/**
 * Firebase(구글 로그인 + Firestore + Cloud Functions)로 매칭, 대전, 점수를 처리합니다.
 *
 * 점수 조작을 막기 위해 앱은 games/queue 문서와 점수를 직접 쓰지 않습니다.
 * 매칭·수 두기·승패·점수는 모두 Cloud Functions(functions/src/index.ts)가 검증하고 기록하며,
 * 앱은 결과를 Firestore에서 읽기만 합니다.
 */
class OnlineRepository(context: Context) {

    val available: Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val functions by lazy { FirebaseFunctions.getInstance(REGION) }
    private val users get() = db.collection("users")
    private val games get() = db.collection("games")

    /** 구글로 로그인된 사용자 uid. 로그인 전(또는 예전 익명 계정)이면 null. */
    val currentUid: String?
        get() = auth.currentUser?.takeUnless { it.isAnonymous }?.uid

    suspend fun signInWithGoogle(idToken: String): String {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return auth.signInWithCredential(credential).await().user!!.uid
    }

    fun signOut() = auth.signOut()

    /** 첫 로그인이면 프로필을 만듭니다 (점수 1000점, 규칙에서 초기값 강제). */
    suspend fun ensureProfile(uid: String) {
        val ref = users.document(uid)
        db.runTransaction { tx ->
            if (!tx.get(ref).exists()) {
                tx.set(
                    ref,
                    mapOf(
                        "name" to randomNickname(),
                        "score" to START_SCORE,
                        "wins" to 0,
                        "losses" to 0,
                    ),
                )
            }
        }.await()
    }

    suspend fun setNickname(uid: String, name: String) {
        users.document(uid).update("name", name.trim().take(12)).await()
    }

    fun profileFlow(uid: String): Flow<Profile?> = callbackFlow {
        val reg = users.document(uid).addSnapshotListener { s, _ ->
            trySend(s?.takeIf { it.exists() }?.toProfile())
        }
        awaitClose { reg.remove() }
    }

    fun leaderboardFlow(): Flow<List<Profile>> = callbackFlow {
        val reg = users.orderBy("score", Query.Direction.DESCENDING).limit(30)
            .addSnapshotListener { s, _ ->
                if (s != null) trySend(s.documents.map { it.toProfile() })
            }
        awaitClose { reg.remove() }
    }

    /**
     * 랜덤 매칭. 상대를 찾을 때까지 서버에 주기적으로 물어보고, 게임 ID를 돌려줍니다.
     * 코루틴이 취소되면(매칭 취소) 대기열에서 빠집니다.
     */
    suspend fun findMatch(): String {
        try {
            while (true) {
                val gameId = call("matchmake")["gameId"] as? String
                if (gameId != null) return gameId
                delay(POLL_MS)
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { runCatching { call("leaveQueue") } }
            throw e
        }
    }

    fun gameFlow(id: String): Flow<OnlineGame?> = callbackFlow {
        val reg = games.document(id).addSnapshotListener { s, _ ->
            if (s != null) trySend(s.takeIf { it.exists() }?.toGame())
        }
        awaitClose { reg.remove() }
    }

    suspend fun submitMove(gameId: String, move: Move) {
        call("submitMove", mapOf("gameId" to gameId, "move" to move.encode()))
    }

    suspend fun forfeit(gameId: String) {
        runCatching { call("forfeit", mapOf("gameId" to gameId)) }
    }

    /** 상대가 제한 시간을 넘겼으면 승리를 가져옵니다 (시간 판단은 서버가 함). */
    suspend fun claimTimeout(gameId: String) {
        runCatching { call("claimTimeout", mapOf("gameId" to gameId)) }
    }

    /** 계정 삭제: 서버가 진행 중 대전 기권 → 프로필·아이템 삭제 → 로그인 계정 삭제. */
    suspend fun deleteAccount() {
        call("deleteAccount")
        auth.signOut()
    }

    /** 점수가 걸린 AI 대전 시작 (판과 AI의 수는 서버가 처리). */
    suspend fun startAiGame(level: AiLevel): String =
        call("startAiGame", mapOf("level" to level.name))["gameId"] as? String
            ?: throw ServerException(tr("AI 대전을 시작하지 못했어요", "Could not start the AI battle", zh = "无法开始 AI 对战", hi = "AI मुकाबला शुरू नहीं हो सका", es = "No se pudo iniciar la batalla contra la IA", fr = "Impossible de lancer le combat contre l’IA", ar = "تعذّر بدء معركة الذكاء الاصطناعي", de = "KI-Duell konnte nicht gestartet werden", pt = "Não foi possível iniciar a batalha contra a IA", ru = "Не удалось начать бой с ИИ", ja = "AI対戦を開始できませんでした"))

    /** 상점 구매: "POTION" 또는 동물 이름(SHEEP/DOG/GIRAFFE). 점수 차감은 서버가 함. */
    suspend fun buyItem(item: String) {
        call("buyItem", mapOf("item" to item))
    }

    /** 동물 장착 (null이면 기본 캐릭터). */
    suspend fun equipAnimal(animal: Animal?) {
        call("equipAnimal", mapOf("animal" to animal?.name))
    }

    private suspend fun call(name: String, data: Map<String, Any?> = emptyMap()): Map<*, *> {
        try {
            val result = functions.getHttpsCallable(name).call(data).await()
            return result.getData() as? Map<*, *> ?: emptyMap<String, Any?>()
        } catch (e: FirebaseFunctionsException) {
            // 서버는 오류 문구를 한국어(message)와 영어(details.en)로 함께 보냄
            val en = (e.details as? Map<*, *>)?.get("en") as? String
            val msg = if (Lang.isKo || en == null) e.message else en
            throw ServerException(msg ?: tr("서버 오류 (${e.code})", "Server error (${e.code})", zh = "服务器错误（${e.code}）", hi = "सर्वर त्रुटि (${e.code})", es = "Error del servidor (${e.code})", fr = "Erreur serveur (${e.code})", ar = "خطأ في الخادم (${e.code})", de = "Serverfehler (${e.code})", pt = "Erro do servidor (${e.code})", ru = "Ошибка сервера (${e.code})", ja = "サーバーエラー（${e.code}）"))
        }
    }

    private fun DocumentSnapshot.toProfile() = Profile(
        uid = id,
        name = getString("name") ?: tr("익명", "Anonymous", zh = "匿名", hi = "गुमनाम", es = "Anónimo", fr = "Anonyme", ar = "مجهول", de = "Anonym", pt = "Anônimo", ru = "Аноним", ja = "匿名"),
        score = (getLong("score") ?: START_SCORE.toLong()).toInt(),
        wins = (getLong("wins") ?: 0).toInt(),
        losses = (getLong("losses") ?: 0).toInt(),
        potions = (getLong("potions") ?: 0).toInt(),
        owned = (get("owned") as? List<*>)?.mapNotNull { Animal.of(it as? String) }?.toSet() ?: emptySet(),
        equipped = Animal.of(getString("equipped")),
    )

    /** 서버가 보낸 수 정보로 화면 언어의 설명을 만듭니다. 정보가 없으면 서버 문구(한/영). */
    private fun DocumentSnapshot.lastMoveText(): String {
        val move = getString("lastMove")?.let { Move.decode(it) }
        val by = getString("lastBy")?.let { runCatching { Side.valueOf(it) }.getOrNull() }
        if (move != null && by != null) {
            return MoveText.describe(
                MoveInfo(
                    side = by,
                    move = move,
                    power = (getLong("lastPower") ?: 0).toInt(),
                    sum = (getLong("lastSum") ?: 0).toInt(),
                    boom = getBoolean("lastBoom") ?: false,
                    splitsLeft = (getLong("lastLeft") ?: 0).toInt(),
                ),
            )
        }
        val ko = getString("lastDesc") ?: ""
        return if (Lang.isKo) ko else getString("lastDescEn") ?: ko
    }

    private fun DocumentSnapshot.toGame(): OnlineGame {
        fun sets(field: String): List<Int> =
            (get(field) as? List<*>)?.map { (it as Number).toInt() } ?: listOf(1, 1)
        return OnlineGame(
            id = id,
            catUid = getString("catUid") ?: "",
            hamUid = getString("hamUid") ?: "",
            catName = getString("catName") ?: tr("고양이", "Cat", zh = "猫咪", hi = "बिल्ली", es = "Gato", fr = "Chat", ar = "قطة", de = "Katze", pt = "Gato", ru = "Котик", ja = "ネコ"),
            hamName = getString("hamName") ?: tr("햄스터", "Hamster", zh = "仓鼠", hi = "हैम्स्टर", es = "Hámster", fr = "Hamster", ar = "هامستر", de = "Hamster", pt = "Hamster", ru = "Хомяк", ja = "ハムスター"),
            catScore = (getLong("catScore") ?: 0).toInt(),
            hamScore = (getLong("hamScore") ?: 0).toInt(),
            board = Board(
                cat = sets("cat"),
                ham = sets("ham"),
                catSplits = (getLong("catSplits") ?: GameLogic.MAX_SPLITS.toLong()).toInt(),
                hamSplits = (getLong("hamSplits") ?: GameLogic.MAX_SPLITS.toLong()).toInt(),
                catRevives = (getLong("catRevives") ?: 0).toInt(),
                hamRevives = (getLong("hamRevives") ?: 0).toInt(),
            ),
            turn = getString("turn")?.let { Side.valueOf(it) } ?: Side.CAT,
            finished = getString("status") == "finished",
            winner = getString("winner")?.let { Side.valueOf(it) },
            reason = getString("reason") ?: "",
            lastDesc = lastMoveText(),
            explodedSide = getString("explodedSide")?.let { Side.valueOf(it) },
            explodedIndex = (getLong("explodedIndex") ?: -1).toInt(),
            moveNo = (getLong("moveNo") ?: 0).toInt(),
            turnStartedAt = getLong("turnStartedAt") ?: System.currentTimeMillis(),
            catDelta = getLong("catDelta")?.toInt(),
            hamDelta = getLong("hamDelta")?.toInt(),
            catBonus = (getLong("catBonus") ?: 0).toInt(),
            hamBonus = (getLong("hamBonus") ?: 0).toInt(),
            catSkin = Animal.of(getString("catSkin")),
            hamSkin = Animal.of(getString("hamSkin")),
            catTurnMs = getLong("catTurnMs") ?: TURN_LIMIT_MS,
            hamTurnMs = getLong("hamTurnMs") ?: TURN_LIMIT_MS,
            vsAi = getString("vsAi")?.let { v -> AiLevel.entries.firstOrNull { it.name == v } },
        )
    }

    companion object {
        /** functions/src/index.ts 의 region과 같아야 합니다. */
        const val REGION = "asia-northeast3"
        const val START_SCORE = 1000
        const val WIN_POINTS = 25
        const val LOSE_POINTS = 15
        /** 패배 후 보상형 광고를 보면 돌려받는 점수 (functions/src/index.ts의 AD_REFUND_POINTS와 같게) */
        const val AD_REFUND_POINTS = 8
        const val TURN_LIMIT_MS = 30_000L
        const val TIMEOUT_GRACE_MS = 5_000L
        private const val POLL_MS = 2_500L

        private val adjectives = listOf("말랑", "몽글", "졸린", "배고픈", "용감한", "수줍은", "통통", "보송", "새침한", "씩씩한")
        private val animals = listOf("고양이", "햄스터", "냥이", "햄찌", "치즈냥", "골든햄")
        private val adjectivesEn = listOf("Fluffy", "Sleepy", "Hungry", "Brave", "Shy", "Chubby", "Bouncy", "Sassy", "Cozy", "Zippy")
        private val animalsEn = listOf("Cat", "Hammy", "Kitty", "Paws", "Whisker", "Nibbles")

        /** 첫 로그인 닉네임 (화면 언어에 맞춰 한국어/영어) */
        fun randomNickname(): String =
            if (Lang.isKo) adjectives.random() + animals.random() + Random.nextInt(10, 100)
            else adjectivesEn.random() + animalsEn.random() + Random.nextInt(10, 100)
    }
}
