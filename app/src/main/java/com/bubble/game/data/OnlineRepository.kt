package com.bubble.game.data

import android.content.Context
import com.bubble.game.game.Board
import com.bubble.game.game.GameLogic
import com.bubble.game.game.Move
import com.bubble.game.game.Side
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

data class Profile(
    val uid: String,
    val name: String,
    val score: Int,
    val wins: Int,
    val losses: Int,
)

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
) {
    fun sideOf(uid: String): Side? = when (uid) {
        catUid -> Side.CAT
        hamUid -> Side.HAMSTER
        else -> null
    }
}

/**
 * Firebase(익명 로그인 + Firestore)로 매칭, 대전, 점수를 처리합니다.
 *
 * Firestore 구조
 * - users/{uid}   : name, score, wins, losses
 * - queue/{uid}   : 매칭 대기열 (status = waiting / matched, gameId)
 * - games/{id}    : 대전 상태 (판, 차례, 승자 등)
 */
class OnlineRepository(context: Context) {

    val available: Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val users get() = db.collection("users")
    private val queue get() = db.collection("queue")
    private val games get() = db.collection("games")

    /** 구글로 로그인된 사용자 uid. 로그인 전(또는 예전 익명 계정)이면 null. */
    val currentUid: String?
        get() = auth.currentUser?.takeUnless { it.isAnonymous }?.uid

    suspend fun signInWithGoogle(idToken: String): String {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return auth.signInWithCredential(credential).await().user!!.uid
    }

    fun signOut() = auth.signOut()

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
     * 랜덤 매칭. 상대를 찾을 때까지 기다렸다가 게임 ID를 돌려줍니다.
     * 코루틴이 취소되면(매칭 취소) 대기열에서 빠집니다.
     *
     * 동시에 두 사람이 서로를 잡는 일이 없도록, 늦게 들어온 사람이
     * 먼저 기다리던 사람을 트랜잭션으로 잡습니다.
     */
    suspend fun findMatch(me: Profile): String {
        val myRef = queue.document(me.uid)
        val createdAt = System.currentTimeMillis()
        myRef.set(
            mapOf(
                "uid" to me.uid,
                "name" to me.name,
                "score" to me.score,
                "status" to STATUS_WAITING,
                "createdAt" to createdAt,
                "heartbeat" to createdAt,
                "gameId" to null,
            ),
        ).await()

        val matched = CompletableDeferred<String>()
        val reg = myRef.addSnapshotListener { s, _ ->
            s?.getString("gameId")?.let { matched.complete(it) }
        }
        try {
            while (!matched.isCompleted) {
                runCatching { myRef.update("heartbeat", System.currentTimeMillis()).await() }
                tryClaim(me, createdAt)?.let { matched.complete(it) }
                withTimeoutOrNull(2_500) { matched.await() }
            }
            val gameId = matched.await()
            runCatching { myRef.delete().await() }
            return gameId
        } catch (e: CancellationException) {
            withContext(NonCancellable) { leaveQueue(me.uid) }
            throw e
        } finally {
            reg.remove()
        }
    }

    private suspend fun tryClaim(me: Profile, myCreatedAt: Long): String? {
        val now = System.currentTimeMillis()
        val candidates = queue.whereEqualTo("status", STATUS_WAITING).limit(30).get().await()
            .documents
            .filter { it.id != me.uid }
            .filter { now - (it.getLong("heartbeat") ?: 0L) < STALE_MS }
            .filter {
                // 나보다 먼저 들어온 사람만 잡는다 (동시 접속 시 같은 시간이면 uid로 결정)
                val c = it.getLong("createdAt") ?: Long.MAX_VALUE
                c < myCreatedAt || (c == myCreatedAt && it.id < me.uid)
            }
            .sortedBy { it.getLong("createdAt") }

        val myRef = queue.document(me.uid)
        for (cand in candidates) {
            val gameRef = games.document()
            val ok = runCatching {
                db.runTransaction { tx ->
                    val mine = tx.get(myRef)
                    val theirs = tx.get(cand.reference)
                    if (mine.getString("status") != STATUS_WAITING ||
                        theirs.getString("status") != STATUS_WAITING
                    ) {
                        return@runTransaction false
                    }
                    val theirName = theirs.getString("name") ?: "익명"
                    val theirScore = (theirs.getLong("score") ?: START_SCORE.toLong()).toInt()
                    val iAmCat = Random.nextBoolean()
                    tx.set(
                        gameRef,
                        mapOf(
                            "catUid" to if (iAmCat) me.uid else cand.id,
                            "hamUid" to if (iAmCat) cand.id else me.uid,
                            "catName" to if (iAmCat) me.name else theirName,
                            "hamName" to if (iAmCat) theirName else me.name,
                            "catScore" to if (iAmCat) me.score else theirScore,
                            "hamScore" to if (iAmCat) theirScore else me.score,
                            "cat" to listOf(1, 1),
                            "ham" to listOf(1, 1),
                            "turn" to Side.CAT.name,
                            "status" to STATUS_PLAYING,
                            "winner" to null,
                            "reason" to "",
                            "lastDesc" to "",
                            "explodedSide" to null,
                            "explodedIndex" to -1,
                            "moveNo" to 0,
                            "turnStartedAt" to System.currentTimeMillis(),
                            "scored" to emptyList<String>(),
                        ),
                    )
                    val matched = mapOf("status" to STATUS_MATCHED, "gameId" to gameRef.id)
                    tx.update(myRef, matched)
                    tx.update(cand.reference, matched)
                    true
                }.await()
            }.getOrDefault(false)
            if (ok) return gameRef.id
        }
        return null
    }

    private suspend fun leaveQueue(uid: String) {
        val ref = queue.document(uid)
        runCatching {
            db.runTransaction { tx ->
                val s = tx.get(ref)
                if (s.exists() && s.getString("status") == STATUS_WAITING) tx.delete(ref)
            }.await()
        }
    }

    fun gameFlow(id: String): Flow<OnlineGame?> = callbackFlow {
        val reg = games.document(id).addSnapshotListener { s, _ ->
            if (s != null) trySend(s.takeIf { it.exists() }?.toGame())
        }
        awaitClose { reg.remove() }
    }

    /** 내 차례에 수를 둡니다. 판 검증은 트랜잭션 안에서 다시 합니다. */
    suspend fun submitMove(gameId: String, uid: String, move: Move) {
        val ref = games.document(gameId)
        db.runTransaction { tx ->
            val g = tx.get(ref).toGame()
            val side = g.sideOf(uid) ?: return@runTransaction
            if (g.finished || g.turn != side) return@runTransaction
            if (!GameLogic.isLegal(g.board, side, move)) return@runTransaction
            val r = GameLogic.apply(g.board, side, move)
            val winner = r.board.winner()
            val update = mutableMapOf<String, Any?>(
                "cat" to r.board.cat,
                "ham" to r.board.ham,
                "turn" to side.opponent.name,
                "lastDesc" to r.description,
                "explodedSide" to r.exploded?.first?.name,
                "explodedIndex" to (r.exploded?.second ?: -1),
                "moveNo" to g.moveNo + 1,
                "turnStartedAt" to System.currentTimeMillis(),
            )
            if (winner != null) {
                update["status"] = STATUS_FINISHED
                update["winner"] = winner.name
                update["reason"] = "normal"
            }
            tx.update(ref, update)
        }.await()
    }

    suspend fun forfeit(gameId: String, uid: String) {
        finishFor(gameId, uid, "forfeit") { g, side -> side.opponent }
    }

    /** 상대가 제한 시간을 넘기면 내가 승리를 가져옵니다. */
    suspend fun claimTimeout(gameId: String, uid: String) {
        finishFor(gameId, uid, "timeout") { g, side ->
            val late = System.currentTimeMillis() - g.turnStartedAt > TURN_LIMIT_MS + TIMEOUT_GRACE_MS
            if (g.turn == side.opponent && late) side else null
        }
    }

    private suspend fun finishFor(
        gameId: String,
        uid: String,
        reason: String,
        decideWinner: (OnlineGame, Side) -> Side?,
    ) {
        val ref = games.document(gameId)
        runCatching {
            db.runTransaction { tx ->
                val g = tx.get(ref).toGame()
                val side = g.sideOf(uid) ?: return@runTransaction
                if (g.finished) return@runTransaction
                val winner = decideWinner(g, side) ?: return@runTransaction
                tx.update(
                    ref,
                    mapOf("status" to STATUS_FINISHED, "winner" to winner.name, "reason" to reason),
                )
            }.await()
        }
    }

    /** 끝난 게임의 점수를 내 프로필에 한 번만 반영하고, 바뀐 점수를 돌려줍니다. */
    suspend fun settleScore(gameId: String, uid: String): Int? {
        val gameRef = games.document(gameId)
        val userRef = users.document(uid)
        return db.runTransaction { tx ->
            val gs = tx.get(gameRef)
            val us = tx.get(userRef)
            val g = gs.toGame()
            val side = g.sideOf(uid) ?: return@runTransaction null
            if (!g.finished || g.winner == null) return@runTransaction null
            @Suppress("UNCHECKED_CAST")
            val scored = gs.get("scored") as? List<String> ?: emptyList()
            val won = g.winner == side
            val delta = if (won) WIN_POINTS else -LOSE_POINTS
            if (uid in scored) return@runTransaction delta
            val score = (us.getLong("score") ?: START_SCORE.toLong()).toInt()
            tx.update(
                userRef,
                mapOf(
                    "score" to maxOf(0, score + delta),
                    if (won) "wins" to FieldValue.increment(1) else "losses" to FieldValue.increment(1),
                ),
            )
            tx.update(gameRef, "scored", FieldValue.arrayUnion(uid))
            delta
        }.await()
    }

    private fun DocumentSnapshot.toProfile() = Profile(
        uid = id,
        name = getString("name") ?: "익명",
        score = (getLong("score") ?: START_SCORE.toLong()).toInt(),
        wins = (getLong("wins") ?: 0).toInt(),
        losses = (getLong("losses") ?: 0).toInt(),
    )

    private fun DocumentSnapshot.toGame(): OnlineGame {
        fun sets(field: String): List<Int> =
            (get(field) as? List<*>)?.map { (it as Number).toInt() } ?: listOf(1, 1)
        return OnlineGame(
            id = id,
            catUid = getString("catUid") ?: "",
            hamUid = getString("hamUid") ?: "",
            catName = getString("catName") ?: "고양이",
            hamName = getString("hamName") ?: "햄스터",
            catScore = (getLong("catScore") ?: 0).toInt(),
            hamScore = (getLong("hamScore") ?: 0).toInt(),
            board = Board(cat = sets("cat"), ham = sets("ham")),
            turn = getString("turn")?.let { Side.valueOf(it) } ?: Side.CAT,
            finished = getString("status") == STATUS_FINISHED,
            winner = getString("winner")?.let { Side.valueOf(it) },
            reason = getString("reason") ?: "",
            lastDesc = getString("lastDesc") ?: "",
            explodedSide = getString("explodedSide")?.let { Side.valueOf(it) },
            explodedIndex = (getLong("explodedIndex") ?: -1).toInt(),
            moveNo = (getLong("moveNo") ?: 0).toInt(),
            turnStartedAt = getLong("turnStartedAt") ?: System.currentTimeMillis(),
        )
    }

    companion object {
        const val START_SCORE = 1000
        const val WIN_POINTS = 25
        const val LOSE_POINTS = 15
        const val TURN_LIMIT_MS = 30_000L
        const val TIMEOUT_GRACE_MS = 5_000L
        private const val STALE_MS = 15_000L
        private const val STATUS_WAITING = "waiting"
        private const val STATUS_MATCHED = "matched"
        private const val STATUS_PLAYING = "playing"
        private const val STATUS_FINISHED = "finished"

        private val adjectives = listOf("말랑", "몽글", "졸린", "배고픈", "용감한", "수줍은", "통통", "보송", "새침한", "씩씩한")
        private val animals = listOf("고양이", "햄스터", "냥이", "햄찌", "치즈냥", "골든햄")

        fun randomNickname(): String =
            adjectives.random() + animals.random() + Random.nextInt(10, 100)
    }
}
