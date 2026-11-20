package com.bubble.game.ui

import com.bubble.game.i18n.tr

import android.app.Application
import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bubble.game.billing.BillingManager
import com.bubble.game.data.GoogleSignIn
import com.bubble.game.data.Invite
import com.bubble.game.data.OnlineGame
import com.bubble.game.data.OnlineRepository
import com.bubble.game.data.Profile
import com.bubble.game.game.Ai
import com.bubble.game.game.AiLevel
import com.bubble.game.game.Animal
import com.bubble.game.game.Board
import com.bubble.game.game.GameLogic
import com.bubble.game.game.Move
import com.bubble.game.game.Rank
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
    data object Shop : Screen
}

/** 터짐 애니메이션을 한 번만 틀기 위한 표식. */
data class Explosion(val side: Side, val index: Int, val id: Int)

data class GameUi(
    val online: Boolean,
    val board: Board = Board(),
    val mySide: Side = Side.CAT,
    val turn: Side = Side.CAT,
    val myName: String = tr("나", "Me", zh = "我", hi = "मैं", es = "Yo", fr = "Moi", ar = "أنا", de = "Ich", pt = "Eu", ru = "Я", ja = "自分"),
    val opponentName: String = tr("상대", "Opponent", zh = "对手", hi = "विरोधी", es = "Rival", fr = "Adversaire", ar = "الخصم", de = "Gegner", pt = "Rival", ru = "Соперник", ja = "相手"),
    val opponentScore: Int? = null,
    val finished: Boolean = false,
    val winner: Side? = null,
    val reason: String = "",
    val log: List<String> = emptyList(),
    val explosion: Explosion? = null,
    val turnDeadline: Long? = null,
    val scoreDelta: Int? = null,
    /** scoreDelta 중 계급 보너스 */
    val scoreBonus: Int = 0,
    /** 서버가 수를 거절했을 때 등 잠깐 보여줄 안내 문구. */
    val notice: String? = null,
    val busy: Boolean = false,
    /** 장착한 동물 캐릭터 (null이면 기본 고양이/햄스터) */
    val mySkin: Animal? = null,
    val opponentSkin: Animal? = null,
    /** 이번 차례의 제한 시간(초) */
    val turnSec: Int? = null,
    /** AI 대전 난이도 (로컬 연습이든 점수가 걸린 서버 대전이든) */
    val aiLevel: AiLevel? = null,
) {
    /** 서버에서 진행하는 점수 걸린 AI 대전 */
    val scoredAi: Boolean get() = online && aiLevel != null
    val myTurn: Boolean get() = !finished && !busy && turn == mySide
}

data class LobbyUi(
    val loading: Boolean = true,
    val onlineAvailable: Boolean = false,
    val needsLogin: Boolean = false,
    val error: String? = null,
    /** 잠깐 보여줄 안내 (예: 광고 보상 처리 중). */
    val info: String? = null,
    /** 계급이 올랐을 때 축하 화면을 띄울 점수 */
    val rankUpScore: Int? = null,
    /** 상점 구매/장착 처리 중 */
    val shopBusy: Boolean = false,
    /** 가입 환영 팝업: ("new" / "returning" / "admin", 시작 점수) */
    val welcome: Pair<String, Int>? = null,
    /** 동시 접속자 수 (관리자만) */
    val onlineCount: Int? = null,
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
    private var lastRank: Rank? = null

    fun dismissRankUp() = _lobby.update { it.copy(rankUpScore = null) }

    fun dismissWelcome() = _lobby.update { it.copy(welcome = null) }

    // ---------- 인앱 결제 ----------

    /** 포인트 상품 결제. 서버가 영수증을 확인한 뒤에 점수가 들어옵니다. */
    val billing = BillingManager(app, viewModelScope) { productId, token ->
        val ok = repo.verifyPurchase(productId, token)
        val amount = BillingManager.PRODUCTS[productId] ?: 0
        if (ok) {
            _lobby.update {
                it.copy(
                    info = tr(
                        "결제 완료! 💰 상점 포인트 +$amount 🎉", "Payment complete! 💰 +$amount shop points 🎉",
                        zh = "支付完成！💰 商店积分 +$amount 🎉", hi = "भुगतान पूरा! 💰 +$amount शॉप पॉइंट 🎉",
                        es = "¡Pago completado! 💰 +$amount puntos de tienda 🎉", fr = "Paiement effectué ! 💰 +$amount points boutique 🎉",
                        ar = "تم الدفع! 💰 +$amount من نقاط المتجر 🎉", de = "Zahlung abgeschlossen! 💰 +$amount Shop-Punkte 🎉",
                        pt = "Pagamento concluído! 💰 +$amount pontos da loja 🎉", ru = "Оплата прошла! 💰 +$amount очков магазина 🎉",
                        ja = "購入完了！💰 ショップポイント +$amount 🎉",
                    ),
                )
            }
        }
        ok
    }

    fun buyPoints(activity: android.app.Activity, productId: String) {
        if (!billing.buy(activity, productId)) {
            _lobby.update {
                it.copy(
                    error = tr(
                        "플레이 스토어에서 설치한 앱에서만 구매할 수 있어요", "Purchases are only available in the Play Store version",
                        zh = "仅限从 Play 商店安装的版本购买", hi = "खरीदारी सिर्फ़ Play Store वाले वर्ज़न में उपलब्ध है",
                        es = "Las compras solo están disponibles en la versión de Play Store", fr = "Les achats ne sont possibles que dans la version Play Store",
                        ar = "الشراء متاح فقط في نسخة متجر Play", de = "Käufe sind nur in der Play-Store-Version möglich",
                        pt = "Compras só estão disponíveis na versão da Play Store", ru = "Покупки доступны только в версии из Play Маркета",
                        ja = "購入はPlayストア版でのみ利用できます",
                    ),
                )
            }
        }
    }

    // ---------- 친구 추천 ----------

    private var redeemingInvite = false

    /** 초대 링크·설치 정보로 받은 코드. 로그인되어 있으면 바로, 아니면 로그인 후 자동 입력. */
    fun receiveInvite(code: String) {
        Invite.savePending(getApplication(), code)
        _lobby.value.profile?.let { tryRedeemPendingInvite(it) }
    }

    private fun tryRedeemPendingInvite(p: Profile) {
        val app = getApplication<Application>()
        val code = Invite.pending(app) ?: return
        if (p.referred || p.refCode == code) {
            Invite.clearPending(app)
            return
        }
        if (redeemingInvite) return
        redeemingInvite = true
        viewModelScope.launch {
            try {
                repo.redeemReferral(code)
                _lobby.update {
                    it.copy(
                        info = tr(
                            "초대 코드 ${code}가 입력됐어요! 친구에게 +30점이 들어갔어요 🎁", "Invite code $code applied! Your friend got +30 pts 🎁",
                            zh = "已使用邀请码 $code！你的朋友获得 +30 分 🎁", hi = "इनवाइट कोड $code लग गया! दोस्त को +30 अंक मिले 🎁",
                            es = "¡Código $code aplicado! Tu amigo recibió +30 pts 🎁", fr = "Code $code appliqué ! Ton ami a reçu +30 pts 🎁",
                            ar = "تم تطبيق رمز الدعوة $code! حصل صديقك على +30 نقطة 🎁", de = "Code $code eingelöst! Dein Freund hat +30 Pkt bekommen 🎁",
                            pt = "Código $code aplicado! Seu amigo ganhou +30 pts 🎁", ru = "Код $code принят! Друг получил +30 очк. 🎁",
                            ja = "招待コード ${code} を入力しました！友だちに+30点 🎁",
                        ),
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _lobby.update { it.copy(error = e.message) }
            } finally {
                Invite.clearPending(app)
                redeemingInvite = false
            }
        }
    }

    /** 내 추천 코드 불러오기 (없으면 서버가 만듦). */
    fun loadReferralCode() {
        if (_lobby.value.profile?.refCode != null || uid == null) return
        viewModelScope.launch { runCatching { repo.referralCode() } }
    }

    fun redeemReferral(code: String) = shopAction(
        tr(
            "코드를 입력했어요! 친구에게 +30점이 들어갔어요 🎁", "Code applied! Your friend got +30 pts 🎁",
            zh = "已使用邀请码！你的朋友获得 +30 分 🎁", hi = "कोड लग गया! आपके दोस्त को +30 अंक मिले 🎁",
            es = "¡Código aplicado! Tu amigo recibió +30 pts 🎁", fr = "Code appliqué ! Ton ami a reçu +30 pts 🎁",
            ar = "تم تطبيق الرمز! حصل صديقك على +30 نقطة 🎁", de = "Code eingelöst! Dein Freund hat +30 Pkt bekommen 🎁",
            pt = "Código aplicado! Seu amigo ganhou +30 pts 🎁", ru = "Код принят! Друг получил +30 очк. 🎁",
            ja = "コードを入力しました！友だちに+30点が入りました 🎁",
        ),
    ) { repo.redeemReferral(code) }

    // ---------- 상점 ----------

    fun openShop() {
        if (_lobby.value.profile != null) _screen.value = Screen.Shop
    }

    fun closeShop() {
        _screen.value = Screen.Lobby
    }

    /** "POTION" 또는 동물 이름. 점수 검사·차감은 서버가 합니다. */
    fun buy(item: String, label: String) = shopAction(tr("$label 구매 완료! 🎉", "Bought $label! 🎉", zh = "已购买 ${label}！🎉", hi = "$label खरीद लिया! 🎉", es = "¡Compraste $label! 🎉", fr = "$label acheté ! 🎉", ar = "تم شراء $label! 🎉", de = "$label gekauft! 🎉", pt = "Você comprou $label! 🎉", ru = "Куплено: $label! 🎉", ja = "${label}を買いました！🎉")) { repo.buyItem(item) }

    fun equip(animal: Animal?) = shopAction(if (animal == null) tr("기본 캐릭터로 바꿨어요", "Switched back to the default character", zh = "已换回默认角色", hi = "डिफ़ॉल्ट किरदार पर वापस", es = "Volviste al personaje por defecto", fr = "Retour au personnage par défaut", ar = "تمت العودة إلى الشخصية الافتراضية", de = "Zurück zur Standardfigur", pt = "Voltou ao personagem padrão", ru = "Возвращён обычный персонаж", ja = "デフォルトのキャラに戻しました") else tr("${animal.label} 장착!", "${animal.label} equipped!", zh = "已装备${animal.label}！", hi = "${animal.label} लगा दिया!", es = "¡${animal.label} equipado!", fr = "${animal.label} équipé !", ar = "تم تجهيز ${animal.label}!", de = "${animal.label} ausgerüstet!", pt = "${animal.label} equipado!", ru = "${animal.label}: надето!", ja = "${animal.label}を装備しました！")) {
        repo.equipAnimal(animal)
    }

    private fun shopAction(success: String, action: suspend () -> Unit) {
        if (_lobby.value.shopBusy) return
        _lobby.update { it.copy(shopBusy = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                action()
                _lobby.update { it.copy(shopBusy = false, info = success) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _lobby.update { it.copy(shopBusy = false, error = e.message ?: tr("실패했어요", "Something went wrong", zh = "出错了", hi = "कुछ गड़बड़ हो गई", es = "Algo salió mal", fr = "Un problème est survenu", ar = "حدث خطأ ما", de = "Etwas ist schiefgelaufen", pt = "Algo deu errado", ru = "Что-то пошло не так", ja = "問題が発生しました")) }
            }
        }
    }

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
                it.copy(error = tr("Firebase 콘솔에서 Google 로그인을 켜고 google-services.json을 다시 받아 넣어야 해요.", "Enable Google sign-in in the Firebase console and add a fresh google-services.json."))
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
                _lobby.update { it.copy(loading = false, error = tr(
"구글 로그인에 실패했어요: ${e.message}",
"Google sign-in failed: ${e.message}",
zh = "Google 登录失败：${e.message}", hi = "Google साइन-इन विफल: ${e.message}", es = "Error al iniciar sesión con Google: ${e.message}", fr = "Échec de la connexion Google : ${e.message}", ar = "فشل تسجيل الدخول عبر Google: ${e.message}", de = "Google-Anmeldung fehlgeschlagen: ${e.message}", pt = "Falha no login com Google: ${e.message}", ru = "Не удалось войти через Google: ${e.message}", ja = "Googleログインに失敗しました：${e.message}",
)) }
            }
        }
    }

    fun signOut() {
        sessionJob?.cancel()
        sessionJob = null
        uid = null
        lastRank = null
        repo.signOut()
        _lobby.value = LobbyUi(onlineAvailable = true, loading = false, needsLogin = true)
    }

    /** 계정과 모든 게임 데이터를 지우고 로그인 화면으로 돌아갑니다. */
    fun deleteAccount() {
        if (_lobby.value.loading) return
        _lobby.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                repo.deleteAccount()
                sessionJob?.cancel()
                sessionJob = null
                uid = null
                lastRank = null
                _lobby.value = LobbyUi(
                    onlineAvailable = true,
                    loading = false,
                    needsLogin = true,
                    info = tr(
"계정이 삭제됐어요. 이용해 주셔서 고마워요 🐾",
"Your account was deleted. Thanks for playing 🐾",
zh = "你的账号已删除。感谢游玩 🐾", hi = "आपका खाता हटा दिया गया। खेलने के लिए धन्यवाद 🐾", es = "Tu cuenta se eliminó. ¡Gracias por jugar! 🐾", fr = "Ton compte a été supprimé. Merci d’avoir joué 🐾", ar = "تم حذف حسابك. شكرًا للعب 🐾", de = "Dein Konto wurde gelöscht. Danke fürs Spielen 🐾", pt = "Sua conta foi excluída. Obrigado por jogar 🐾", ru = "Аккаунт удалён. Спасибо за игру 🐾", ja = "アカウントを削除しました。遊んでくれてありがとう 🐾",
),
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _lobby.update {
                    it.copy(loading = false, error = e.message ?: tr("계정을 삭제하지 못했어요", "Could not delete your account", zh = "无法删除账号", hi = "खाता हटाया नहीं जा सका", es = "No se pudo eliminar tu cuenta", fr = "Impossible de supprimer ton compte", ar = "تعذّر حذف حسابك", de = "Konto konnte nicht gelöscht werden", pt = "Não foi possível excluir sua conta", ru = "Не удалось удалить аккаунт", ja = "アカウントを削除できませんでした"))
                }
            }
        }
    }

    /** 로그인된 상태에서 프로필/랭킹을 다시 불러옵니다. */
    fun connect() {
        uid?.let { startSession(it) }
    }

    private fun startSession(id: String) {
        uid = id
        billing.start()
        sessionJob?.cancel()
        _lobby.update { it.copy(loading = true, needsLogin = false, error = null) }
        sessionJob = viewModelScope.launch {
            try {
                repo.ensureProfile()?.let { w -> _lobby.update { it.copy(welcome = w) } }
                // 1분마다 접속 신호
                launch {
                    while (isActive) {
                        runCatching { repo.pingPresence(id) }
                        delay(60_000)
                    }
                }
                // 관리자면 30초마다 동시 접속자 수
                launch {
                    while (isActive) {
                        if (_lobby.value.profile?.admin == true) {
                            runCatching { repo.onlineCount() }.onSuccess { n -> _lobby.update { it.copy(onlineCount = n) } }
                        }
                        delay(30_000)
                    }
                }
                launch {
                    repo.profileFlow(id).collect { p ->
                        val rank = p?.rank
                        val prev = lastRank
                        val rankedUp = rank != null && prev != null && rank.ordinal > prev.ordinal
                        if (rank != null) lastRank = rank
                        if (p != null) tryRedeemPendingInvite(p)
                        _lobby.update {
                            it.copy(
                                profile = p,
                                loading = false,
                                rankUpScore = if (rankedUp) p!!.score else it.rankUpScore,
                            )
                        }
                    }
                }
                launch { repo.leaderboardFlow().collect { l -> _lobby.update { it.copy(leaderboard = l) } } }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _lobby.update { it.copy(loading = false, error = tr(
"서버에 연결하지 못했어요: ${e.message}",
"Could not connect to the server: ${e.message}",
zh = "无法连接服务器：${e.message}", hi = "सर्वर से कनेक्ट नहीं हो सका: ${e.message}", es = "No se pudo conectar con el servidor: ${e.message}", fr = "Connexion au serveur impossible : ${e.message}", ar = "تعذّر الاتصال بالخادم: ${e.message}", de = "Keine Verbindung zum Server: ${e.message}", pt = "Não foi possível conectar ao servidor: ${e.message}", ru = "Не удалось подключиться к серверу: ${e.message}", ja = "サーバーに接続できませんでした：${e.message}",
)) }
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
                _lobby.update { it.copy(error = tr("매칭 중 문제가 생겼어요: ${e.message}", "Matching failed: ${e.message}", zh = "匹配失败：${e.message}", hi = "मैचिंग विफल: ${e.message}", es = "Error al emparejar: ${e.message}", fr = "Échec du matchmaking : ${e.message}", ar = "فشلت المطابقة: ${e.message}", de = "Matchmaking fehlgeschlagen: ${e.message}", pt = "Falha no pareamento: ${e.message}", ru = "Ошибка подбора: ${e.message}", ja = "マッチングに失敗しました：${e.message}")) }
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
            opponentName = og.vsAi?.let { tr("AI 친구 (${it.label})", "AI Buddy (${it.label})", zh = "AI 伙伴（${it.label}）", hi = "AI दोस्त (${it.label})", es = "Amigo IA (${it.label})", fr = "Copain IA (${it.label})", ar = "رفيق الذكاء الاصطناعي (${it.label})", de = "KI-Kumpel (${it.label})", pt = "Amigo IA (${it.label})", ru = "ИИ-друг (${it.label})", ja = "AIフレンド（${it.label}）") }
                ?: if (side == Side.CAT) og.hamName else og.catName,
            opponentScore = if (side == Side.CAT) og.hamScore else og.catScore,
            finished = og.finished,
            winner = og.winner,
            reason = og.reason,
            log = log,
            explosion = explosion,
            // AI 대전은 시간 제한 없음
            turnDeadline = if (og.vsAi != null) null else og.turnStartedAt + og.turnMsOf(og.turn),
            turnSec = if (og.vsAi != null) null else (og.turnMsOf(og.turn) / 1000).toInt(),
            aiLevel = og.vsAi,
            mySkin = og.skinOf(side),
            opponentSkin = og.skinOf(side.opponent),
            scoreDelta = og.deltaFor(side),
            scoreBonus = og.bonusFor(side),
            notice = if (newMove) null else notice,
            busy = false,
        )
    }

    // ---------- AI 연습 ----------

    private var ai: Ai? = null

    fun startAiGame(level: AiLevel) {
        // 로그인했으면 점수가 걸린 서버 AI 대전, 아니면 폰 안에서 하는 연습
        if (repo.available && uid != null && _lobby.value.profile != null) {
            gameJob?.cancel()
            onlineGameId = null
            _game.value = GameUi(online = true, busy = true, aiLevel = level, opponentName = tr("AI 친구 (${level.label})", "AI Buddy (${level.label})", zh = "AI 伙伴（${level.label}）", hi = "AI दोस्त (${level.label})", es = "Amigo IA (${level.label})", fr = "Copain IA (${level.label})", ar = "رفيق الذكاء الاصطناعي (${level.label})", de = "KI-Kumpel (${level.label})", pt = "Amigo IA (${level.label})", ru = "ИИ-друг (${level.label})", ja = "AIフレンド（${level.label}）"))
            _screen.value = Screen.Game
            gameJob = viewModelScope.launch {
                try {
                    startOnlineGame(repo.startAiGame(level))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 서버에 연결할 수 없으면 점수 없는 연습으로라도 계속 플레이
                    startLocalAiGame(level)
                    _game.update {
                        it.copy(
                            notice = tr(
"서버에 연결할 수 없어 점수 없는 연습으로 진행해요",
"Can't reach the server, so this is an unscored practice game",
zh = "无法连接服务器，本局为不计分练习", hi = "सर्वर तक नहीं पहुँच पाए, यह बिना अंकों का अभ्यास गेम है", es = "No hay conexión con el servidor; esta es una práctica sin puntos", fr = "Serveur injoignable : partie d’entraînement sans points", ar = "تعذّر الوصول إلى الخادم، هذه مباراة تدريب بلا نقاط", de = "Server nicht erreichbar, das ist eine Übungspartie ohne Punkte", pt = "Sem conexão com o servidor; esta é uma partida de treino sem pontos", ru = "Сервер недоступен, это тренировка без очков", ja = "サーバーに接続できないため、点数なしの練習になります",
) + " (${e.message ?: "error"})",
                        )
                    }
                }
            }
            return
        }
        startLocalAiGame(level)
    }

    private fun startLocalAiGame(level: AiLevel) {
        gameJob?.cancel()
        onlineGameId = null
        ai = Ai(level)
        val mySide = if (Random.nextBoolean()) Side.CAT else Side.HAMSTER
        _game.value = GameUi(
            online = false,
            mySide = mySide,
            myName = _lobby.value.profile?.name ?: tr("나", "Me", zh = "我", hi = "मैं", es = "Yo", fr = "Moi", ar = "أنا", de = "Ich", pt = "Eu", ru = "Я", ja = "自分"),
            opponentName = tr("AI 친구 (${level.label})", "AI Buddy (${level.label})", zh = "AI 伙伴（${level.label}）", hi = "AI दोस्त (${level.label})", es = "Amigo IA (${level.label})", fr = "Copain IA (${level.label})", ar = "رفيق الذكاء الاصطناعي (${level.label})", de = "KI-Kumpel (${level.label})", pt = "Amigo IA (${level.label})", ru = "ИИ-друг (${level.label})", ja = "AIフレンド（${level.label}）"),
            mySkin = _lobby.value.profile?.equipped,
            aiLevel = level,
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
            log = (listOf(r.localized) + g.log).take(30),
            explosion = r.exploded?.let { Explosion(it.first, it.second, id) } ?: g.explosion,
            busy = false,
        )
    }

    /** 테스트(스크린샷)용: 로비 상태를 직접 바꿉니다. */
    /** 보상형 광고의 서버 확인(SSV)에 실어 보낼 (uid, gameId). 온라인 게임에서 졌을 때만. */
    fun rewardTarget(): Pair<String, String>? {
        val g = _game.value
        val me = uid ?: return null
        val id = onlineGameId ?: return null
        if (g.scoredAi) return null
        return if (g.online && g.finished && g.winner != null && g.winner != g.mySide) me to id else null
    }

    /** 광고를 끝까지 봤을 때 (점수는 서버가 AdMob 확인 후 반영). */
    fun onAdRewardEarned() {
        _lobby.update { it.copy(info = tr(
"📺 광고 보상 확인 중… 곧 점수가 돌아와요",
"📺 Checking your ad reward… points coming soon",
zh = "📺 正在确认广告奖励… 分数马上返还", hi = "📺 विज्ञापन इनाम जाँच रहे हैं… अंक जल्द मिलेंगे", es = "📺 Comprobando tu recompensa… los puntos llegan pronto", fr = "📺 Vérification de ta récompense… les points arrivent", ar = "📺 جارٍ التحقق من مكافأة الإعلان… ستصل النقاط قريبًا", de = "📺 Werbebelohnung wird geprüft… Punkte kommen gleich", pt = "📺 Verificando sua recompensa… os pontos chegam logo", ru = "📺 Проверяем награду за рекламу… очки скоро вернутся", ja = "📺 広告報酬を確認中… まもなく点数が戻ります",
)) }
        viewModelScope.launch {
            delay(15_000)
            _lobby.update { it.copy(info = null) }
        }
    }

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
