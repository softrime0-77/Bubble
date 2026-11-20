package com.bubble.game.ui

import android.app.Application
import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bubble.game.data.GoogleSignIn
import com.bubble.game.data.OnlineGame
import com.bubble.game.data.OnlineRepository
import com.bubble.game.data.Profile
import com.bubble.game.game.Ai
import com.bubble.game.game.AiLevel
import com.bubble.game.game.Board
import com.bubble.game.game.GameLogic
import com.bubble.game.game.Move
import com.bubble.game.game.Side
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed interface Screen {
    data object Lobby : Screen
    data class Matching(val startedAt: Long) : Screen
    data object Game : Screen
}

/** 터짐 애니메이션을 한 번만 틀기 위한 표식. */
data class Explosion(val side: Side, val index: Int, val id: Int)

data class GameUi(
    val online: Boolean,
    val board: Board = Board(),
    val mySide: Side = Side.CAT,
    val turn: Side = Side.CAT,
    val myName: String = "나",
    val opponentName: String = "상대",
    val opponentScore: Int? = null,
    val finished: Boolean = false,
    val winner: Side? = null,
    val reason: String = "",
    val log: List<String> = emptyList(),
    val explosion: Explosion? = null,
    val turnDeadline: Long? = null,
    val scoreDelta: Int? = null,
    /** 서버가 수를 거절했을 때 등 잠깐 보여줄 안내 문구. */
    val notice: String? = null,
    val busy: Boolean = false,
) {
    val myTurn: Boolean get() = !finished && !busy && turn == mySide
}

data class LobbyUi(
    val loading: Boolean = true,
    val onlineAvailable: Boolean = false,
    val needsLogin: Boolean = false,
    val error: String? = null,
    val profile: Profile? = null,
    val leaderboard: List<Profile> = emptyList(),
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = OnlineRepository(app)

    private val _screen = MutableStateFlow<Screen>(Screen.Lobby)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _lobby = MutableStateFlow(LobbyUi(onlineAvailable = repo.available))
    val lobby: StateFlow<LobbyUi> = _lobby.asStateFlow()

    private val _game = MutableStateFlow(GameUi(online = false))
    val game: StateFlow<GameUi> = _game.asStateFlow()

    private var uid: String? = null
    private var matchJob: Job? = null
    private var gameJob: Job? = null
    private var onlineGameId: String? = null

    private var sessionJob: Job? = null

    init {
        val id = if (repo.available) repo.currentUid else null
        when {
            !repo.available -> _lobby.update { it.copy(loading = false) }
            id == null -> _lobby.update { it.copy(loading = false, needsLogin = true) }
            else -> startSession(id)
        }
    }

    /** 구글 계정으로 로그인. [activityContext]는 계정 선택 창을 띄울 Activity. */
    fun signInWithGoogle(activityContext: Context) {
        if (_lobby.value.loading) return
        val clientId = GoogleSignIn.webClientId(getApplication())
        if (clientId == null) {
            _lobby.update {
                it.copy(error = "Firebase 콘솔에서 Google 로그인을 켜고 google-services.json을 다시 받아 넣어야 해요.")
            }
            return
        }
        _lobby.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val token = GoogleSignIn.requestIdToken(activityContext, clientId)
                startSession(repo.signInWithGoogle(token))
            } catch (e: GetCredentialCancellationException) {
                _lobby.update { it.copy(loading = false) }
            } catch (e: Exception) {
                _lobby.update { it.copy(loading = false, error = "구글 로그인에 실패했어요: ${e.message}") }
            }
        }
    }

    fun signOut() {
        sessionJob?.cancel()
        sessionJob = null
        uid = null
        repo.signOut()
        _lobby.value = LobbyUi(onlineAvailable = true, loading = false, needsLogin = true)
    }

    /** 로그인된 상태에서 프로필/랭킹을 다시 불러옵니다. */
    fun connect() {
        uid?.let { startSession(it) }
    }

    private fun startSession(id: String) {
        uid = id
        sessionJob?.cancel()
        _lobby.update { it.copy(loading = true, needsLogin = false, error = null) }
        sessionJob = viewModelScope.launch {
            try {
                repo.ensureProfile(id)
                launch { repo.profileFlow(id).collect { p -> _lobby.update { it.copy(profile = p, loading = false) } } }
                launch { repo.leaderboardFlow().collect { l -> _lobby.update { it.copy(leaderboard = l) } } }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _lobby.update { it.copy(loading = false, error = "서버에 연결하지 못했어요: ${e.message}") }
            }
        }
    }

    fun rename(name: String) {
        val id = uid ?: return
        if (name.isBlank()) return
        viewModelScope.launch { runCatching { repo.setNickname(id, name) } }
    }

    // ---------- 랜덤 매칭 ----------

    fun startMatching() {
        if (_lobby.value.profile == null) return
        if (matchJob?.isActive == true) return
        _screen.value = Screen.Matching(System.currentTimeMillis())
        matchJob = viewModelScope.launch {
            try {
                val gameId = repo.findMatch()
                startOnlineGame(gameId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _lobby.update { it.copy(error = "매칭 중 문제가 생겼어요: ${e.message}") }
                _screen.value = Screen.Lobby
            }
        }
    }

    fun cancelMatching() {
        matchJob?.cancel()
        matchJob = null
        _screen.value = Screen.Lobby
    }

    private fun startOnlineGame(gameId: String) {
        val me = uid ?: return
        onlineGameId = gameId
        gameJob?.cancel()
        _game.value = GameUi(online = true, busy = true)
        _screen.value = Screen.Game
        gameJob = viewModelScope.launch {
            var lastMove = -1
            launch {
                // 상대 시간 초과 감시
                while (isActive) {
                    delay(1_000)
                    val g = _game.value
                    val deadline = g.turnDeadline ?: continue
                    if (!g.finished && g.turn != g.mySide &&
                        System.currentTimeMillis() > deadline + OnlineRepository.TIMEOUT_GRACE_MS
                    ) {
                        repo.claimTimeout(gameId)
                    }
                }
            }
            repo.gameFlow(gameId).collect { og ->
                if (og == null) return@collect
                val side = og.sideOf(me) ?: return@collect
                _game.update { cur -> cur.merge(og, side, lastMove) }
                lastMove = og.moveNo
            }
        }
    }

    private fun GameUi.merge(og: OnlineGame, side: Side, lastMove: Int): GameUi {
        val newMove = og.moveNo != lastMove && og.moveNo > 0
        val log = if (newMove && og.lastDesc.isNotBlank()) (listOf(og.lastDesc) + log).take(30) else log
        val explosion = if (newMove && og.explodedSide != null) {
            Explosion(og.explodedSide, og.explodedIndex, og.moveNo)
        } else {
            explosion
        }
        return copy(
            board = og.board,
            mySide = side,
            turn = og.turn,
            myName = if (side == Side.CAT) og.catName else og.hamName,
            opponentName = if (side == Side.CAT) og.hamName else og.catName,
            opponentScore = if (side == Side.CAT) og.hamScore else og.catScore,
            finished = og.finished,
            winner = og.winner,
            reason = og.reason,
            log = log,
            explosion = explosion,
            turnDeadline = og.turnStartedAt + OnlineRepository.TURN_LIMIT_MS,
            scoreDelta = og.deltaFor(side),
            notice = if (newMove) null else notice,
            busy = false,
        )
    }

    // ---------- AI 연습 ----------

    private var ai: Ai? = null

    fun startAiGame(level: AiLevel) {
        gameJob?.cancel()
        onlineGameId = null
        ai = Ai(level)
        val mySide = if (Random.nextBoolean()) Side.CAT else Side.HAMSTER
        _game.value = GameUi(
            online = false,
            mySide = mySide,
            myName = _lobby.value.profile?.name ?: "나",
            opponentName = "AI 친구 (${level.label})",
        )
        _screen.value = Screen.Game
        gameJob = viewModelScope.launch { aiTurnIfNeeded() }
    }

    private suspend fun aiTurnIfNeeded() {
        val g = _game.value
        if (g.online || g.finished || g.turn == g.mySide) return
        _game.update { it.copy(busy = true) }
        delay(Random.nextLong(700, 1_400)) // 생각하는 척
        val move = ai?.choose(g.board, g.turn) ?: return
        applyLocal(g.turn, move)
    }

    private fun applyLocal(side: Side, move: Move) {
        val g = _game.value
        if (!GameLogic.isLegal(g.board, side, move)) return
        val r = GameLogic.apply(g.board, side, move)
        val w = r.board.winner()
        val id = (g.explosion?.id ?: 0) + 1
        _game.value = g.copy(
            board = r.board,
            turn = side.opponent,
            finished = w != null,
            winner = w,
            reason = if (w != null) "normal" else "",
            log = (listOf(r.description) + g.log).take(30),
            explosion = r.exploded?.let { Explosion(it.first, it.second, id) } ?: g.explosion,
            busy = false,
        )
    }

    /** 테스트(스크린샷)용: 로비 상태를 직접 바꿉니다. */
    internal fun debugSetLobby(f: (LobbyUi) -> LobbyUi) = _lobby.update(f)

    /** 테스트(스크린샷)용: 게임 상태를 직접 바꿉니다. */
    internal fun debugSetGame(f: (GameUi) -> GameUi) {
        gameJob?.cancel()
        _game.update(f)
    }

    // ---------- 공통 ----------

    fun play(move: Move) {
        val g = _game.value
        if (!g.myTurn || !GameLogic.isLegal(g.board, g.mySide, move)) return
        if (g.online) {
            val id = onlineGameId ?: return
            _game.update { it.copy(busy = true) }
            viewModelScope.launch {
                runCatching { repo.submitMove(id, move) }
                    .onFailure { e -> _game.update { it.copy(busy = false, notice = e.message) } }
            }
        } else {
            applyLocal(g.mySide, move)
            gameJob = viewModelScope.launch { aiTurnIfNeeded() }
        }
    }

    /** 게임에서 나가기. 온라인 대전 중이면 기권 처리됩니다. */
    fun leaveGame() {
        val g = _game.value
        val id = onlineGameId
        if (g.online && !g.finished && id != null) {
            viewModelScope.launch { repo.forfeit(id) }
        }
        gameJob?.cancel()
        gameJob = null
        onlineGameId = null
        _screen.value = Screen.Lobby
    }
}
