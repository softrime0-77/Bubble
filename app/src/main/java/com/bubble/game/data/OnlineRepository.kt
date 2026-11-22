package com.bubble.game.data

import com.bubble.game.i18n.Lang
import com.bubble.game.i18n.tr

import android.content.Context
import com.bubble.game.game.AiLevel
import com.bubble.game.billing.BillingManager
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
    /** 동물별 남은 개수 (소모품: 랜덤 매칭 1판에 1개) */
    val animals: Map<Animal, Int> = emptyMap(),
    /** 장착한 동물 (null이면 기본 고양이/햄스터) */
    val equipped: Animal? = null,
    /** 내 추천 코드 (처음 친구 초대를 열 때 서버가 만듦) */
    val refCode: String? = null,
    /** 다른 사람의 추천 코드를 이미 입력했는지 */
    val referred: Boolean = false,
    /** 내가 초대한 친구 수 */
    val referrals: Int = 0,
    /** 관리자 계정 (동시 접속자 수가 보임) */
    val admin: Boolean = false,
    /** 상점 포인트 (결제로 충전, 상점 전용 — 점수·계급과 별개) */
    val coins: Int = 0,
    /** ×3 부스터 남은 개수와 켜짐 여부 */
    val boosts: Int = 0,
    val boostOn: Boolean = false,
    /** 나라 (ISO 2글자, 랭킹 국기용. 모르면 null) */
    val country: String? = null,
    /** 산 유료 스킨들 */
    val skins: Set<Animal> = emptySet(),
    /** 광고 제거 상품을 샀는지 (전면 광고 안 나옴) */
    val noAds: Boolean = false,
    /** 첫 포인트 구매를 했는지 (안 했으면 첫 구매 2배) */
    val firstBuyDone: Boolean = false,
    /** 가입 시각 (ms). 첫 구매 2배는 가입 후 7일 동안만 */
    val createdAt: Long? = null,
    /** 💰 복냥이로 오늘 받은 포인트 (luckyDay가 오늘일 때만 유효) */
    val luckyDay: String? = null,
    val luckyCoins: Int = 0,
    /** 🐉 아기 용으로 오늘 받은 포인트 (dragonDay가 오늘일 때만 유효) */
    val dragonDay: String? = null,
    val dragonCoins: Int = 0,
    /** 🐰 연승 토끼로 오늘 받은 포인트 (rabbitDay가 오늘일 때만 유효) */
    val rabbitDay: String? = null,
    val rabbitCoins: Int = 0,
    /** 🐰 연승 (랜덤 매칭·점수 걸린 친구 대전) */
    val winStreak: Int = 0,
    /** ✏️ 닉네임: 첫 무료 변경을 썼는지, 가진 변경권 수 */
    val freeRenameUsed: Boolean = false,
    val renameTickets: Int = 0,
    /** 출석: 마지막으로 받은 날(한국 시간)과 연속 일수 */
    val checkinDay: String? = null,
    val checkinStreak: Int = 0,
    /** 일일 미션 진행도 (missionDay가 오늘일 때만 유효) */
    val missionDay: String? = null,
    val mPlay: Int = 0,
    val mWin: Int = 0,
    val mRandom: Int = 0,
    val mClaimed: Set<String> = emptySet(),
    /** 광고로 받은 부활물약 (오늘 몇 번) */
    val potionAdDay: String? = null,
    val potionAds: Int = 0,
    /** 주간 랭킹 보상 (아직 안 봤으면 팝업) */
    val weeklyReward: WeeklyReward? = null,
) {
    val rank: Rank get() = Rank.of(score)

    /** 남아 있는 동물들 */
    val owned: Set<Animal> get() = animals.filterValues { it > 0 }.keys

    fun animalCount(a: Animal): Int = animals[a] ?: 0
}

/** 주간 랭킹 보상 안내 */
data class WeeklyReward(val week: String, val rank: Int, val coins: Int, val seen: Boolean)

/** 랭킹 한 줄 (내 주변 순위) */
data class RankRow(
    val uid: String, val rank: Int, val name: String, val score: Int, val country: String?,
    val wins: Int? = null, val losses: Int? = null,
    /** 지금 보이는 캐릭터 (null이면 기본 고양이) */
    val look: Animal? = null,
)

/** 친구 대전방 상태: 친구가 들어왔으면 gameId, 방이 끝났으면 expired */
data class FriendRoomState(val gameId: String?, val expired: Boolean)

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
    /** 각 플레이어의 나라 (ISO 2글자, 모르면 null) */
    val catCountry: String?,
    val hamCountry: String?,
    /** 이번 판에 ×3 부스터를 쓴 사람 */
    val catBoost: Boolean,
    val hamBoost: Boolean,
    /** 마지막 수와 둔 쪽 (방울이 날아가는 효과용) */
    val lastMove: Move? = null,
    val lastBy: Side? = null,
    /** 마지막 공격에서 💰 복냥이 효과가 터졌는지 */
    val lastLucky: Boolean = false,
    /** 이번 수로 받은 💰 (복냥이 1 · 아기 용 2) */
    val lastCoins: Int = 0,
    /** 🐰 시작할 때의 연승 리본 수 */
    val catStreak: Int = 0,
    val hamStreak: Int = 0,
    /** 🐰 이긴 쪽이 이 판으로 된 연승과 받은 💰 (연승 토끼일 때만) */
    val rabbitStreak: Int = 0,
    val rabbitCoins: Int = 0,
    /** 친구 대전이면 true, 점수가 걸린 판이면 rated */
    val vsFriend: Boolean = false,
    val rated: Boolean = false,
) {
    fun boostOf(side: Side): Boolean = if (side == Side.CAT) catBoost else hamBoost

    fun countryOf(side: Side): String? = if (side == Side.CAT) catCountry else hamCountry

    fun scoreOf(side: Side): Int = if (side == Side.CAT) catScore else hamScore

    fun skinOf(side: Side): Animal? = if (side == Side.CAT) catSkin else hamSkin
    fun ribbonsOf(side: Side): Int = if (side == Side.CAT) catStreak else hamStreak

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

    /**
     * 첫 로그인이면 서버가 프로필을 만듭니다 (보통 1000점 · 탈퇴 후 재가입 0점 · 관리자 5000점).
     * 새로 만들었으면 종류("new" / "returning" / "admin")와 시작 점수, 이미 있으면 null.
     */
    suspend fun ensureProfile(country: String?): Pair<String, Int>? {
        val r = call("ensureProfile", mapOf("name" to randomNickname(), "country" to country))
        val kind = r["kind"] as? String ?: return null
        return kind to ((r["score"] as? Number)?.toInt() ?: START_SCORE)
    }

    /** 접속 중 신호 (관리자 화면의 동시 접속자 수에 쓰임) */
    suspend fun pingPresence(uid: String) {
        db.collection("presence").document(uid).set(mapOf("at" to System.currentTimeMillis())).await()
    }

    /** 동시 접속자 수 (관리자만) */
    suspend fun onlineCount(): Int =
        (call("onlineCount")["online"] as? Number)?.toInt() ?: 0

    /** 🛠 관리자: 닉네임(정확히)이나 uid로 찾기. 빈 칸이면 나 */
    suspend fun adminFind(query: String): List<AdminUser> {
        val list = call("adminFindUser", mapOf("query" to query.trim()))["users"] as? List<*> ?: return emptyList()
        return list.mapNotNull { m ->
            (m as? Map<*, *>)?.let {
                AdminUser(
                    uid = it["uid"] as? String ?: return@let null,
                    name = it["name"] as? String ?: "",
                    score = (it["score"] as? Number)?.toInt() ?: 0,
                    coins = (it["coins"] as? Number)?.toInt() ?: 0,
                    potions = (it["potions"] as? Number)?.toInt() ?: 0,
                )
            }
        }
    }

    /** 🛠 관리자: 점수 · 💰 · 💖 를 넣은 값으로 바꿈 */
    suspend fun adminSet(uid: String, score: Int, coins: Int, potions: Int) {
        call("adminSetPoints", mapOf("uid" to uid, "score" to score, "coins" to coins, "potions" to potions))
    }

    /** 닉네임 바꾸기 (서버): 첫 변경 무료, 그다음은 변경권 1장. 돌려주는 값: "free" / "ticket" / "same" */
    suspend fun rename(name: String): String =
        (call("rename", mapOf("name" to name.trim().take(12)))["used"] as? String) ?: "free"

    fun profileFlow(uid: String): Flow<Profile?> = callbackFlow {
        val reg = users.document(uid).addSnapshotListener { s, _ ->
            trySend(s?.takeIf { it.exists() }?.toProfile())
        }
        awaitClose { reg.remove() }
    }

    /**
     * 랭킹 TOP 100 한 번 불러오기. 실시간 구독은 상위권 점수가 바뀔 때마다 보고 있는 모두에게
     * 다시 전송돼 사용자가 많아지면 읽기 비용이 크게 늘어서, 로비에서 1분마다 새로고침합니다.
     */
    suspend fun leaderboard(): List<Profile> =
        users.orderBy("score", Query.Direction.DESCENDING).limit(LEADERBOARD_SIZE.toLong())
            .get().await().documents.map { it.toProfile() }

    /** 내 순위와 내 위아래 10명 (TOP 100 밖일 때 보여줌) */
    suspend fun myRanking(): Pair<Int, List<RankRow>> {
        val r = call("myRanking")
        val rows = (r["rows"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.toRankRow() }
        return ((r["rank"] as? Number)?.toInt() ?: 0) to rows
    }

    private fun Map<*, *>.toRankRow(): RankRow? {
        val uid = this["uid"] as? String ?: return null
        val rank = (this["rank"] as? Number)?.toInt() ?: return null
        return RankRow(
            uid = uid,
            rank = rank,
            name = this["name"] as? String ?: "Player",
            score = (this["score"] as? Number)?.toInt() ?: START_SCORE,
            country = this["country"] as? String,
            wins = (this["wins"] as? Number)?.toInt(),
            losses = (this["losses"] as? Number)?.toInt(),
            look = Animal.of(this["look"] as? String),
        )
    }

    // ---------- 친구 대전 ----------

    /** 친구 대전방 만들기 → 대전 코드 */
    suspend fun createFriendRoom(country: String?): String =
        call("createFriendRoom", mapOf("country" to country))["code"] as? String ?: throw ServerException("no code")

    suspend fun pollFriendRoom(code: String): FriendRoomState {
        val r = call("pollFriendRoom", mapOf("code" to code))
        return FriendRoomState(r["gameId"] as? String, r["expired"] == true)
    }

    suspend fun cancelFriendRoom(code: String) {
        runCatching { call("cancelFriendRoom", mapOf("code" to code)) }
    }

    /** 친구의 대전 링크로 들어가기 → gameId */
    suspend fun joinFriendRoom(code: String, country: String?): String =
        call("joinFriendRoom", mapOf("code" to code, "country" to country))["gameId"] as? String ?: throw ServerException("no game")

    /**
     * 랜덤 매칭. 상대를 찾을 때까지 서버에 주기적으로 물어보고, 게임 ID를 돌려줍니다.
     * [timeoutMs] 안에 상대가 없으면 대기열에서 빠지고 null (→ 앱이 AI 대전으로 넘어감).
     * 코루틴이 취소되면(매칭 취소) 대기열에서 빠집니다.
     */
    suspend fun findMatch(country: String?, timeoutMs: Long = MATCH_TIMEOUT_MS): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        val data = mapOf("country" to country)
        try {
            while (System.currentTimeMillis() < deadline) {
                val gameId = call("matchmake", data)["gameId"] as? String
                if (gameId != null) return gameId
                delay(POLL_MS)
            }
            // 시간이 다 됐으면 마지막으로 한 번 더 확인 (그사이 누가 나를 잡았을 수 있음)
            (call("matchmake", data)["gameId"] as? String)?.let { return it }
            call("leaveQueue")
            return null
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

    /** 내 추천 코드 (없으면 서버가 새로 만듦) */
    suspend fun referralCode(): String =
        call("getReferralCode")["code"] as? String ?: throw ServerException("no code")

    /** 친구의 추천 코드 입력 (초대한 친구와 나 둘 다 +30점) */
    suspend fun redeemReferral(code: String) {
        call("redeemReferral", mapOf("code" to code))
    }

    /** 인앱 결제 영수증을 서버가 확인하고 점수를 지급. 지급(또는 이미 지급됨)이면 true. */
    /** 영수증 확인 → (처리됐는지, 새로 받은 포인트). 광고 제거는 포인트 0으로 처리됨 */
    suspend fun verifyPurchase(productId: String, token: String): Pair<Boolean, Int> {
        val r = call("verifyPurchase", mapOf("productId" to productId, "token" to token))
        val granted = (r["granted"] as? Number)?.toInt() ?: 0
        // 광고 제거는 포인트가 없어도 처리 완료 (granted 0)
        val ok = granted > 0 || r["already"] == true || productId == BillingManager.REMOVE_ADS
        return ok to granted
    }

    /** 계정 삭제: 서버가 진행 중 대전 기권 → 프로필·아이템 삭제 → 로그인 계정 삭제. */
    suspend fun deleteAccount() {
        call("deleteAccount")
        auth.signOut()
    }

    /** 점수가 걸린 AI 대전 시작 (판과 AI의 수는 서버가 처리). */
    suspend fun startAiGame(level: AiLevel, country: String?): String =
        call("startAiGame", mapOf("level" to level.name, "country" to country))["gameId"] as? String
            ?: throw ServerException(tr("AI 대전을 시작하지 못했어요", "Could not start the AI battle", zh = "无法开始 AI 对战", hi = "AI मुकाबला शुरू नहीं हो सका", es = "No se pudo iniciar la batalla contra la IA", fr = "Impossible de lancer le combat contre l’IA", ar = "تعذّر بدء معركة الذكاء الاصطناعي", de = "KI-Duell konnte nicht gestartet werden", pt = "Não foi possível iniciar a batalha contra a IA", ru = "Не удалось начать бой с ИИ", ja = "AI対戦を開始できませんでした"))

    /** 오늘 출석 보상 받기 → (며칠째, 받은 💰, 물약 받았는지) */
    /** 출석 보상 → (며칠째, 💰, 💖 물약, 🐼 판다 보너스) */
    suspend fun claimDaily(): CheckinResult {
        val r = call("claimDaily")
        return CheckinResult(
            (r["day"] as? Number)?.toInt() ?: 1, (r["coins"] as? Number)?.toInt() ?: 0, r["potion"] == true,
            (r["panda"] as? Number)?.toInt() ?: 0,
        )
    }

    /** 일일 미션 보상 받기 → 받은 💰 */
    suspend fun claimMission(id: String): Int =
        (call("claimMission", mapOf("id" to id))["coins"] as? Number)?.toInt() ?: 0

    suspend fun ackWeeklyReward() {
        runCatching { call("ackWeeklyReward") }
    }

    /** 상점 구매: "POTION" 또는 동물 이름(SHEEP/DOG/GIRAFFE). 점수 차감은 서버가 함. */
    suspend fun buyItem(item: String, qty: Int = 1) {
        call("buyItem", mapOf("item" to item, "qty" to qty))
    }

    /** ×3 부스터 켜기/끄기 */
    suspend fun setBoost(on: Boolean) {
        call("setBoost", mapOf("on" to on))
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
        animals = (get("animals") as? Map<*, *>)
            ?.mapNotNull { (k, v) -> Animal.of(k as? String)?.let { a -> a to ((v as? Number)?.toInt() ?: 0) } }
            ?.toMap() ?: emptyMap(),
        equipped = Animal.of(getString("equipped")),
        refCode = getString("refCode"),
        referred = getString("referredBy") != null,
        referrals = (getLong("referrals") ?: 0).toInt(),
        admin = getBoolean("admin") ?: false,
        coins = (getLong("coins") ?: 0).toInt(),
        boosts = (getLong("boosts") ?: 0).toInt(),
        boostOn = getBoolean("boostOn") ?: false,
        country = getString("country"),
        skins = (get("skins") as? List<*>).orEmpty().mapNotNull { Animal.of(it as? String) }.toSet(),
        noAds = getBoolean("noAds") ?: false,
        firstBuyDone = getBoolean("firstBuyDone") ?: false,
        createdAt = getLong("createdAt"),
        luckyDay = getString("luckyDay"),
        luckyCoins = (getLong("luckyCoins") ?: 0).toInt(),
        dragonDay = getString("dragonDay"),
        dragonCoins = (getLong("dragonCoins") ?: 0).toInt(),
        rabbitDay = getString("rabbitDay"),
        rabbitCoins = (getLong("rabbitCoins") ?: 0).toInt(),
        winStreak = (getLong("winStreak") ?: 0).toInt(),
        freeRenameUsed = getBoolean("freeRenameUsed") ?: false,
        renameTickets = (getLong("renameTickets") ?: 0).toInt(),
        checkinDay = getString("checkinDay"),
        checkinStreak = (getLong("checkinStreak") ?: 0).toInt(),
        missionDay = getString("missionDay"),
        mPlay = (getLong("mPlay") ?: 0).toInt(),
        mWin = (getLong("mWin") ?: 0).toInt(),
        mRandom = (getLong("mRandom") ?: 0).toInt(),
        mClaimed = (get("mClaimed") as? List<*>).orEmpty().mapNotNull { it as? String }.toSet(),
        potionAdDay = getString("potionAdDay"),
        potionAds = (getLong("potionAds") ?: 0).toInt(),
        weeklyReward = (get("weeklyReward") as? Map<*, *>)?.let { w ->
            WeeklyReward(
                week = w["week"] as? String ?: "",
                rank = (w["rank"] as? Number)?.toInt() ?: 0,
                coins = (w["coins"] as? Number)?.toInt() ?: 0,
                seen = w["seen"] == true,
            )
        },
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
            lastMove = getString("lastMove")?.let { Move.decode(it) },
            lastBy = getString("lastBy")?.let { runCatching { Side.valueOf(it) }.getOrNull() },
            lastLucky = getBoolean("lastLucky") ?: false,
            lastCoins = (getLong("lastCoins") ?: 0).toInt(),
            catStreak = (getLong("catStreak") ?: 0).toInt(),
            hamStreak = (getLong("hamStreak") ?: 0).toInt(),
            rabbitStreak = (getLong("rabbitStreak") ?: 0).toInt(),
            rabbitCoins = (getLong("rabbitCoins") ?: 0).toInt(),
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
            catCountry = getString("catCountry"),
            hamCountry = getString("hamCountry"),
            catBoost = getBoolean("catBoost") ?: false,
            hamBoost = getBoolean("hamBoost") ?: false,
            vsFriend = getBoolean("vsFriend") ?: false,
            rated = getBoolean("rated") ?: false,
        )
    }

    companion object {
        /** functions/src/index.ts 의 region과 같아야 합니다. */
        const val REGION = "asia-northeast3"
        const val START_SCORE = 1000
        const val WIN_POINTS = 30
        const val LOSE_POINTS = 10
        /** 패배 후 보상형 광고를 보면 돌려받는 점수 (functions/src/index.ts의 AD_REFUND_POINTS와 같게) */
        const val AD_REFUND_POINTS = 8
        const val TURN_LIMIT_MS = 20_000L
        /** 서버의 시간 판정 여유(2초)보다 조금 길게 */
        const val TIMEOUT_GRACE_MS = 2_500L
        private const val POLL_MS = 2_500L
        /** 이 시간 안에 사람 상대가 없으면 AI(어려움)와 대결 */
        const val MATCH_TIMEOUT_MS = 30_000L
        /** 랭킹 목록에 보여줄 인원 */
        const val LEADERBOARD_SIZE = 100
        /** 친구 대전 점수 (functions/src/friend.ts와 같게) */
        const val FRIEND_WIN_POINTS = 30
        const val FRIEND_LOSE_POINTS = 10
        const val FRIEND_RATED_PER_DAY = 5
        /** 친구를 기다리는 최대 시간 (서버 FRIEND_ROOM_TTL_MS와 같게) */
        const val FRIEND_WAIT_MS = 10 * 60_000L
        /** 출석 7일 보상 💰 (functions/src/rewards.ts CHECKIN_COINS와 같게). 7일째는 💖도 */
        val CHECKIN_COINS = listOf(20, 20, 30, 30, 50, 50, 100)
        /** 첫 구매 2배 기간: 가입 후 7일 (functions/src/catalog.ts FIRST_BUY_WINDOW_MS와 같게) */
        const val FIRST_BUY_WINDOW_MS = 7 * 86_400_000L

        /** 첫 구매 2배가 끝나기까지 남은 시간 (ms). 이미 샀거나 기간이 지났으면 null */
        fun firstBuyLeftMs(p: Profile?, now: Long = System.currentTimeMillis()): Long? {
            if (p == null || p.firstBuyDone) return null
            val joined = p.createdAt ?: return null
            return (joined + FIRST_BUY_WINDOW_MS - now).takeIf { it > 0 }
        }
        /** 광고 보고 물약: 하루 최대 */
        const val POTION_ADS_PER_DAY = 3

        /** 한국 시간 오늘 (서버 dayKey와 같게) */
        fun today(now: Long = System.currentTimeMillis()): String =
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Seoul") }
                .format(java.util.Date(now))

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

/** 출석 보상 결과 */
data class CheckinResult(val day: Int, val coins: Int, val potion: Boolean, val panda: Int)

/** 🛠 관리자 화면에서 고친 사용자 */
data class AdminUser(val uid: String, val name: String, val score: Int, val coins: Int, val potions: Int)
