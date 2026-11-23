package com.bubble.game.ui

import com.bubble.game.i18n.tr
import com.bubble.game.data.Invite
import com.bubble.game.data.Country
import com.bubble.game.billing.BillingManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import com.bubble.game.i18n.Lang

import android.app.Activity
import com.bubble.game.ads.AdManager
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.graphicsLayer
import com.bubble.game.game.Shop
import com.bubble.game.game.Rank
import com.bubble.game.game.Animal
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bubble.game.data.OnlineRepository
import com.bubble.game.game.AiLevel
import com.bubble.game.game.GameLogic
import com.bubble.game.game.Move
import com.bubble.game.game.Side
import kotlinx.coroutines.delay

/** 🔊/🔇 배경음악 켜고 끄기 */
@Composable
private fun MusicToggle() {
    val on = com.bubble.game.audio.Music.enabled
    Text(
        if (on) "🔊" else "🔇", fontSize = 18.sp,
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(50))
            .clickable { com.bubble.game.audio.Music.toggle() }
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun BubbleApp(vm: AppViewModel) {
    val screen by vm.screen.collectAsState()
    // 🎵 배경음악: 게임 중엔 내 캐릭터 곡, 그 밖엔 로비 곡
    val gameNow by vm.game.collectAsState()
    val track = when (screen) {
        Screen.Game -> com.bubble.game.audio.Music.gameTrack(gameNow.mySkin, gameNow.mySide)
        is Screen.Matching, is Screen.FriendWait -> com.bubble.game.audio.Music.matching
        else -> com.bubble.game.audio.Music.lobby
    }
    LaunchedEffect(track) { com.bubble.game.audio.Music.play(track) }
    // 아랍어는 오른쪽에서 왼쪽으로
    val direction = if (Lang.current.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
    BubbleBackground {
        AnimatedContent(
            targetState = screen,
            contentKey = { it::class },
            transitionSpec = { (fadeIn(tween(350)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(200)) },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.Lobby -> LobbyScreen(vm)
                is Screen.Matching -> MatchingScreen(s.startedAt, onCancel = vm::cancelMatching)
                is Screen.FriendWait -> FriendWaitScreen(s.code, s.startedAt, onCancel = vm::cancelFriendBattle)
                Screen.Game -> GameScreen(vm)
                Screen.Shop -> ShopScreen(vm)
            }
        }
        val lobby by vm.lobby.collectAsState()
        val rankUp = lobby.rankUpScore
        if (rankUp != null && screen == Screen.Lobby) RankUpCelebration(rankUp, onDismiss = vm::dismissRankUp)
        lobby.rankDown?.let { (from, score) ->
            if (screen == Screen.Lobby) RankDownNotice(from, score, onDismiss = vm::dismissRankDown)
        }
        val checkin = lobby.checkin
        androidx.compose.animation.AnimatedVisibility(
            checkin != null && screen == Screen.Lobby,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(250)),
        ) {
            // 사라지는 동안에도 마지막 내용을 보여줌
            val last = remember { mutableStateOf(checkin) }
            if (checkin != null) last.value = checkin
            last.value?.let { CheckinCelebration(it.day, it.coins, it.potion, panda = it.panda, onDismiss = vm::dismissCheckin) }
        }
        val mission = lobby.mission
        androidx.compose.animation.AnimatedVisibility(
            mission != null && screen == Screen.Lobby,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(250)),
        ) {
            val last = remember { mutableStateOf(mission) }
            if (mission != null) last.value = mission
            last.value?.let { MissionCelebration(it.label, it.coins, onDismiss = vm::dismissMission) }
        }
    }
    }
}

// ======================= 로비 (대기방) =======================

@Composable
private fun LobbyScreen(vm: AppViewModel) {
    val ui by vm.lobby.collectAsState()
    var showRules by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var showRanks by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showAdmin by remember { mutableStateOf(false) }
    var cardOf by remember { mutableStateOf<RankedPlayer?>(null) }
    cardOf?.let { c -> PlayerCardDialog(c, me = c.uid == ui.profile?.uid) { cardOf = null } }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LanguageMenu()
            Spacer(Modifier.width(8.dp))
            MusicToggle()
            // 관리자만: 🛠 포인트 수정
            if (ui.profile?.admin == true) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "🛠", fontSize = 18.sp,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(50))
                        .clickable { showAdmin = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            // 관리자만: 동시 접속자 수
            ui.onlineCount?.let { n ->
                Spacer(Modifier.width(8.dp))
                Text(
                    tr("🟢 ${n}명 접속", "🟢 $n online", zh = "🟢 在线 $n", hi = "🟢 $n ऑनलाइन", es = "🟢 $n en línea",
                        fr = "🟢 $n en ligne", ar = "🟢 $n متصل", de = "🟢 $n online", pt = "🟢 $n online",
                        ru = "🟢 $n в сети", ja = "🟢 ${n}人接続中"),
                    fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF2E7D32),
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (ui.profile != null) ShopIconButton(onClick = vm::openShop)
        }
        TitleBanner()
        TextButton(onClick = { showRules = true }) { Text(tr("📖 게임 규칙 보기", "📖 How to play", zh = "📖 游戏规则", hi = "📖 कैसे खेलें", es = "📖 Cómo jugar", fr = "📖 Règles du jeu", ar = "📖 طريقة اللعب", de = "📖 Spielregeln", pt = "📖 Como jogar", ru = "📖 Как играть", ja = "📖 遊び方"), color = Ui.text) }
        Spacer(Modifier.height(16.dp))

        SoftCard {
            when {
                !ui.onlineAvailable -> Text(
                    tr("온라인 대전은 Firebase 설정(google-services.json)을 넣고 다시 빌드하면 열려요.\n지금은 AI 연습을 즐겨 주세요!", "Online play opens once Firebase (google-services.json) is added and the app is rebuilt.\nEnjoy AI practice for now!"),
                    color = Ui.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                ui.needsLogin && !ui.loading -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(tr(
"구글 계정으로 로그인하면\n점수와 전적이 계정에 저장돼요",
"Sign in with Google to save\nyour score and record",
zh = "用 Google 登录，\n保存你的分数和战绩", hi = "Google से साइन इन करें और\nअपना स्कोर व रिकॉर्ड सेव करें", es = "Inicia sesión con Google para guardar\ntu puntuación y récord", fr = "Connecte-toi avec Google pour sauvegarder\nton score et ton palmarès", ar = "سجّل الدخول عبر Google لحفظ\nنقاطك وسجلّك", de = "Melde dich mit Google an, um\nPunkte und Bilanz zu speichern", pt = "Entre com o Google para salvar\nsua pontuação e histórico", ru = "Войдите через Google, чтобы сохранить\nсчёт и статистику", ja = "Google でログインすると\nスコアと戦績が保存されます",
), color = Ui.text, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text(tr(
"상대에게는 구글 이름 대신 닉네임만 보여요",
"Opponents only see your nickname, not your Google name",
zh = "对手只能看到你的昵称，看不到 Google 名字", hi = "विरोधियों को सिर्फ़ आपका निकनेम दिखेगा, Google नाम नहीं", es = "Los rivales solo ven tu apodo, no tu nombre de Google", fr = "Les adversaires ne voient que ton pseudo, pas ton nom Google", ar = "يرى الخصوم لقبك فقط وليس اسمك في Google", de = "Gegner sehen nur deinen Spitznamen, nicht deinen Google-Namen", pt = "Os rivais só veem seu apelido, não seu nome do Google", ru = "Соперники видят только ваш ник, а не имя в Google", ja = "相手にはGoogleの名前ではなくニックネームだけが表示されます",
), fontSize = 12.sp, color = Ui.text.copy(alpha = 0.6f))
                    Spacer(Modifier.height(12.dp))
                    val activity = LocalContext.current
                    Button(
                        onClick = { vm.signInWithGoogle(activity) },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ui.text),
                        border = BorderStroke(1.dp, Color(0xFFDADCE0)),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text("G", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color(0xFF4285F4))
                        Spacer(Modifier.width(10.dp))
                        Text(tr("Google로 시작하기", "Continue with Google", zh = "使用 Google 继续", hi = "Google के साथ जारी रखें", es = "Continuar con Google", fr = "Continuer avec Google", ar = "المتابعة باستخدام Google", de = "Mit Google fortfahren", pt = "Continuar com o Google", ru = "Продолжить с Google", ja = "Google で続ける"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    if (ui.error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(ui.error!!, color = Ui.danger, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
                ui.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Ui.primary, strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(tr("서버에 접속하는 중…", "Connecting to server…", zh = "正在连接服务器…", hi = "सर्वर से जुड़ रहे हैं…", es = "Conectando con el servidor…", fr = "Connexion au serveur…", ar = "جارٍ الاتصال بالخادم…", de = "Verbinde mit dem Server…", pt = "Conectando ao servidor…", ru = "Подключение к серверу…", ja = "サーバーに接続中…"), color = Ui.text)
                }
                ui.profile != null -> {
                    val p = ui.profile!!
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Critter(Side.CAT, Modifier.size(52.dp), skin = p.equipped)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ui.text)
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showRanks = true },
                            ) {
                                RankBadge(p.score)
                                Text(tr("  계급표 ›", "  Ranks ›", zh = "  等级表 ›", hi = "  रैंक ›", es = "  Rangos ›", fr = "  Rangs ›", ar = "  الرتب ›", de = "  Ränge ›", pt = "  Ranks ›", ru = "  Ранги ›", ja = "  ランク表 ›"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ui.accent)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                tr(
                                    "${p.wins}승 ${p.losses}패 · 💖 ${p.potions} · 💰 ${p.coins}",
                                    "${p.wins}W ${p.losses}L · 💖 ${p.potions} · 💰 ${p.coins}",
                                    zh = "${p.wins}胜 ${p.losses}负 · 💖 ${p.potions} · 💰 ${p.coins}",
                                    hi = "${p.wins}जीत ${p.losses}हार · 💖 ${p.potions} · 💰 ${p.coins}",
                                    es = "${p.wins}V ${p.losses}D · 💖 ${p.potions} · 💰 ${p.coins}",
                                    fr = "${p.wins}V ${p.losses}D · 💖 ${p.potions} · 💰 ${p.coins}",
                                    ar = "${p.wins} فوز ${p.losses} خسارة · 💖 ${p.potions} · 💰 ${p.coins}",
                                    de = "${p.wins}S ${p.losses}N · 💖 ${p.potions} · 💰 ${p.coins}",
                                    pt = "${p.wins}V ${p.losses}D · 💖 ${p.potions} · 💰 ${p.coins}",
                                    ru = "${p.wins}П ${p.losses}Пр · 💖 ${p.potions} · 💰 ${p.coins}",
                                    ja = "${p.wins}勝 ${p.losses}敗 · 💖 ${p.potions} · 💰 ${p.coins}",
                                ),
                                fontSize = 13.sp, color = Ui.text.copy(alpha = 0.7f),
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${p.score}", fontWeight = FontWeight.Black, fontSize = 26.sp, color = Ui.primaryDark)
                            Text(tr("점", "pts", zh = "分", hi = "अंक", es = "pts", fr = "pts", ar = "نقطة", de = "Pkt", pt = "pts", ru = "очк.", ja = "点"), fontSize = 12.sp, color = Ui.text.copy(alpha = 0.7f))
                        }
                    }
                    Row {
                        TextButton(onClick = { renaming = true }) { Text(tr("✏️ 닉네임 바꾸기", "✏️ Change nickname", zh = "✏️ 修改昵称", hi = "✏️ निकनेम बदलें", es = "✏️ Cambiar apodo", fr = "✏️ Changer de pseudo", ar = "✏️ تغيير اللقب", de = "✏️ Spitznamen ändern", pt = "✏️ Mudar apelido", ru = "✏️ Сменить ник", ja = "✏️ ニックネーム変更"), color = Ui.accent) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = vm::signOut) { Text(tr("로그아웃", "Sign out", zh = "退出登录", hi = "साइन आउट", es = "Cerrar sesión", fr = "Se déconnecter", ar = "تسجيل الخروج", de = "Abmelden", pt = "Sair", ru = "Выйти", ja = "ログアウト"), color = Ui.text.copy(alpha = 0.6f)) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            tr("계정 삭제", "Delete account", zh = "删除账号", hi = "खाता हटाएँ", es = "Eliminar cuenta", fr = "Supprimer le compte", ar = "حذف الحساب", de = "Konto löschen", pt = "Excluir conta", ru = "Удалить аккаунт", ja = "アカウント削除"),
                            fontSize = 12.sp,
                            color = Ui.danger.copy(alpha = 0.7f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { confirmDelete = true }
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                        )
                    }
                }
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(ui.error ?: tr("접속하지 못했어요", "Could not connect", zh = "无法连接", hi = "कनेक्ट नहीं हो सका", es = "No se pudo conectar", fr = "Connexion impossible", ar = "تعذّر الاتصال", de = "Verbindung fehlgeschlagen", pt = "Não foi possível conectar", ru = "Не удалось подключиться", ja = "接続できませんでした"), color = Ui.danger, textAlign = TextAlign.Center)
                    TextButton(onClick = vm::connect) { Text(tr("다시 시도", "Retry", zh = "重试", hi = "फिर कोशिश करें", es = "Reintentar", fr = "Réessayer", ar = "إعادة المحاولة", de = "Erneut versuchen", pt = "Tentar de novo", ru = "Повторить", ja = "再試行")) }
                }
            }
            if (ui.error != null && ui.profile != null) {
                Text(ui.error!!, color = Ui.danger, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        BigButton(
            text = tr("🎲  랜덤 매칭", "🎲  Random Match", zh = "🎲  随机匹配", hi = "🎲  रैंडम मैच", es = "🎲  Partida aleatoria", fr = "🎲  Match aléatoire", ar = "🎲  مباراة عشوائية", de = "🎲  Zufallsmatch", pt = "🎲  Partida aleatória", ru = "🎲  Случайный матч", ja = "🎲  ランダムマッチ"),
            sub = tr(
"익명의 상대와 1:1 대결 · 승리 +${OnlineRepository.WIN_POINTS} · 패배 -${OnlineRepository.LOSE_POINTS}",
"1:1 vs an anonymous player · Win +${OnlineRepository.WIN_POINTS} · Lose -${OnlineRepository.LOSE_POINTS}",
zh = "与匿名玩家 1:1 对战 · 胜 +${OnlineRepository.WIN_POINTS} · 负 -${OnlineRepository.LOSE_POINTS}", hi = "गुमनाम खिलाड़ी से 1:1 · जीत +${OnlineRepository.WIN_POINTS} · हार -${OnlineRepository.LOSE_POINTS}", es = "1:1 contra un jugador anónimo · Gana +${OnlineRepository.WIN_POINTS} · Pierde -${OnlineRepository.LOSE_POINTS}", fr = "1:1 contre un joueur anonyme · Victoire +${OnlineRepository.WIN_POINTS} · Défaite -${OnlineRepository.LOSE_POINTS}", ar = "1:1 ضد لاعب مجهول · الفوز +${OnlineRepository.WIN_POINTS} · الخسارة -${OnlineRepository.LOSE_POINTS}", de = "1:1 gegen anonyme Gegner · Sieg +${OnlineRepository.WIN_POINTS} · Niederlage -${OnlineRepository.LOSE_POINTS}", pt = "1:1 contra um jogador anônimo · Vitória +${OnlineRepository.WIN_POINTS} · Derrota -${OnlineRepository.LOSE_POINTS}", ru = "1:1 с анонимным игроком · Победа +${OnlineRepository.WIN_POINTS} · Поражение -${OnlineRepository.LOSE_POINTS}", ja = "匿名の相手と1対1 · 勝ち +${OnlineRepository.WIN_POINTS} · 負け -${OnlineRepository.LOSE_POINTS}",
),
            enabled = ui.onlineAvailable && ui.profile != null && !ui.needsLogin,
            onClick = vm::startMatching,
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = vm::startFriendBattle,
            enabled = ui.onlineAvailable && ui.profile != null && !ui.needsLogin,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ui.accent),
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tr("👫  친구와 대전", "👫  Battle a Friend", zh = "👫  好友对战", hi = "👫  दोस्त से मुकाबला", es = "👫  Reta a un amigo", fr = "👫  Défier un ami", ar = "👫  تحدَّ صديقًا", de = "👫  Freund herausfordern", pt = "👫  Desafiar um amigo", ru = "👫  Бой с другом", ja = "👫  友だちと対戦"), fontSize = 19.sp, fontWeight = FontWeight.Black)
                Text(
                    tr(
                        "링크를 보내 1:1 · 승리 +${OnlineRepository.FRIEND_WIN_POINTS} · 패배 -${OnlineRepository.FRIEND_LOSE_POINTS}",
                        "Send a link, play 1:1 · Win +${OnlineRepository.FRIEND_WIN_POINTS} · Lose -${OnlineRepository.FRIEND_LOSE_POINTS}",
                        zh = "发送链接 1:1 · 胜 +${OnlineRepository.FRIEND_WIN_POINTS} · 负 -${OnlineRepository.FRIEND_LOSE_POINTS}",
                        es = "Envía un enlace, 1:1 · Gana +${OnlineRepository.FRIEND_WIN_POINTS} · Pierde -${OnlineRepository.FRIEND_LOSE_POINTS}",
                        fr = "Envoie un lien, 1:1 · Victoire +${OnlineRepository.FRIEND_WIN_POINTS} · Défaite -${OnlineRepository.FRIEND_LOSE_POINTS}",
                        de = "Link senden, 1:1 · Sieg +${OnlineRepository.FRIEND_WIN_POINTS} · Niederlage -${OnlineRepository.FRIEND_LOSE_POINTS}",
                        pt = "Envie um link, 1:1 · Vitória +${OnlineRepository.FRIEND_WIN_POINTS} · Derrota -${OnlineRepository.FRIEND_LOSE_POINTS}",
                        ru = "Отправьте ссылку, 1:1 · Победа +${OnlineRepository.FRIEND_WIN_POINTS} · Поражение -${OnlineRepository.FRIEND_LOSE_POINTS}",
                        ja = "リンクを送って1対1 · 勝ち +${OnlineRepository.FRIEND_WIN_POINTS} · 負け -${OnlineRepository.FRIEND_LOSE_POINTS}",
                    ),
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SoftCard {
            Text(if (ui.profile != null) tr("🤖 AI 대전", "🤖 AI Battle", zh = "🤖 AI 对战", hi = "🤖 AI मुकाबला", es = "🤖 Batalla contra IA", fr = "🤖 Combat contre l’IA", ar = "🤖 معركة ضد الذكاء الاصطناعي", de = "🤖 KI-Duell", pt = "🤖 Batalha contra IA", ru = "🤖 Бой с ИИ", ja = "🤖 AI対戦") else tr("🤖 AI와 연습하기", "🤖 Practice with AI", zh = "🤖 与 AI 练习", hi = "🤖 AI के साथ अभ्यास", es = "🤖 Practicar con IA", fr = "🤖 S’entraîner contre l’IA", ar = "🤖 تدرّب مع الذكاء الاصطناعي", de = "🤖 Gegen KI üben", pt = "🤖 Treinar com IA", ru = "🤖 Тренировка с ИИ", ja = "🤖 AIと練習"), fontWeight = FontWeight.Bold, color = Ui.text)
            Spacer(Modifier.height(8.dp))
            // 난이도마다 버튼 아래에 승·패 점수와 턴 시간을 세로로 맞춰 표시
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiLevel.entries.forEach { lv ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        OutlinedButton(
                            onClick = { vm.startAiGame(lv) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        ) { Text(lv.label, color = Ui.accent, maxLines = 1, softWrap = false, fontSize = 14.sp) }
                        Spacer(Modifier.height(4.dp))
                        if (ui.profile != null) {
                            Text(
                                "🏆 +${lv.winPoints}   💔 ${if (lv.losePoints == 0) "0" else lv.losePoints}",
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ui.text.copy(alpha = 0.8f),
                                maxLines = 1, softWrap = false,
                            )
                        }
                        Text(
                            "⏱ " + tr("${lv.turnSec}초", "${lv.turnSec}s", zh = "${lv.turnSec}秒", hi = "${lv.turnSec}से",
                                ar = "${lv.turnSec}ث", ru = "${lv.turnSec}с", ja = "${lv.turnSec}秒"),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ui.accent, maxLines = 1,
                        )
                    }
                }
            }
            if (ui.profile == null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    tr("로그인하면 AI 대전도 점수가 걸려요", "Sign in to earn points in AI battles too", zh = "登录后 AI 对战也能得分",
                        hi = "साइन इन करें, AI मुकाबलों में भी अंक मिलेंगे", es = "Inicia sesión para ganar puntos también contra la IA",
                        fr = "Connecte-toi pour gagner des points contre l’IA aussi", ar = "سجّل الدخول لتكسب نقاطًا في معارك الذكاء الاصطناعي أيضًا",
                        de = "Melde dich an, um auch gegen die KI Punkte zu sammeln", pt = "Entre para ganhar pontos também contra a IA",
                        ru = "Войдите, чтобы получать очки и в боях с ИИ", ja = "ログインするとAI対戦でもポイントがもらえます"),
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ui.text.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        ui.profile?.let { p -> DailyCard(vm, p, ui.shopBusy) }
        ui.profile?.let { p -> InviteCard(vm, p, ui.shopBusy) }
        ui.info?.let {
            Text(it, color = Ui.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        val privacyRequired by AdManager.privacyOptionsRequired.collectAsState()
        if (privacyRequired) {
            val activity = LocalContext.current as Activity
            TextButton(onClick = { AdManager.showPrivacyOptions(activity) }) {
                Text(tr("🔒 광고 개인정보 설정", "🔒 Ad privacy settings", zh = "🔒 广告隐私设置", hi = "🔒 विज्ञापन गोपनीयता सेटिंग", es = "🔒 Privacidad de anuncios", fr = "🔒 Confidentialité des pubs", ar = "🔒 إعدادات خصوصية الإعلانات", de = "🔒 Werbe-Datenschutz", pt = "🔒 Privacidade de anúncios", ru = "🔒 Настройки рекламы", ja = "🔒 広告のプライバシー設定"), color = Ui.text.copy(alpha = 0.7f), fontSize = 13.sp)
            }
        }

        if (ui.leaderboard.isNotEmpty()) {
            SoftCard {
                Text(tr("🏆 랭킹", "🏆 Leaderboard", zh = "🏆 排行榜", hi = "🏆 लीडरबोर्ड", es = "🏆 Clasificación", fr = "🏆 Classement", ar = "🏆 لوحة المتصدرين", de = "🏆 Bestenliste", pt = "🏆 Ranking", ru = "🏆 Рейтинг", ja = "🏆 ランキング"), fontWeight = FontWeight.Bold, color = Ui.text)
                Text(
                    tr("🎁 매주 월요일 0시 TOP 100 보상 · 1위 💰1000 · 2위 700 · 3위 500 · 4~10위 300 · 11~100위 100",
                        "🎁 Every Monday 00:00 (KST), TOP 100 get 💰: #1 1000 · #2 700 · #3 500 · #4–10 300 · #11–100 100",
                        zh = "🎁 每周一 0 点（韩国时间）TOP 100 奖励 💰：第1名 1000 · 第2名 700 · 第3名 500 · 4–10名 300 · 11–100名 100",
                        es = "🎁 Cada lunes 00:00 (KST) el TOP 100 recibe 💰: #1 1000 · #2 700 · #3 500 · #4–10 300 · #11–100 100",
                        fr = "🎁 Chaque lundi 00h (KST), le TOP 100 reçoit 💰 : #1 1000 · #2 700 · #3 500 · #4–10 300 · #11–100 100",
                        de = "🎁 Jeden Montag 0 Uhr (KST) bekommt die TOP 100 💰: #1 1000 · #2 700 · #3 500 · #4–10 300 · #11–100 100",
                        pt = "🎁 Toda segunda 00h (KST) o TOP 100 ganha 💰: #1 1000 · #2 700 · #3 500 · #4–10 300 · #11–100 100",
                        ru = "🎁 Каждый понедельник 00:00 (KST) ТОП-100 получают 💰: #1 1000 · #2 700 · #3 500 · #4–10 300 · #11–100 100",
                        ja = "🎁 毎週月曜0時（韓国時間）TOP100に 💰：1位1000 · 2位700 · 3位500 · 4〜10位300 · 11〜100位100"),
                    fontSize = 11.sp, color = Ui.text.copy(alpha = 0.7f), lineHeight = 15.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    tr("🔄 1분마다 갱신 · 이름을 누르면 프로필 카드", "🔄 Updates every minute · tap a name for their card",
                        zh = "🔄 每分钟更新 · 点名字查看名片", es = "🔄 Se actualiza cada minuto · toca un nombre", fr = "🔄 Mis à jour chaque minute · touche un nom",
                        de = "🔄 Jede Minute aktualisiert · Namen antippen", pt = "🔄 Atualiza a cada minuto · toque num nome",
                        ru = "🔄 Обновляется каждую минуту · нажмите на имя", ja = "🔄 1分ごとに更新 · 名前をタップでカード"),
                    fontSize = 11.sp, color = Ui.text.copy(alpha = 0.55f),
                )
                Spacer(Modifier.height(6.dp))
                ui.leaderboard.forEachIndexed { i, p ->
                    val r = p.ranked(i + 1)
                    RankingRow(r, me = p.uid == ui.profile?.uid) { cardOf = r }
                }
                // TOP 100 밖이면 내 순위와 위아래 10명
                if (ui.myRank != null && ui.aroundMe.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        tr("📍 내 순위: ${ui.myRank}위", "📍 My rank: #${ui.myRank}", zh = "📍 我的排名：第 ${ui.myRank} 名", hi = "📍 मेरी रैंक: #${ui.myRank}", es = "📍 Mi puesto: #${ui.myRank}", fr = "📍 Mon rang : #${ui.myRank}", ar = "📍 ترتيبي: #${ui.myRank}", de = "📍 Mein Platz: #${ui.myRank}", pt = "📍 Minha posição: #${ui.myRank}", ru = "📍 Моё место: #${ui.myRank}", ja = "📍 自分の順位：${ui.myRank}位"),
                        fontWeight = FontWeight.Bold, color = Ui.text,
                    )
                    Spacer(Modifier.height(4.dp))
                    ui.aroundMe.forEach { row ->
                        val r = row.ranked()
                        RankingRow(r, me = row.uid == ui.profile?.uid) { cardOf = r }
                    }
                }
            }
        }
    }

    ui.welcome?.let { (kind, score) -> WelcomeDialog(kind, score, onDismiss = vm::dismissWelcome) }
    ui.profile?.weeklyReward?.takeIf { !it.seen }?.let { w ->
        AlertDialog(
            onDismissRequest = vm::ackWeeklyReward,
            title = { Text(tr("🏆 주간 랭킹 보상!", "🏆 Weekly ranking reward!", zh = "🏆 每周排行奖励！", hi = "🏆 साप्ताहिक रैंकिंग इनाम!", es = "🏆 ¡Premio semanal!", fr = "🏆 Récompense hebdo !", ar = "🏆 مكافأة الترتيب الأسبوعية!", de = "🏆 Wochenbelohnung!", pt = "🏆 Prêmio semanal!", ru = "🏆 Недельная награда!", ja = "🏆 週間ランキング報酬！"), fontWeight = FontWeight.Black) },
            text = {
                Text(
                    tr("지난주 ${w.rank}위! 💰 상점 포인트 ${w.coins}점을 드렸어요 🎉", "You were #${w.rank} last week! Here are 💰 ${w.coins} shop points 🎉",
                        zh = "上周第 ${w.rank} 名！获得 💰 ${w.coins} 商店积分 🎉", hi = "पिछले हफ़्ते #${w.rank}! 💰 ${w.coins} शॉप पॉइंट 🎉",
                        es = "¡Quedaste #${w.rank} la semana pasada! Toma 💰 ${w.coins} puntos 🎉", fr = "Tu étais #${w.rank} la semaine dernière ! 💰 ${w.coins} points 🎉",
                        ar = "كنت #${w.rank} الأسبوع الماضي! 💰 ${w.coins} نقطة 🎉", de = "Letzte Woche Platz ${w.rank}! 💰 ${w.coins} Shop-Punkte 🎉",
                        pt = "Você foi #${w.rank} na semana passada! 💰 ${w.coins} pontos 🎉", ru = "На прошлой неделе #${w.rank}! 💰 ${w.coins} очков 🎉",
                        ja = "先週は${w.rank}位！💰 ショップポイント ${w.coins} をプレゼント 🎉"),
                    fontSize = 16.sp,
                )
            },
            confirmButton = { TextButton(onClick = vm::ackWeeklyReward) { Text(tr("받았어요!", "Got it!", zh = "收到！", hi = "मिल गया!", es = "¡Genial!", fr = "Super !", ar = "تم!", de = "Danke!", pt = "Valeu!", ru = "Ура!", ja = "受け取った！"), fontWeight = FontWeight.Black) } },
        )
    }
    if (showRules) RulesDialog { showRules = false }
    if (showRanks) RanksDialog(ui.profile?.score ?: 0) { showRanks = false }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("정말 계정을 삭제할까요?", "Delete your account?", zh = "确定要删除账号吗？", hi = "क्या खाता हटाना है?", es = "¿Eliminar tu cuenta?", fr = "Supprimer ton compte ?", ar = "هل تريد حذف حسابك؟", de = "Konto wirklich löschen?", pt = "Excluir sua conta?", ru = "Удалить аккаунт?", ja = "アカウントを削除しますか？"), fontWeight = FontWeight.Black) },
            text = {
                Text(
                    tr(
                        "점수, 계급, 승패 기록, 부활물약, 동물 친구가 모두 사라지고 되돌릴 수 없어요.\n" +
                            "진행 중인 대전은 기권 처리돼요.\n\n같은 구글 계정으로 다시 로그인하면 1000점부터 새로 시작해요.",
                        "Your score, rank, record, potions and animal friends will be gone for good.\n" +
                            "Any game in progress counts as a forfeit.\n\nSigning in again with the same Google account starts fresh at 1000 pts.",
                        zh = "分数、等级、战绩、药水和动物伙伴都会永久消失。\n进行中的对战将判为弃权。\n\n用同一个 Google 账号重新登录会从 1000 分重新开始。",
                        hi = "आपका स्कोर, रैंक, रिकॉर्ड, पोशन और जानवर दोस्त हमेशा के लिए चले जाएँगे।\nचल रहा गेम हार माना जाएगा।\n\nउसी Google खाते से फिर साइन इन करने पर 1000 अंक से नई शुरुआत होगी।",
                        es = "Tu puntuación, rango, récord, pociones y amigos animales desaparecerán para siempre.\nLa partida en curso contará como abandono.\n\nSi vuelves a entrar con la misma cuenta de Google, empezarás de nuevo con 1000 pts.",
                        fr = "Ton score, ton rang, ton palmarès, tes potions et tes amis animaux disparaîtront pour toujours.\nUne partie en cours compte comme un abandon.\n\nEn te reconnectant avec le même compte Google, tu repars à 1000 pts.",
                        ar = "ستختفي نقاطك ورتبتك وسجلّك وجرعاتك وأصدقاؤك الحيوانات نهائيًا.\nأي مباراة جارية تُحتسب انسحابًا.\n\nإذا سجّلت الدخول مجددًا بنفس حساب Google ستبدأ من جديد بـ 1000 نقطة.",
                        de = "Punkte, Rang, Bilanz, Tränke und Tierfreunde sind dann für immer weg.\nEine laufende Partie zählt als Aufgabe.\n\nMeldest du dich mit demselben Google-Konto wieder an, startest du neu mit 1000 Pkt.",
                        pt = "Sua pontuação, rank, histórico, poções e amigos animais vão sumir para sempre.\nUma partida em andamento conta como desistência.\n\nSe entrar de novo com a mesma conta Google, você recomeça com 1000 pts.",
                        ru = "Счёт, ранг, статистика, зелья и друзья-зверята исчезнут навсегда.\nТекущая партия засчитается как поражение.\n\nПри новом входе с тем же аккаунтом Google вы начнёте заново с 1000 очк.",
                        ja = "スコア・ランク・戦績・復活薬・どうぶつの仲間がすべて消え、元に戻せません。\n対戦中のゲームは棄権になります。\n\n同じGoogleアカウントで再ログインすると1000点からやり直しです。",
                    ),
                    lineHeight = 21.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteAccount() }) {
                    Text(tr("삭제", "Delete", zh = "删除", hi = "हटाएँ", es = "Eliminar", fr = "Supprimer", ar = "حذف", de = "Löschen", pt = "Excluir", ru = "Удалить", ja = "削除"), color = Ui.danger, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("취소", "Cancel", zh = "取消", hi = "रद्द करें", es = "Cancelar", fr = "Annuler", ar = "إلغاء", de = "Abbrechen", pt = "Cancelar", ru = "Отмена", ja = "キャンセル")) } },
        )
    }
    if (showAdmin) AdminDialog(vm, onClose = { showAdmin = false })
    if (renaming) {
        var name by remember { mutableStateOf(ui.profile?.name ?: "") }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(tr("닉네임", "Nickname", zh = "昵称", hi = "निकनेम", es = "Apodo", fr = "Pseudo", ar = "اللقب", de = "Spitzname", pt = "Apelido", ru = "Ник", ja = "ニックネーム")) },
            text = {
                Column {
                    OutlinedTextField(name, { name = it.take(12) }, singleLine = true, label = { Text(tr("최대 12자", "Up to 12 characters", zh = "最多 12 个字符", hi = "अधिकतम 12 अक्षर", es = "Hasta 12 caracteres", fr = "12 caractères max.", ar = "حتى 12 حرفًا", de = "Bis zu 12 Zeichen", pt = "Até 12 caracteres", ru = "До 12 символов", ja = "12文字まで")) })
                    Spacer(Modifier.height(8.dp))
                    // 첫 변경은 무료, 그다음은 ✏️ 변경권
                    val p0 = ui.profile
                    Text(
                        when {
                            p0 == null || !p0.freeRenameUsed -> tr("🎁 첫 변경은 무료예요!", "🎁 Your first change is free!", zh = "🎁 第一次修改免费！", es = "🎁 ¡El primer cambio es gratis!",
                                fr = "🎁 Le premier changement est gratuit !", de = "🎁 Die erste Änderung ist gratis!", pt = "🎁 A primeira troca é grátis!", ru = "🎁 Первая смена бесплатно!", ja = "🎁 最初の変更は無料！")
                            p0.renameTickets > 0 -> tr("✏️ 변경권 1장을 써요 (보유 ${p0.renameTickets}장)", "✏️ Uses 1 rename ticket (you have ${p0.renameTickets})",
                                zh = "✏️ 使用 1 张改名券（拥有 ${p0.renameTickets} 张）", es = "✏️ Usa 1 cupón de cambio (tienes ${p0.renameTickets})", fr = "✏️ Utilise 1 ticket (tu en as ${p0.renameTickets})",
                                de = "✏️ Nutzt 1 Namensticket (du hast ${p0.renameTickets})", pt = "✏️ Usa 1 cupom de troca (você tem ${p0.renameTickets})", ru = "✏️ Тратится 1 талон (у вас ${p0.renameTickets})", ja = "✏️ 変更券を1枚使います（所持${p0.renameTickets}枚）")
                            else -> tr("✏️ 닉네임 변경권이 필요해요 · 상점에서 ${Shop.RENAME_PRICE}점", "✏️ You need a rename ticket · ${Shop.RENAME_PRICE} pts in the shop",
                                zh = "✏️ 需要改名券 · 商店 ${Shop.RENAME_PRICE} 分", es = "✏️ Necesitas un cupón de cambio · ${Shop.RENAME_PRICE} pts en la tienda",
                                fr = "✏️ Il faut un ticket de pseudo · ${Shop.RENAME_PRICE} pts en boutique", de = "✏️ Du brauchst ein Namensticket · ${Shop.RENAME_PRICE} Pkt im Shop",
                                pt = "✏️ Precisa de um cupom de troca · ${Shop.RENAME_PRICE} pts na loja", ru = "✏️ Нужен талон смены ника · ${Shop.RENAME_PRICE} очк. в магазине",
                                ja = "✏️ ニックネーム変更券が必要です · ショップで${Shop.RENAME_PRICE}点")
                        },
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = if (p0 != null && p0.freeRenameUsed && p0.renameTickets == 0) Ui.danger else Ui.accent,
                    )
                }
            },
            confirmButton = {
                val p0 = ui.profile
                val canRename = p0 == null || !p0.freeRenameUsed || p0.renameTickets > 0
                if (canRename) {
                    TextButton(onClick = { vm.rename(name); renaming = false }) { Text(tr("저장", "Save", zh = "保存", hi = "सेव करें", es = "Guardar", fr = "Enregistrer", ar = "حفظ", de = "Speichern", pt = "Salvar", ru = "Сохранить", ja = "保存")) }
                } else {
                    TextButton(onClick = { renaming = false; vm.openShop() }) { Text(tr("🛒 상점 가기", "🛒 Go to shop", zh = "🛒 去商店", es = "🛒 Ir a la tienda", fr = "🛒 Aller à la boutique", de = "🛒 Zum Shop", pt = "🛒 Ir à loja", ru = "🛒 В магазин", ja = "🛒 ショップへ"), fontWeight = FontWeight.Bold) }
                }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(tr("취소", "Cancel", zh = "取消", hi = "रद्द करें", es = "Cancelar", fr = "Annuler", ar = "إلغاء", de = "Abbrechen", pt = "Cancelar", ru = "Отмена", ja = "キャンセル")) } },
        )
    }
}

@Composable
private fun TitleBanner() {
    val t = rememberInfiniteTransition(label = "title")
    val hop by t.animateFloat(0f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "hop")
    Row(verticalAlignment = Alignment.Bottom) {
        Critter(Side.CAT, Modifier.size(64.dp).offset(y = (-hop * 10).dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 6.dp)) {
            Text("Bubble", fontSize = 44.sp, fontWeight = FontWeight.Black, color = Ui.primaryDark)
            Text(tr("냥냥 vs 햄햄 방울 대전", "Cats vs Hamsters bubble battle", zh = "猫咪 vs 仓鼠 泡泡大战", hi = "बिल्ली बनाम हैम्स्टर बबल बैटल", es = "Gatos vs Hámsteres: batalla de burbujas", fr = "Chats contre hamsters : la bataille des bulles", ar = "قطط ضد هامستر: معركة الفقاعات", de = "Katzen gegen Hamster: Blasen-Duell", pt = "Gatos vs Hamsters: batalha de bolhas", ru = "Котики против хомяков: битва пузырей", ja = "ネコ vs ハムスター バブル対決"), fontSize = 14.sp, color = Ui.text)
        }
        Critter(Side.HAMSTER, Modifier.size(64.dp).offset(y = (-(1 - hop) * 10).dp))
    }
}

@Composable
private fun SoftCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Ui.card),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun BigButton(text: String, sub: String, enabled: Boolean, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "big")
    val s by t.animateFloat(1f, 1.04f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "s")
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Ui.primary),
        modifier = Modifier.fillMaxWidth().height(76.dp).scale(if (enabled) s else 1f),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(sub, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RulesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("알겠어요!", "Got it!", zh = "知道了！", hi = "समझ गया!", es = "¡Entendido!", fr = "Compris !", ar = "فهمت!", de = "Alles klar!", pt = "Entendi!", ru = "Понятно!", ja = "わかった！")) } },
        title = { Text(tr("게임 규칙", "How to play", zh = "游戏规则", hi = "कैसे खेलें", es = "Cómo jugar", fr = "Règles du jeu", ar = "طريقة اللعب", de = "Spielregeln", pt = "Como jogar", ru = "Как играть", ja = "遊び方"), fontWeight = FontWeight.Black, fontSize = 24.sp) },
        text = {
            Text(
                tr(
"• 고양이와 햄스터가 각자 방울(세트) 2개에 1마리씩 들고 시작해요. 고양이가 먼저!\n\n",
"• Cats and hamsters each start with 2 bubbles (sets) holding 1 critter. Cats go first!\n\n",
zh = "• 猫咪和仓鼠各有 2 个泡泡（组），每个里有 1 只。猫咪先手！\n\n", hi = "• बिल्लियाँ और हैम्स्टर 2-2 बबल (सेट) से शुरू करते हैं, हर एक में 1 जीव। बिल्लियाँ पहले!\n\n", es = "• Gatos y hámsteres empiezan con 2 burbujas (grupos) de 1 bichito cada una. ¡Los gatos van primero!\n\n", fr = "• Chats et hamsters commencent avec 2 bulles (groupes) d’1 bestiole chacune. Les chats commencent !\n\n", ar = "• يبدأ كل من القطط والهامستر بفقاعتين (مجموعتين) في كل منهما مخلوق واحد. القطط تبدأ أولًا!\n\n", de = "• Katzen und Hamster starten mit je 2 Blasen (Gruppen) mit 1 Tierchen. Katzen beginnen!\n\n", pt = "• Gatos e hamsters começam com 2 bolhas (grupos) com 1 bichinho cada. Os gatos começam!\n\n", ru = "• Котики и хомяки начинают с 2 пузырями (группами) по 1 зверьку. Котики ходят первыми!\n\n", ja = "• ネコとハムスターはそれぞれ1匹入りのバブル（セット）2つから始めます。ネコが先攻！\n\n",
) +
                    tr(
"• 내 차례에는 내 방울 하나를 골라요. 그 방울의 마리 수만큼\n",
"• On your turn, pick one of your bubbles. Add its critter count\n",
zh = "• 轮到你时，选一个自己的泡泡，把它的数量\n", hi = "• अपनी बारी में अपना एक बबल चुनें। उसकी गिनती जोड़ें\n", es = "• En tu turno, elige una de tus burbujas. Suma su número\n", fr = "• À ton tour, choisis une de tes bulles. Ajoute son nombre\n", ar = "• في دورك، اختر إحدى فقاعاتك وأضِف عددها\n", de = "• Wähle in deinem Zug eine deiner Blasen. Addiere ihre Anzahl\n", pt = "• Na sua vez, escolha uma de suas bolhas. Some o número dela\n", ru = "• В свой ход выберите свой пузырь. Прибавьте его число\n", ja = "• 自分の番に自分のバブルを1つ選び、その数を\n",
) +
                    tr("   ⚔️ 상대 방울에 더하거나 (공격)\n", "   ⚔️ to an opponent bubble (attack)\n", zh = "   ⚔️ 加到对手的泡泡上（攻击）\n", hi = "   ⚔️ विरोधी के बबल में (हमला)\n", es = "   ⚔️ a una burbuja rival (ataque)\n", fr = "   ⚔️ à une bulle adverse (attaque)\n", ar = "   ⚔️ إلى فقاعة الخصم (هجوم)\n", de = "   ⚔️ zu einer gegnerischen Blase (Angriff)\n", pt = "   ⚔️ a uma bolha rival (ataque)\n", ru = "   ⚔️ к пузырю соперника (атака)\n", ja = "   ⚔️ 相手のバブルに足す（攻撃）\n") +
                    tr(
"   💪 내 다른 방울에 더할 수 있어요 (강화)\n\n",
"   💪 or to your other bubble (boost)\n\n",
zh = "   💪 或加到自己的另一个泡泡上（强化）\n\n", hi = "   💪 या अपने दूसरे बबल में (बूस्ट)\n\n", es = "   💪 o a tu otra burbuja (refuerzo)\n\n", fr = "   💪 ou à ton autre bulle (renfort)\n\n", ar = "   💪 أو إلى فقاعتك الأخرى (تقوية)\n\n", de = "   💪 oder zu deiner anderen Blase (Stärkung)\n\n", pt = "   💪 ou à sua outra bolha (reforço)\n\n", ru = "   💪 или к своему другому пузырю (усиление)\n\n", ja = "   💪 または自分のもう1つのバブルに足す（強化）\n\n",
) +
                    tr(
"• 방울이 5마리 이상이 되면 💥 펑! 터져서 0마리가 돼요.\n\n",
"• A bubble with 5 or more critters goes 💥 POP! and drops to 0.\n\n",
zh = "• 泡泡达到 5 只以上就会 💥 啪！破掉，变成 0。\n\n", hi = "• 5 या ज़्यादा होते ही बबल 💥 फट! जाता है और 0 हो जाता है।\n\n", es = "• Una burbuja con 5 o más hace 💥 ¡PUM! y vuelve a 0.\n\n", fr = "• Une bulle à 5 ou plus fait 💥 POP ! et retombe à 0.\n\n", ar = "• الفقاعة التي يصل عددها إلى 5 أو أكثر تنفجر 💥 بوب! وتصبح 0.\n\n", de = "• Eine Blase mit 5 oder mehr macht 💥 PLOPP! und fällt auf 0.\n\n", pt = "• Uma bolha com 5 ou mais faz 💥 POP! e volta a 0.\n\n", ru = "• Пузырь с 5 и больше — 💥 ХЛОП! и становится 0.\n\n", ja = "• 5匹以上になったバブルは 💥 パン！と割れて0になります。\n\n",
) +
                    tr(
"• 0마리 방울은 공격도 강화도 못 해요. 살리는 방법은 분할뿐!\n",
"• An empty bubble can’t be attacked or boosted. Only a split revives it!\n",
zh = "• 空泡泡不能被攻击或强化，只有分裂能救回它！\n", hi = "• खाली बबल पर हमला या बूस्ट नहीं हो सकता। उसे सिर्फ़ स्प्लिट ही बचा सकता है!\n", es = "• Una burbuja vacía no se puede atacar ni reforzar. ¡Solo una división la revive!\n", fr = "• Une bulle vide ne peut être ni attaquée ni renforcée. Seule une division la ranime !\n", ar = "• لا يمكن مهاجمة الفقاعة الفارغة أو تقويتها. وحده التقسيم يعيدها!\n", de = "• Eine leere Blase kann weder angegriffen noch gestärkt werden. Nur Teilen belebt sie wieder!\n", pt = "• Uma bolha vazia não pode ser atacada nem reforçada. Só uma divisão a revive!\n", ru = "• Пустой пузырь нельзя атаковать или усилить. Оживить его может только деление!\n", ja = "• 空のバブルは攻撃も強化もできません。復活は分割だけ！\n",
) +
                    tr(
"   ✂️ 분할: 한 방울이 짝수(2·4마리)이고 다른 방울이 0일 때 반씩 나눠요.\n",
"   ✂️ Split: if one bubble has an even count (2 or 4) and the other is empty, halve it.\n",
zh = "   ✂️ 分裂：一个泡泡是偶数（2 或 4）且另一个为空时，平分成两半。\n", hi = "   ✂️ स्प्लिट: अगर एक बबल में सम संख्या (2 या 4) हो और दूसरा खाली हो, तो आधा-आधा बाँटें।\n", es = "   ✂️ División: si una burbuja tiene un número par (2 o 4) y la otra está vacía, pártela a la mitad.\n", fr = "   ✂️ Division : si une bulle a un nombre pair (2 ou 4) et que l’autre est vide, coupe-la en deux.\n", ar = "   ✂️ التقسيم: إذا كان في فقاعة عدد زوجي (2 أو 4) والأخرى فارغة، اقسمها نصفين.\n", de = "   ✂️ Teilen: Hat eine Blase eine gerade Zahl (2 oder 4) und die andere ist leer, halbiere sie.\n", pt = "   ✂️ Divisão: se uma bolha tem número par (2 ou 4) e a outra está vazia, divida ao meio.\n", ru = "   ✂️ Деление: если в одном пузыре чётное число (2 или 4), а другой пуст — разделите пополам.\n", ja = "   ✂️ 分割：片方が偶数（2か4）でもう片方が空なら、半分に分けます。\n",
) +
                    tr(
"   ✂️ 분할은 한 게임에 한 사람당 최대 ${GameLogic.MAX_SPLITS}번까지!\n\n",
"   ✂️ Up to ${GameLogic.MAX_SPLITS} splits per player per game!\n\n",
zh = "   ✂️ 每人每局最多分裂 ${GameLogic.MAX_SPLITS} 次！\n\n", hi = "   ✂️ हर खिलाड़ी हर गेम में अधिकतम ${GameLogic.MAX_SPLITS} बार स्प्लिट!\n\n", es = "   ✂️ ¡Hasta ${GameLogic.MAX_SPLITS} divisiones por jugador y partida!\n\n", fr = "   ✂️ Jusqu’à ${GameLogic.MAX_SPLITS} divisions par joueur et par partie !\n\n", ar = "   ✂️ حتى ${GameLogic.MAX_SPLITS} تقسيمات لكل لاعب في كل مباراة!\n\n", de = "   ✂️ Bis zu ${GameLogic.MAX_SPLITS} Teilungen pro Spieler und Partie!\n\n", pt = "   ✂️ Até ${GameLogic.MAX_SPLITS} divisões por jogador por partida!\n\n", ru = "   ✂️ До ${GameLogic.MAX_SPLITS} делений на игрока за партию!\n\n", ja = "   ✂️ 分割は1人1ゲーム最大${GameLogic.MAX_SPLITS}回まで！\n\n",
) +
                    tr(
"• 내 방울 2개가 모두 터지면 져요.\n\n",
"• Lose both bubbles and you lose.\n\n",
zh = "• 两个泡泡都破掉就输了。\n\n", hi = "• दोनों बबल फट गए तो आप हार जाते हैं।\n\n", es = "• Si pierdes las dos burbujas, pierdes.\n\n", fr = "• Perds tes deux bulles et tu as perdu.\n\n", ar = "• إذا خسرت فقاعتيك تخسر المباراة.\n\n", de = "• Verlierst du beide Blasen, hast du verloren.\n\n", pt = "• Perdeu as duas bolhas, perdeu o jogo.\n\n", ru = "• Потеряли оба пузыря — проиграли.\n\n", ja = "• 2つのバブルが両方割れたら負けです。\n\n",
) +
                    tr(
"• 💖 부활물약(상점)이 있으면 터진 방울을 1마리로 살릴 수 있어요. 한 판에 최대 3번 (랜덤 매칭 1번)!\n\n",
"• 💖 Revive potions (shop) bring a popped bubble back with 1 critter. Up to 3 per game (1 in random matches)!\n\n",
zh = "• 💖 复活药水（商店）能让破掉的泡泡带着 1 只复活。每局最多 3 次（随机匹配 1 次）！\n\n", hi = "• 💖 रिवाइव पोशन (दुकान) फटे बबल को 1 जीव के साथ वापस लाता है। हर गेम में अधिकतम 3 (रैंडम मैच में 1)!\n\n", es = "• 💖 Las pociones de revivir (tienda) devuelven una burbuja reventada con 1 bichito. ¡Hasta 3 por partida (1 en aleatorias)!\n\n", fr = "• 💖 Les potions de résurrection (boutique) font revenir une bulle éclatée avec 1 bestiole. Jusqu’à 3 par partie (1 en aléatoire) !\n\n", ar = "• 💖 جرعات الإحياء (المتجر) تعيد الفقاعة المنفجرة بمخلوق واحد. حتى 3 في كل مباراة (1 في العشوائية)!\n\n", de = "• 💖 Wiederbelebungstränke (Shop) holen eine geplatzte Blase mit 1 Tierchen zurück. Bis zu 3 pro Partie (1 im Zufallsmatch)!\n\n", pt = "• 💖 Poções de reviver (loja) trazem de volta uma bolha estourada com 1 bichinho. Até 3 por partida (1 em aleatórias)!\n\n", ru = "• 💖 Зелья оживления (магазин) возвращают лопнувший пузырь с 1 зверьком. До 3 за партию (1 в случайном)!\n\n", ja = "• 💖 復活薬（ショップ）で割れたバブルを1匹で復活できます。1ゲーム最大3回（ランダムマッチは1回）！\n\n",
) +
                    tr(
"• 온라인 대전은 한 턴에 ${OnlineRepository.TURN_LIMIT_MS / 1000}초! 시간을 넘기면 패배예요.\n",
"• Online turns last ${OnlineRepository.TURN_LIMIT_MS / 1000}s. Run out of time and you lose.\n",
zh = "• 在线对战每回合 ${OnlineRepository.TURN_LIMIT_MS / 1000} 秒，超时即判负。\n", hi = "• ऑनलाइन हर चाल ${OnlineRepository.TURN_LIMIT_MS / 1000} सेकंड। समय खत्म तो हार।\n", es = "• En línea cada turno dura ${OnlineRepository.TURN_LIMIT_MS / 1000} s. Si se acaba el tiempo, pierdes.\n", fr = "• En ligne, chaque tour dure ${OnlineRepository.TURN_LIMIT_MS / 1000} s. Temps écoulé = défaite.\n", ar = "• مدة الدور عبر الإنترنت ${OnlineRepository.TURN_LIMIT_MS / 1000} ثانية. إذا انتهى الوقت تخسر.\n", de = "• Online dauert ein Zug ${OnlineRepository.TURN_LIMIT_MS / 1000} s. Zeit abgelaufen = verloren.\n", pt = "• Online, cada turno dura ${OnlineRepository.TURN_LIMIT_MS / 1000} s. Se o tempo acabar, você perde.\n", ru = "• Онлайн ход длится ${OnlineRepository.TURN_LIMIT_MS / 1000} с. Время вышло — поражение.\n", ja = "• オンラインの1ターンは${OnlineRepository.TURN_LIMIT_MS / 1000}秒。時間切れで負けです。\n",
) +
                    tr(
                    "   상대가 양·강아지·기린을 썼다면 내 턴이 15·12·8초로 줄어요.\n" +
                        "• AI 대전의 내 턴: 쉬움 20초 · 보통 15초 · 어려움 8초. 시간을 넘기면 패배!\n\n",
                    "   If your opponent uses a sheep, puppy or giraffe, your turn drops to 15, 12 or 8s.\n" +
                        "• Your turn vs AI: Easy 20s · Normal 15s · Hard 8s. Run out of time and you lose!\n\n",
                    zh = "   如果对手使用了绵羊、小狗或长颈鹿，你的回合会缩短为 15、12 或 8 秒。\n• AI 对战中你的回合：简单 20 秒 · 普通 15 秒 · 困难 8 秒。超时判负！\n\n",
                    hi = "   अगर विरोधी ने भेड़, पिल्ला या जिराफ़ इस्तेमाल किया, तो आपकी बारी 15, 12 या 8 सेकंड की होगी।\n• AI के खिलाफ आपकी बारी: आसान 20से · सामान्य 15से · कठिन 8से। समय खत्म तो हार!\n\n",
                    es = "   Si tu rival usa una oveja, un perrito o una jirafa, tu turno baja a 15, 12 o 8 s.\n• Tu turno contra la IA: Fácil 20 s · Normal 15 s · Difícil 8 s. ¡Si se acaba el tiempo, pierdes!\n\n",
                    fr = "   Si ton adversaire utilise un mouton, un chiot ou une girafe, ton tour passe à 15, 12 ou 8 s.\n• Ton tour contre l’IA : Facile 20 s · Normal 15 s · Difficile 8 s. Temps écoulé = défaite !\n\n",
                    ar = "   إذا استخدم خصمك خروفًا أو جروًا أو زرافة، يقل دورك إلى 15 أو 12 أو 8 ثوانٍ.\n• دورك ضد الذكاء الاصطناعي: سهل 20ث · عادي 15ث · صعب 8ث. إذا انتهى الوقت تخسر!\n\n",
                    de = "   Nutzt dein Gegner Schaf, Welpe oder Giraffe, sinkt dein Zug auf 15, 12 oder 8 s.\n• Dein Zug gegen die KI: Leicht 20 s · Normal 15 s · Schwer 8 s. Zeit abgelaufen = verloren!\n\n",
                    pt = "   Se o rival usar ovelha, cachorrinho ou girafa, seu turno cai para 15, 12 ou 8 s.\n• Seu turno contra a IA: Fácil 20 s · Normal 15 s · Difícil 8 s. Acabou o tempo, você perde!\n\n",
                    ru = "   Если соперник использует овечку, щенка или жирафа, ваш ход сокращается до 15, 12 или 8 с.\n• Ваш ход против ИИ: Легко 20 с · Нормально 15 с · Сложно 8 с. Время вышло — поражение!\n\n",
                    ja = "   相手がヒツジ・子イヌ・キリンを使うと、自分のターンが15・12・8秒になります。\n• AI対戦の自分のターン：かんたん20秒 · ふつう15秒 · むずかしい8秒。時間切れで負け！\n\n",
                ) +
                    tr(
                        "• 계급: 1000점 이하 흙 → 구리(1001) → 은(1401) → 금(2001) → 루비(4001) → 사파이어(7001) → 다이아(10001) → 핑크다이아(50001)",
                        "• Ranks: Dirt (to 1000) → Copper (1001) → Silver (1401) → Gold (2001) → Ruby (4001) → Sapphire (7001) → Diamond (10001) → Pink Diamond (50001)",
                        zh = "• 等级：泥土（≤1000）→ 铜（1001）→ 银（1401）→ 金（2001）→ 红宝石（4001）→ 蓝宝石（7001）→ 钻石（10001）→ 粉钻（50001）",
                        hi = "• रैंक: मिट्टी (1000 तक) → कॉपर (1001) → सिल्वर (1401) → गोल्ड (2001) → रूबी (4001) → नीलम (7001) → डायमंड (10001) → पिंक डायमंड (50001)",
                        es = "• Rangos: Tierra (hasta 1000) → Cobre (1001) → Plata (1401) → Oro (2001) → Rubí (4001) → Zafiro (7001) → Diamante (10001) → Diamante rosa (50001)",
                        fr = "• Rangs : Terre (jusqu’à 1000) → Cuivre (1001) → Argent (1401) → Or (2001) → Rubis (4001) → Saphir (7001) → Diamant (10001) → Diamant rose (50001)",
                        ar = "• الرتب: تراب (حتى 1000) ← نحاس (1001) ← فضة (1401) ← ذهب (2001) ← ياقوت أحمر (4001) ← ياقوت أزرق (7001) ← ماس (10001) ← ماس وردي (50001)",
                        de = "• Ränge: Erde (bis 1000) → Kupfer (1001) → Silber (1401) → Gold (2001) → Rubin (4001) → Saphir (7001) → Diamant (10001) → Pinker Diamant (50001)",
                        pt = "• Ranks: Terra (até 1000) → Cobre (1001) → Prata (1401) → Ouro (2001) → Rubi (4001) → Safira (7001) → Diamante (10001) → Diamante rosa (50001)",
                        ru = "• Ранги: Земля (до 1000) → Медь (1001) → Серебро (1401) → Золото (2001) → Рубин (4001) → Сапфир (7001) → Алмаз (10001) → Розовый алмаз (50001)",
                        ja = "• ランク：土（〜1000）→ 銅（1001）→ 銀（1401）→ 金（2001）→ ルビー（4001）→ サファイア（7001）→ ダイヤ（10001）→ ピンクダイヤ（50001）",
                    ),
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ui.text,
            )
        },
    )
}

// ======================= 친구 대전 기다리기 =======================

@Composable
private fun FriendWaitScreen(code: String?, startedAt: Long, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val left = ((OnlineRepository.FRIEND_WAIT_MS - (now - startedAt)) / 1000).coerceAtLeast(0)
    val t = rememberInfiniteTransition(label = "friend")
    val swing by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "swing")

    fun shareText(c: String) = tr(
        "Bubble 냥햄대전에서 나랑 한 판 붙자! ⚔️🐱🐹\n대전 코드: $c\n${Invite.battleLink(c)}",
        "Battle me in Bubble: Cats vs Hamsters! ⚔️🐱🐹\nBattle code: $c\n${Invite.battleLink(c)}",
        zh = "来 Bubble 猫咪大战仓鼠和我对战吧！⚔️🐱🐹\n对战码：$c\n${Invite.battleLink(c)}",
        es = "¡Juega una partida conmigo en Bubble! ⚔️🐱🐹\nCódigo: $c\n${Invite.battleLink(c)}",
        fr = "Viens m’affronter dans Bubble ! ⚔️🐱🐹\nCode : $c\n${Invite.battleLink(c)}",
        de = "Spiel eine Runde Bubble gegen mich! ⚔️🐱🐹\nCode: $c\n${Invite.battleLink(c)}",
        pt = "Jogue uma partida de Bubble comigo! ⚔️🐱🐹\nCódigo: $c\n${Invite.battleLink(c)}",
        ru = "Сразись со мной в Bubble! ⚔️🐱🐹\nКод боя: $c\n${Invite.battleLink(c)}",
        ja = "Bubble で一緒に対戦しよう！⚔️🐱🐹\n対戦コード：$c\n${Invite.battleLink(c)}",
    )

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Critter(Side.CAT, Modifier.size(96.dp).offset(x = (swing * 10).dp))
            Text("👫", fontSize = 34.sp, modifier = Modifier.padding(horizontal = 10.dp))
            Critter(Side.HAMSTER, Modifier.size(96.dp).offset(x = (-swing * 10).dp))
        }
        Spacer(Modifier.height(20.dp))
        if (code == null) {
            CircularProgressIndicator(color = Ui.accent)
            Spacer(Modifier.height(10.dp))
            Text(tr("대전방을 만드는 중…", "Creating a room…", zh = "正在创建房间…", es = "Creando sala…", fr = "Création de la salle…", de = "Raum wird erstellt…", pt = "Criando sala…", ru = "Создаём комнату…", ja = "ルームを作成中…"), color = Ui.text)
        } else {
            Text(tr("친구에게 링크를 보내 주세요", "Send this link to a friend", zh = "把链接发给朋友", es = "Envía este enlace a un amigo", fr = "Envoie ce lien à un ami", de = "Schick diesen Link an einen Freund", pt = "Envie este link para um amigo", ru = "Отправьте ссылку другу", ja = "友だちにリンクを送ってください"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ui.text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                code, fontSize = 32.sp, fontWeight = FontWeight.Black, color = Ui.primaryDark, letterSpacing = 4.sp,
                modifier = Modifier.background(Color.White, RoundedCornerShape(14.dp)).padding(horizontal = 18.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, shareText(code))
                    }
                    context.startActivity(android.content.Intent.createChooser(send, null))
                },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ui.accent),
            ) { Text(tr("💬 링크 보내기", "💬 Send link", zh = "💬 发送链接", es = "💬 Enviar enlace", fr = "💬 Envoyer le lien", de = "💬 Link senden", pt = "💬 Enviar link", ru = "💬 Отправить ссылку", ja = "💬 リンクを送る"), fontWeight = FontWeight.Bold) }
            TextButton(onClick = {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(Invite.battleLink(code)))
                copied = true
            }) {
                Text(
                    if (copied) tr("✅ 링크를 복사했어요", "✅ Link copied", zh = "✅ 链接已复制", hi = "✅ लिंक कॉपी हो गया", es = "✅ Enlace copiado", fr = "✅ Lien copié", ar = "✅ تم نسخ الرابط", de = "✅ Link kopiert", pt = "✅ Link copiado", ru = "✅ Ссылка скопирована", ja = "✅ リンクをコピーしました")
                    else tr("🔗 링크 복사", "🔗 Copy link", zh = "🔗 复制链接", es = "🔗 Copiar enlace", fr = "🔗 Copier le lien", de = "🔗 Link kopieren", pt = "🔗 Copiar link", ru = "🔗 Скопировать ссылку", ja = "🔗 リンクをコピー"),
                    color = Ui.accent, fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(tr("친구를 기다리는 중", "Waiting for your friend", zh = "正在等待朋友", es = "Esperando a tu amigo", fr = "En attente de ton ami", de = "Warte auf deinen Freund", pt = "Esperando seu amigo", ru = "Ждём друга", ja = "友だちを待っています") + ".".repeat(((now / 1000) % 4).toInt()) + "  %d:%02d".format(left / 60, left % 60), color = Ui.text.copy(alpha = 0.75f))
            Spacer(Modifier.height(8.dp))
            Text(
                tr(
                    "승리 +${OnlineRepository.FRIEND_WIN_POINTS}점 · 패배 -${OnlineRepository.FRIEND_LOSE_POINTS}점\n같은 친구와는 하루 ${OnlineRepository.FRIEND_RATED_PER_DAY}판까지 점수가 걸려요",
                    "Win +${OnlineRepository.FRIEND_WIN_POINTS} · Lose -${OnlineRepository.FRIEND_LOSE_POINTS}\nOnly ${OnlineRepository.FRIEND_RATED_PER_DAY} scored games per friend per day",
                    zh = "胜 +${OnlineRepository.FRIEND_WIN_POINTS} · 负 -${OnlineRepository.FRIEND_LOSE_POINTS}\n同一好友每天最多 ${OnlineRepository.FRIEND_RATED_PER_DAY} 局计分",
                    es = "Gana +${OnlineRepository.FRIEND_WIN_POINTS} · Pierde -${OnlineRepository.FRIEND_LOSE_POINTS}\nSolo ${OnlineRepository.FRIEND_RATED_PER_DAY} partidas con puntos por amigo al día",
                    fr = "Victoire +${OnlineRepository.FRIEND_WIN_POINTS} · Défaite -${OnlineRepository.FRIEND_LOSE_POINTS}\n${OnlineRepository.FRIEND_RATED_PER_DAY} parties notées max. par ami et par jour",
                    de = "Sieg +${OnlineRepository.FRIEND_WIN_POINTS} · Niederlage -${OnlineRepository.FRIEND_LOSE_POINTS}\nNur ${OnlineRepository.FRIEND_RATED_PER_DAY} gewertete Partien pro Freund und Tag",
                    pt = "Vitória +${OnlineRepository.FRIEND_WIN_POINTS} · Derrota -${OnlineRepository.FRIEND_LOSE_POINTS}\nSó ${OnlineRepository.FRIEND_RATED_PER_DAY} partidas valendo pontos por amigo por dia",
                    ru = "Победа +${OnlineRepository.FRIEND_WIN_POINTS} · Поражение -${OnlineRepository.FRIEND_LOSE_POINTS}\nНе больше ${OnlineRepository.FRIEND_RATED_PER_DAY} игр на очки с одним другом в день",
                    ja = "勝ち +${OnlineRepository.FRIEND_WIN_POINTS}点 · 負け -${OnlineRepository.FRIEND_LOSE_POINTS}点\n同じ友だちとは1日${OnlineRepository.FRIEND_RATED_PER_DAY}戦まで点数がかかります",
                ),
                fontSize = 13.sp, color = Ui.accent, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(28.dp))
        OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(20.dp)) { Text(tr("취소", "Cancel", zh = "取消", hi = "रद्द करें", es = "Cancelar", fr = "Annuler", ar = "إلغاء", de = "Abbrechen", pt = "Cancelar", ru = "Отмена", ja = "キャンセル"), color = Ui.text) }
    }
}

// ======================= 매칭 중 =======================

@Composable
private fun MatchingScreen(startedAt: Long, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(500); now = System.currentTimeMillis() } }
    val sec = ((now - startedAt) / 1000).toInt()
    val t = rememberInfiniteTransition(label = "match")
    val swing by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "swing")

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Critter(Side.CAT, Modifier.size(110.dp).offset(x = (swing * 14).dp, y = (-kotlin.math.abs(swing) * 12).dp))
            Text("VS", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Ui.accent, modifier = Modifier.padding(horizontal = 12.dp))
            Critter(Side.HAMSTER, Modifier.size(110.dp).offset(x = (-swing * 14).dp, y = (-kotlin.math.abs(swing) * 12).dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(tr("상대를 찾고 있어요", "Looking for an opponent", zh = "正在寻找对手", hi = "विरोधी ढूँढ रहे हैं", es = "Buscando rival", fr = "Recherche d’un adversaire", ar = "جارٍ البحث عن خصم", de = "Suche Gegner", pt = "Procurando um rival", ru = "Ищем соперника", ja = "相手を探しています") + ".".repeat(sec % 4), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ui.text)
        Spacer(Modifier.height(6.dp))
        Text("%d:%02d".format(sec / 60, sec % 60), fontSize = 16.sp, color = Ui.text.copy(alpha = 0.7f))
        val left = ((OnlineRepository.MATCH_TIMEOUT_MS / 1000) - sec).coerceAtLeast(0)
        Spacer(Modifier.height(10.dp))
        Text(
            tr("${left}초 안에 상대가 없으면 🤖 AI(어려움)와 대결해요", "No opponent in ${left}s? You'll face 🤖 AI (Hard)",
                zh = "${left} 秒内没有对手，将与 🤖 AI（困难）对战", hi = "${left} सेकंड में विरोधी नहीं मिला तो 🤖 AI (कठिन) से मुकाबला",
                es = "¿Sin rival en ${left} s? Jugarás contra 🤖 IA (Difícil)", fr = "Pas d’adversaire dans ${left} s ? Tu affronteras 🤖 l’IA (Difficile)",
                ar = "إذا لم يوجد خصم خلال ${left} ث ستواجه 🤖 الذكاء الاصطناعي (صعب)", de = "Kein Gegner in ${left} s? Dann spielst du gegen 🤖 KI (Schwer)",
                pt = "Sem rival em ${left} s? Você enfrentará 🤖 IA (Difícil)", ru = "Нет соперника за ${left} с? Сыграете с 🤖 ИИ (Сложно)",
                ja = "${left}秒以内に相手がいなければ 🤖 AI（むずかしい）と対戦します"),
            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ui.accent, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(20.dp)) { Text(tr("매칭 취소", "Cancel", zh = "取消", hi = "रद्द करें", es = "Cancelar", fr = "Annuler", ar = "إلغاء", de = "Abbrechen", pt = "Cancelar", ru = "Отмена", ja = "キャンセル"), color = Ui.text) }
    }
}

// ======================= 게임 =======================

@Composable
private fun GameScreen(vm: AppViewModel) {
    val live by vm.game.collectAsState()
    // 화면에 보여주는 판: 방울이 날아가 부딪힐 때까지 바뀐 판을 잠깐 늦게 보여줌
    var g by remember { mutableStateOf(live) }
    val shots = remember { mutableStateListOf<FlyingShot>() }
    var landAt by remember { mutableLongStateOf(0L) }
    val centers = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Offset>() }
    var layerOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var firedShot by remember { mutableStateOf(live.lastShot?.id) }
    // 💰 복냥이 동전
    val coins = remember { mutableStateListOf<CoinPop>() }
    var firedLucky by remember { mutableStateOf(live.lucky?.id) }
    // 💖 부활 효과
    val revives = remember { mutableStateListOf<ReviveBurst>() }
    var firedRevive by remember { mutableStateOf(live.lastShot?.takeIf { it.kind == ShotKind.REVIVE }?.id) }

    /** [side]의 [from] 방울이 [to] 방울로 날아가는 효과 */
    fun fire(side: Side, from: Int, toOpp: Boolean, to: Int, pre: GameUi, split: Boolean = false) {
        val targetSide = if (toOpp) side.opponent else side
        val a = centers["$side$from"] ?: return
        val b = centers["$targetSide$to"] ?: return
        val count = pre.board.sets(side).getOrElse(from) { 0 }
        // 분할은 절반이 옆 방울로 날아감
        val power = if (split) count / 2 else count
        if (power <= 0) return
        val targetCount = pre.board.sets(targetSide).getOrElse(to) { 0 }
        shots += FlyingShot(
            key = System.nanoTime(),
            from = a - layerOrigin, to = b - layerOrigin,
            side = side, skin = if (side == pre.mySide) pre.mySkin else pre.opponentSkin,
            power = power, attack = toOpp, boom = !split && targetCount + power >= GameLogic.EXPLODE_AT, split = split,
        )
        landAt = System.currentTimeMillis() + SHOT_FLY_MS
        com.bubble.game.audio.Music.sfx(
            if (split) com.bubble.game.audio.Music.splitSfx(if (side == pre.mySide) pre.mySkin else pre.opponentSkin)
            else com.bubble.game.audio.Music.Sfx.SHOOT,
        )
    }

    LaunchedEffect(live) {
        val s = live.lastShot
        if (s == null) {
            firedShot = null
        } else if (s.id != firedShot) {
            firedShot = s.id
            when (s.kind) {
                // 내 더하기는 누를 때 이미 날렸으니 상대 수만
                ShotKind.ADD -> if (s.side != live.mySide) fire(s.side, s.from, s.toOpponent, s.to, g)
                // 분할: 가위로 싹둑 → 절반이 옆 방울로 날아감 (내 수·상대 수 모두)
                ShotKind.SPLIT -> fire(s.side, s.from, false, s.to, g, split = true)
                // 부활: 물약이 떨어져 깨지는 순간에 살아난 방울이 보이게
                ShotKind.REVIVE -> if (s.id != firedRevive) {
                    firedRevive = s.id
                    centers["${s.side}${s.to}"]?.let { at ->
                        revives += ReviveBurst(System.nanoTime(), at - layerOrigin, s.side, if (s.side == g.mySide) g.mySkin else g.opponentSkin)
                        com.bubble.game.audio.Music.sfx(com.bubble.game.audio.Music.Sfx.POTION)
                        landAt = System.currentTimeMillis() + REVIVE_DROP_MS
                    }
                }
            }
        }
        val wait = landAt - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        g = live
    }

    // 보여주는 판이 바뀌는 순간(= 방울이 부딪힌 순간) 맞은 방울에서 동전이 튀어나옴
    LaunchedEffect(g.lucky) {
        val l = g.lucky ?: return@LaunchedEffect
        if (l.id == firedLucky) return@LaunchedEffect
        firedLucky = l.id
        val at = centers["${l.side.opponent}${l.to}"] ?: return@LaunchedEffect
        coins += CoinPop(System.nanoTime(), at - layerOrigin, mine = l.side == g.mySide, amount = l.coins)
        com.bubble.game.audio.Music.sfx(com.bubble.game.audio.Music.coinSfx(if (l.side == g.mySide) g.mySkin else g.opponentSkin))
    }

    var selected by remember { mutableStateOf<Int?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    // 🔊 끝나면 승리/패배 징글 (한 판에 한 번)
    LaunchedEffect(g.finished, g.winner) {
        if (g.finished && g.winner != null) {
            com.bubble.game.audio.Music.sfx(if (g.winner == g.mySide) com.bubble.game.audio.Music.Sfx.WIN else com.bubble.game.audio.Music.Sfx.LOSE)
        }
    }

    // 차례가 바뀌거나 판이 바뀌면 선택 해제
    LaunchedEffect(g.turn, g.board) {
        val s = selected
        if (!g.myTurn || (s != null && g.board.sets(g.mySide)[s] == 0)) selected = null
    }
    BackHandler { if (g.finished) vm.leaveGame() else confirmLeave = true }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(100); now = System.currentTimeMillis() } }

    val me = g.mySide
    val opp = me.opponent
    val mine = g.board.sets(me)
    val theirs = g.board.sets(opp)
    val sel = selected
    val power = sel?.let { mine[it] } ?: 0

    val inFlight = landAt > now
    /** 내 방울을 날려서 치기 */
    fun shoot(from: Int, toOpp: Boolean, to: Int) {
        if (inFlight) return
        fire(me, from, toOpp, to, g)
        vm.play(Move.Add(from, toOpp, to))
        selected = null
    }

    fun previewFor(target: Int) = if (target + power >= GameLogic.EXPLODE_AT) tr("💥 펑!", "💥 POP!", zh = "💥 啪！", hi = "💥 फट!", es = "💥 ¡PUM!", fr = "💥 POP !", ar = "💥 بوب!", de = "💥 PLOPP!", pt = "💥 POP!", ru = "💥 ХЛОП!", ja = "💥 パン！") else "→ ${target + power}"

    Box(Modifier.fillMaxSize().onGloballyPositioned { layerOrigin = it.positionInRoot() }) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 상대 방울을 치면 화면이 살짝 흔들림
                    val k = shake.value
                    translationX = kotlin.math.sin(k * 30f) * k * 7.dp.toPx()
                    translationY = kotlin.math.cos(k * 23f) * k * 4.dp.toPx()
                }
                .statusBarsPadding().navigationBarsPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 상단 바
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (g.finished) vm.leaveGame() else confirmLeave = true }) {
                    Text(tr("← 나가기", "← Leave", zh = "← 离开", hi = "← छोड़ें", es = "← Salir", fr = "← Quitter", ar = "→ خروج", de = "← Verlassen", pt = "← Sair", ru = "← Выйти", ja = "← 退出"), color = Ui.text)
                }
                Spacer(Modifier.weight(1f))
                MusicToggle()
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerTag(opp, g.opponentName, g.opponentScore?.let { tr("${it}점", "${it} pts", zh = "${it} 分", hi = "${it} अंक", es = "${it} pts", fr = "${it} pts", ar = "${it} نقطة", de = "${it} Pkt", pt = "${it} pts", ru = "${it} очк.", ja = "${it}点") }, active = g.turn == opp && !g.finished, skin = g.opponentSkin, modifier = Modifier.weight(1f, fill = false), country = g.opponentCountry, score = if (g.aiLevel == null) g.opponentScore else null)
                Spacer(Modifier.width(8.dp))
                SplitCounter(g.board.splitsLeft(opp))
                ReviveCounter(g.board.revivesLeft(opp))
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                for (i in 0 until GameLogic.SET_COUNT) {
                    val target = sel != null && theirs[i] > 0
                    SetBubble(
                        side = opp,
                        count = theirs[i],
                        title = GameLogic.setName(i),
                        mode = if (target) BubbleMode.TARGET else BubbleMode.NORMAL,
                        size = 140.dp,
                        skin = g.opponentSkin,
                        ribbons = g.opponentRibbons,
                        preview = if (target) previewFor(theirs[i]) else null,
                        explodeKey = g.explosion?.takeIf { it.side == opp && it.index == i }?.id,
                        onClick = if (target) ({ shoot(sel!!, true, i) }) else null,
                        modifier = Modifier.onGloballyPositioned { c ->
                            centers["$opp$i"] = c.positionInRoot() + androidx.compose.ui.geometry.Offset(c.size.width / 2f, c.size.height / 2f)
                        },
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            TurnBanner(g, sel != null)
            TurnTimer(g, now)
            Spacer(Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                for (i in 0 until GameLogic.SET_COUNT) {
                    val isSel = sel == i
                    val boostTarget = sel != null && sel != i && mine[i] > 0
                    val pickable = g.myTurn && mine[i] > 0
                    SetBubble(
                        side = me,
                        count = mine[i],
                        title = GameLogic.setName(i),
                        mode = when {
                            isSel -> BubbleMode.SELECTED
                            boostTarget -> BubbleMode.TARGET
                            !pickable -> BubbleMode.DISABLED
                            else -> BubbleMode.NORMAL
                        },
                        size = 150.dp,
                        skin = g.mySkin,
                        ribbons = g.myRibbons,
                        preview = if (boostTarget) "💪 " + previewFor(mine[i]) else null,
                        explodeKey = g.explosion?.takeIf { it.side == me && it.index == i }?.id,
                        modifier = Modifier.onGloballyPositioned { c ->
                            centers["$me$i"] = c.positionInRoot() + androidx.compose.ui.geometry.Offset(c.size.width / 2f, c.size.height / 2f)
                        },
                        onClick = when {
                            boostTarget -> ({ shoot(sel!!, false, i) })
                            pickable && !inFlight -> ({
                                if (!isSel) com.bubble.game.audio.Music.sfx(com.bubble.game.audio.Music.Sfx.SELECT)
                                selected = if (isSel) null else i
                            })
                            else -> null
                        },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerTag(me, if (g.myName == tr("나", "Me", zh = "我", hi = "मैं", es = "Yo", fr = "Moi", ar = "أنا", de = "Ich", pt = "Eu", ru = "Я", ja = "自分")) tr("나", "Me", zh = "我", hi = "मैं", es = "Yo", fr = "Moi", ar = "أنا", de = "Ich", pt = "Eu", ru = "Я", ja = "自分") else g.myName + tr(" (나)", " (me)", zh = " (我)", hi = " (मैं)", es = " (yo)", fr = " (moi)", ar = " (أنا)", de = " (ich)", pt = " (eu)", ru = " (я)", ja = " (自分)"), null, active = g.myTurn, skin = g.mySkin, modifier = Modifier.weight(1f, fill = false), country = g.myCountry, score = g.myScore)
                Spacer(Modifier.width(8.dp))
                SplitCounter(g.board.splitsLeft(me))
                ReviveCounter(g.board.revivesLeft(me))
                if (g.myBoost) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "🚀x3", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color.White,
                        modifier = Modifier.background(Brush.horizontalGradient(listOf(Ui.primary, Ui.accent)), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            // 분할 모양이 되면 버튼을 보여주고, 횟수를 다 썼으면 비활성화
            val splitsLeft = g.board.splitsLeft(me)
            val splitShape = sel != null && GameLogic.splitShapeOk(g.board, me, sel)
            AnimatedVisibility(splitShape, enter = fadeIn() + slideInVertically { it }) {
                Button(
                    onClick = { vm.play(Move.Split(sel!!)); selected = null },
                    enabled = splitsLeft > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = Ui.accent),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    Text(
                        if (splitsLeft > 0) tr(
"✂️ 분할하기 (${power / 2} + ${power / 2}) · 남은 ${splitsLeft}회",
"✂️ Split (${power / 2} + ${power / 2}) · ${splitsLeft} left",
zh = "✂️ 分裂 (${power / 2} + ${power / 2}) · 剩 ${splitsLeft} 次", hi = "✂️ स्प्लिट (${power / 2} + ${power / 2}) · ${splitsLeft} बाकी", es = "✂️ Dividir (${power / 2} + ${power / 2}) · quedan ${splitsLeft}", fr = "✂️ Diviser (${power / 2} + ${power / 2}) · ${splitsLeft} restantes", ar = "✂️ تقسيم (${power / 2} + ${power / 2}) · متبقٍ ${splitsLeft}", de = "✂️ Teilen (${power / 2} + ${power / 2}) · noch ${splitsLeft}", pt = "✂️ Dividir (${power / 2} + ${power / 2}) · restam ${splitsLeft}", ru = "✂️ Разделить (${power / 2} + ${power / 2}) · осталось ${splitsLeft}", ja = "✂️ 分割 (${power / 2} + ${power / 2}) · 残り${splitsLeft}回",
)
                        else tr("✂️ 분할 횟수를 모두 썼어요", "✂️ No splits left", zh = "✂️ 分裂次数已用完", hi = "✂️ स्प्लिट खत्म", es = "✂️ No quedan divisiones", fr = "✂️ Plus de divisions", ar = "✂️ لا تقسيمات متبقية", de = "✂️ Keine Teilungen mehr", pt = "✂️ Sem divisões restantes", ru = "✂️ Деления закончились", ja = "✂️ 分割回数を使い切りました"),
                        fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    )
                }
            }
            // 부활물약: 터진 방울이 있고 물약이 남았으면 (선택 없이 바로 사용)
            val reviveTarget = (0 until GameLogic.SET_COUNT).firstOrNull { GameLogic.canRevive(g.board, me, it) }
            AnimatedVisibility(g.myTurn && reviveTarget != null, enter = fadeIn() + slideInVertically { it }) {
                Button(
                    onClick = { reviveTarget?.let { vm.play(Move.Revive(it)) }; selected = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5C8A)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(
                        tr(
"💖 부활물약으로 ${GameLogic.setName(reviveTarget ?: 0)} 살리기 · 남은 ${g.board.revivesLeft(me)}개",
"💖 Revive ${GameLogic.setName(reviveTarget ?: 0)} · ${g.board.revivesLeft(me)} left",
zh = "💖 复活${GameLogic.setName(reviveTarget ?: 0)} · 剩 ${g.board.revivesLeft(me)} 瓶", hi = "💖 ${GameLogic.setName(reviveTarget ?: 0)} वापस लाएँ · ${g.board.revivesLeft(me)} बाकी", es = "💖 Revivir ${GameLogic.setName(reviveTarget ?: 0)} · quedan ${g.board.revivesLeft(me)}", fr = "💖 Ranimer ${GameLogic.setName(reviveTarget ?: 0)} · ${g.board.revivesLeft(me)} restantes", ar = "💖 إحياء ${GameLogic.setName(reviveTarget ?: 0)} · متبقٍ ${g.board.revivesLeft(me)}", de = "💖 ${GameLogic.setName(reviveTarget ?: 0)} wiederbeleben · noch ${g.board.revivesLeft(me)}", pt = "💖 Reviver ${GameLogic.setName(reviveTarget ?: 0)} · restam ${g.board.revivesLeft(me)}", ru = "💖 Оживить ${GameLogic.setName(reviveTarget ?: 0)} · осталось ${g.board.revivesLeft(me)}", ja = "💖 ${GameLogic.setName(reviveTarget ?: 0)}を復活 · 残り${g.board.revivesLeft(me)}個",
),
                        fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            g.notice?.let {
                Text(it, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ui.danger, textAlign = TextAlign.Center)
            }
            g.log.take(2).forEachIndexed { i, line ->
                Text(
                    line,
                    fontSize = if (i == 0) 16.sp else 14.sp,
                    fontWeight = if (i == 0) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = Ui.text.copy(alpha = if (i == 0) 1f else 0.55f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        // 날아가는 방울들
        ShotLayer(
            shots,
            onImpact = { s ->
                // 🔊 맞는 소리: 터지면 펑, 아니면 통 (분할은 소리 없이)
                if (s.boom) com.bubble.game.audio.Music.sfx(com.bubble.game.audio.Music.Sfx.POP)
                else if (!s.split) com.bubble.game.audio.Music.sfx(com.bubble.game.audio.Music.Sfx.HIT)
                if (s.attack || s.boom) scope.launch {
                    shake.snapTo(if (s.boom) 1f else 0.6f)
                    shake.animateTo(0f, tween(if (s.boom) 650 else 420))
                }
            },
            onDone = { s -> shots.remove(s) },
        )
        coins.forEach { c -> androidx.compose.runtime.key(c.key) { CoinPopFx(c) { coins.remove(it) } } }
        revives.forEach { r -> androidx.compose.runtime.key(r.key) { ReviveFx(r) { revives.remove(it) } } }

        // 매칭되면 3초 동안 VS 소개 (나라·계급)
        var showVs by remember { mutableStateOf(false) }
        LaunchedEffect(g.gameId) {
            if (g.gameId != null && g.showIntro) {
                showVs = true
                delay(3_000)
                showVs = false
            }
        }
        AnimatedVisibility(showVs && !g.finished, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut()) {
            VsIntro(g) { showVs = false }
        }

        if (g.finished && g.winner != null) {
            val activity = LocalContext.current as Activity
            val rewardedReady by AdManager.rewardedReady.collectAsState()
            var showRewardIntro by remember { mutableStateOf(false) }
            // 친구 대전은 광고로 점수를 돌려받을 수 없음 (서버도 거절)
            val lostOnline = g.online && !g.friend && g.winner != g.mySide && (g.scoreDelta ?: 0) < 0
            val canOfferReward = lostOnline && rewardedReady && vm.rewardTarget() != null
            val lobbyUi by vm.lobby.collectAsState()
            ResultOverlay(
                g,
                refCode = lobbyUi.profile?.refCode,
                rewardOffer = if (canOfferReward) minOf(OnlineRepository.AD_REFUND_POINTS, -(g.scoreDelta ?: 0)) else null,
                onLobby = {
                    if (canOfferReward) {
                        showRewardIntro = true
                    } else {
                        AdManager.maybeShowInterstitial(activity) { vm.leaveGame() }
                    }
                },
                onAgain = {
                    val lv = g.aiLevel ?: AiLevel.NORMAL
                    AdManager.maybeShowInterstitial(activity) { vm.startAiGame(lv) }
                },
            )
            if (showRewardIntro) {
                RewardIntro(
                    points = minOf(OnlineRepository.AD_REFUND_POINTS, -(g.scoreDelta ?: 0)),
                    onSkip = { showRewardIntro = false; vm.leaveGame() },
                    onWatch = {
                        showRewardIntro = false
                        val target = vm.rewardTarget()
                        if (target == null) {
                            vm.leaveGame()
                        } else {
                            AdManager.showRewarded(
                                activity, target.first, target.second,
                                onEarned = vm::onAdRewardEarned,
                                onDone = vm::leaveGame,
                            )
                        }
                    },
                )
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(tr("게임에서 나갈까요?", "Leave the game?", zh = "要离开游戏吗？", hi = "गेम छोड़ना है?", es = "¿Salir de la partida?", fr = "Quitter la partie ?", ar = "هل تريد مغادرة المباراة؟", de = "Spiel verlassen?", pt = "Sair da partida?", ru = "Выйти из игры?", ja = "ゲームを退出しますか？")) },
            text = {
                Text(
                    when {
                        g.scoredAi -> tr(
"지금 나가면 패배로 처리돼요 (${g.aiLevel!!.losePoints}점).",
"Leaving now counts as a loss (${g.aiLevel!!.losePoints} pts).",
zh = "现在离开将判负（${g.aiLevel!!.losePoints} 分）。", hi = "अभी छोड़ने पर हार मानी जाएगी (${g.aiLevel!!.losePoints} अंक)।", es = "Salir ahora cuenta como derrota (${g.aiLevel!!.losePoints} pts).", fr = "Partir maintenant compte comme une défaite (${g.aiLevel!!.losePoints} pts).", ar = "المغادرة الآن تُحتسب خسارة (${g.aiLevel!!.losePoints} نقطة).", de = "Jetzt zu gehen zählt als Niederlage (${g.aiLevel!!.losePoints} Pkt).", pt = "Sair agora conta como derrota (${g.aiLevel!!.losePoints} pts).", ru = "Выход сейчас — поражение (${g.aiLevel!!.losePoints} очк.).", ja = "今退出すると負けになります（${g.aiLevel!!.losePoints}点）。",
)
                        g.online -> tr(
"지금 나가면 기권패로 처리돼요 (-${OnlineRepository.LOSE_POINTS}점).",
"Leaving now counts as a forfeit (-${OnlineRepository.LOSE_POINTS} pts).",
zh = "现在离开将判为弃权（-${OnlineRepository.LOSE_POINTS} 分）。", hi = "अभी छोड़ने पर हार मानी जाएगी (-${OnlineRepository.LOSE_POINTS} अंक)।", es = "Salir ahora cuenta como abandono (-${OnlineRepository.LOSE_POINTS} pts).", fr = "Partir maintenant compte comme un abandon (-${OnlineRepository.LOSE_POINTS} pts).", ar = "المغادرة الآن تُحتسب انسحابًا (-${OnlineRepository.LOSE_POINTS} نقطة).", de = "Jetzt zu gehen zählt als Aufgabe (-${OnlineRepository.LOSE_POINTS} Pkt).", pt = "Sair agora conta como desistência (-${OnlineRepository.LOSE_POINTS} pts).", ru = "Выход сейчас — техническое поражение (-${OnlineRepository.LOSE_POINTS} очк.).", ja = "今退出すると棄権負けになります（-${OnlineRepository.LOSE_POINTS}点）。",
)
                        else -> tr("연습 게임이 끝나요.", "This practice game will end.", zh = "这局练习将结束。", hi = "यह अभ्यास गेम खत्म हो जाएगा।", es = "Esta partida de práctica terminará.", fr = "Cette partie d’entraînement va se terminer.", ar = "ستنتهي مباراة التدريب هذه.", de = "Diese Übungspartie wird beendet.", pt = "Esta partida de treino vai terminar.", ru = "Тренировочная партия завершится.", ja = "この練習ゲームは終了します。")
                    },
                )
            },
            confirmButton = { TextButton(onClick = { confirmLeave = false; vm.leaveGame() }) { Text(tr("나가기", "Leave", zh = "离开", hi = "छोड़ें", es = "Salir", fr = "Quitter", ar = "مغادرة", de = "Verlassen", pt = "Sair", ru = "Выйти", ja = "退出"), color = Ui.danger) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(tr("계속하기", "Keep playing", zh = "继续玩", hi = "खेलते रहें", es = "Seguir jugando", fr = "Continuer", ar = "متابعة اللعب", de = "Weiterspielen", pt = "Continuar jogando", ru = "Продолжить", ja = "続ける")) } },
        )
    }
}

@Composable
private fun PlayerTag(
    side: Side,
    name: String,
    extra: String?,
    active: Boolean,
    skin: Animal? = null,
    modifier: Modifier = Modifier,
    country: String? = null,
    /** 점수가 있으면 계급 배지 표시 */
    score: Int? = null,
) {
    val t = rememberInfiniteTransition(label = "tag")
    val glow by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "glow")
    Row(
        modifier
            .background(
                if (active) Ui.gold.copy(alpha = 0.35f + glow * 0.4f) else Color.White.copy(alpha = 0.6f),
                RoundedCornerShape(50),
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Critter(side, Modifier.size(26.dp), skin = skin)
        if (country != null) Text(" " + Country.flag(country), fontSize = 15.sp)
        Spacer(Modifier.width(6.dp))
        // 이름이 길면 말줄임 (옆의 분할·물약 표시가 찌그러지지 않게)
        Text(
            "${skin?.label ?: side.label} · $name",
            color = Ui.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
        )
        if (score != null) {
            Spacer(Modifier.width(4.dp))
            RankBadge(score)
        }
        if (extra != null) Text("  $extra", color = Ui.primaryDark, fontSize = 13.sp)
    }
}

@Composable
private fun TurnBanner(g: GameUi, selecting: Boolean) {
    val text = when {
        g.finished -> tr("게임 끝!", "Game over!", zh = "游戏结束！", hi = "गेम खत्म!", es = "¡Fin de la partida!", fr = "Partie terminée !", ar = "انتهت المباراة!", de = "Spiel vorbei!", pt = "Fim de jogo!", ru = "Игра окончена!", ja = "ゲーム終了！")
        g.busy && g.turn == g.mySide -> tr("보내는 중…", "Sending…", zh = "发送中…", hi = "भेज रहे हैं…", es = "Enviando…", fr = "Envoi…", ar = "جارٍ الإرسال…", de = "Sende…", pt = "Enviando…", ru = "Отправка…", ja = "送信中…")
        g.turn != g.mySide -> if (g.aiLevel != null) tr("AI가 생각 중… 🤔", "AI is thinking… 🤔", zh = "AI 正在思考… 🤔", hi = "AI सोच रहा है… 🤔", es = "La IA está pensando… 🤔", fr = "L’IA réfléchit… 🤔", ar = "الذكاء الاصطناعي يفكّر… 🤔", de = "Die KI denkt nach… 🤔", pt = "A IA está pensando… 🤔", ru = "ИИ думает… 🤔", ja = "AIが考え中… 🤔") else tr("상대가 고민 중이에요…", "Opponent is thinking…", zh = "对手正在思考…", hi = "विरोधी सोच रहा है…", es = "El rival está pensando…", fr = "L’adversaire réfléchit…", ar = "الخصم يفكّر…", de = "Der Gegner denkt nach…", pt = "O rival está pensando…", ru = "Соперник думает…", ja = "相手が考え中…")
        selecting -> tr("어디에 더할까요? 반짝이는 방울을 눌러요", "Where to add? Tap a glowing bubble", zh = "加到哪里？点一下闪光的泡泡", hi = "कहाँ जोड़ें? चमकते बबल पर टैप करें", es = "¿Dónde sumar? Toca una burbuja brillante", fr = "Où ajouter ? Touche une bulle qui brille", ar = "أين تضيف؟ اضغط على فقاعة متوهجة", de = "Wohin addieren? Tippe auf eine leuchtende Blase", pt = "Onde somar? Toque numa bolha brilhante", ru = "Куда добавить? Нажмите на светящийся пузырь", ja = "どこに足す？光っているバブルをタップ")
        else -> tr("내 차례! 내 방울을 하나 골라요", "Your turn! Pick one of your bubbles", zh = "轮到你了！选一个你的泡泡", hi = "आपकी बारी! अपना एक बबल चुनें", es = "¡Tu turno! Elige una de tus burbujas", fr = "À toi ! Choisis une de tes bulles", ar = "دورك! اختر إحدى فقاعاتك", de = "Du bist dran! Wähle eine deiner Blasen", pt = "Sua vez! Escolha uma de suas bolhas", ru = "Ваш ход! Выберите свой пузырь", ja = "あなたの番！自分のバブルを1つ選んで")
    }
    AnimatedContent(text, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith fadeOut() }, label = "turn") {
        Text(
            it, fontSize = 22.sp, fontWeight = FontWeight.Black, lineHeight = 28.sp,
            color = if (g.turn == g.mySide && !g.finished) Ui.primaryDark else Ui.text,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun ResultOverlay(g: GameUi, refCode: String?, rewardOffer: Int?, onLobby: () -> Unit, onAgain: () -> Unit) {
    val won = g.winner == g.mySide
    val context = LocalContext.current
    val t = rememberInfiniteTransition(label = "result")
    val jump by t.animateFloat(0f, 1f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "jump")
    Box(
        Modifier.fillMaxSize().background(Color(0x88302040)),
        contentAlignment = Alignment.Center,
    ) {
        // 이기면 내 스킨 테마로 하트 · 달 · 눈송이 · 금가루가 쏟아짐 (모습만)
        if (won) WinRain(g.mySide, g.mySkin)
        Card(
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.padding(32.dp),
        ) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Critter(
                    g.mySide, Modifier.size(120.dp).offset(y = (if (won) -jump * 16 else 0f).dp),
                    happy = won, skin = g.mySkin, ribbons = g.myRibbons,
                )
                Text(
                    if (won) tr("승리! 🎉", "You win! 🎉", zh = "你赢了！🎉", hi = "आप जीत गए! 🎉", es = "¡Ganaste! 🎉", fr = "Victoire ! 🎉", ar = "لقد فزت! 🎉", de = "Gewonnen! 🎉", pt = "Você venceu! 🎉", ru = "Победа! 🎉", ja = "勝利！🎉") else tr("아쉬운 패배…", "So close…", zh = "差一点…", hi = "बस थोड़ा सा रह गया…", es = "Casi…", fr = "Presque…", ar = "كدت تفوز…", de = "Knapp daneben…", pt = "Quase…", ru = "Почти…", ja = "惜しい…"),
                    fontSize = 32.sp, fontWeight = FontWeight.Black,
                    color = if (won) Ui.primaryDark else Ui.text,
                )
                val reason = when (g.reason) {
                    "forfeit" -> if (won) tr("상대가 나갔어요", "Your opponent left", zh = "对手离开了", hi = "विरोधी चला गया", es = "Tu rival se fue", fr = "Ton adversaire est parti", ar = "غادر خصمك", de = "Dein Gegner hat das Spiel verlassen", pt = "Seu rival saiu", ru = "Соперник вышел", ja = "相手が退出しました") else tr("기권했어요", "You forfeited", zh = "你弃权了", hi = "आपने हार मान ली", es = "Abandonaste", fr = "Tu as abandonné", ar = "لقد انسحبت", de = "Du hast aufgegeben", pt = "Você desistiu", ru = "Вы сдались", ja = "棄権しました")
                    "timeout" -> if (won) tr("상대가 시간을 넘겼어요", "Your opponent ran out of time", zh = "对手超时了", hi = "विरोधी का समय खत्म हो गया", es = "A tu rival se le acabó el tiempo", fr = "Ton adversaire a dépassé le temps", ar = "انتهى وقت خصمك", de = "Deinem Gegner lief die Zeit ab", pt = "O tempo do rival acabou", ru = "У соперника кончилось время", ja = "相手が時間切れになりました") else tr("시간을 넘겼어요", "You ran out of time", zh = "你超时了", hi = "आपका समय खत्म हो गया", es = "Se te acabó el tiempo", fr = "Tu as dépassé le temps", ar = "انتهى وقتك", de = "Deine Zeit ist abgelaufen", pt = "Seu tempo acabou", ru = "У вас кончилось время", ja = "時間切れになりました")
                    else -> if (won) tr("상대 방울을 모두 터뜨렸어요!", "You popped all their bubbles!", zh = "你戳破了对方所有泡泡！", hi = "आपने उनके सारे बबल फोड़ दिए!", es = "¡Reventaste todas sus burbujas!", fr = "Tu as éclaté toutes ses bulles !", ar = "فجّرت كل فقاعاته!", de = "Du hast alle seine Blasen platzen lassen!", pt = "Você estourou todas as bolhas dele!", ru = "Вы лопнули все его пузыри!", ja = "相手のバブルを全部割りました！") else tr("내 방울이 모두 터졌어요", "All your bubbles popped", zh = "你的泡泡全破了", hi = "आपके सारे बबल फट गए", es = "Todas tus burbujas reventaron", fr = "Toutes tes bulles ont éclaté", ar = "انفجرت كل فقاعاتك", de = "Alle deine Blasen sind geplatzt", pt = "Todas as suas bolhas estouraram", ru = "Все ваши пузыри лопнули", ja = "自分のバブルが全部割れました")
                }
                Text(reason, color = Ui.text.copy(alpha = 0.7f))
                if (g.online) {
                    Spacer(Modifier.height(10.dp))
                    val d = g.scoreDelta
                    Text(
                        when {
                            d == null -> tr("점수 계산 중…", "Calculating score…", zh = "正在计算分数…", hi = "स्कोर गिना जा रहा है…", es = "Calculando puntuación…", fr = "Calcul du score…", ar = "جارٍ حساب النقاط…", de = "Punkte werden berechnet…", pt = "Calculando pontuação…", ru = "Подсчёт очков…", ja = "スコア計算中…")
                            d >= 0 -> tr("+$d 점", "+$d pts", zh = "+$d 分", hi = "+$d अंक", es = "+$d pts", fr = "+$d pts", ar = "+$d نقطة", de = "+$d Pkt", pt = "+$d pts", ru = "+$d очк.", ja = "+${d}点")
                            else -> tr("$d 점", "$d pts", zh = "$d 分", hi = "$d अंक", es = "$d pts", fr = "$d pts", ar = "$d نقطة", de = "$d Pkt", pt = "$d pts", ru = "$d очк.", ja = "${d}点")
                        },
                        fontSize = 26.sp, fontWeight = FontWeight.Black,
                        color = if ((d ?: 0) >= 0) Ui.accent else Ui.danger,
                    )
                    if (g.friend && !g.friendRated) {
                        Text(
                            tr("🤝 친선전 (오늘 이 친구와는 점수가 걸린 ${OnlineRepository.FRIEND_RATED_PER_DAY}판을 다 했어요)", "🤝 Friendly match (you've played today's ${OnlineRepository.FRIEND_RATED_PER_DAY} scored games with this friend)",
                                zh = "🤝 友谊赛（今天与这位好友的 ${OnlineRepository.FRIEND_RATED_PER_DAY} 局计分赛已用完）", es = "🤝 Amistoso (ya jugaste las ${OnlineRepository.FRIEND_RATED_PER_DAY} partidas con puntos de hoy con este amigo)",
                                fr = "🤝 Match amical (les ${OnlineRepository.FRIEND_RATED_PER_DAY} parties notées du jour avec cet ami sont jouées)", de = "🤝 Freundschaftsspiel (die ${OnlineRepository.FRIEND_RATED_PER_DAY} gewerteten Partien mit diesem Freund sind heute schon gespielt)",
                                pt = "🤝 Amistoso (as ${OnlineRepository.FRIEND_RATED_PER_DAY} partidas com pontos de hoje com este amigo já foram jogadas)", ru = "🤝 Товарищеский матч (сегодняшние ${OnlineRepository.FRIEND_RATED_PER_DAY} игр на очки с этим другом сыграны)",
                                ja = "🤝 フレンドリーマッチ（今日はこの友だちとの点数付き${OnlineRepository.FRIEND_RATED_PER_DAY}戦を終えました）"),
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ui.text.copy(alpha = 0.75f), textAlign = TextAlign.Center,
                        )
                    }
                    if (g.myBoost && (d ?: 0) > 0) {
                        Text(
                            tr("🚀 ×3 부스터 적용!", "🚀 x3 booster applied!", zh = "🚀 ×3 加成生效！", hi = "🚀 ×3 बूस्टर लगा!",
                                es = "🚀 ¡Potenciador x3 aplicado!", fr = "🚀 Booster x3 appliqué !", ar = "🚀 تم تطبيق المضاعِف ×3!",
                                de = "🚀 x3-Booster angewendet!", pt = "🚀 Impulso x3 aplicado!", ru = "🚀 Бустер x3 сработал!",
                                ja = "🚀 ×3 ブースター適用！"),
                            fontSize = 16.sp, fontWeight = FontWeight.Black, color = Ui.primaryDark,
                        )
                    }
                    if (g.scoreBonus > 0) {
                        Text(
                            tr("✨ 계급 보너스 +${g.scoreBonus}점 포함", "✨ Includes rank bonus +${g.scoreBonus}", zh = "✨ 含等级奖励 +${g.scoreBonus}", hi = "✨ रैंक बोनस +${g.scoreBonus} शामिल", es = "✨ Incluye bonus de rango +${g.scoreBonus}", fr = "✨ Bonus de rang inclus +${g.scoreBonus}", ar = "✨ يشمل مكافأة الرتبة +${g.scoreBonus}", de = "✨ Inkl. Rangbonus +${g.scoreBonus}", pt = "✨ Inclui bônus de rank +${g.scoreBonus}", ru = "✨ С бонусом ранга +${g.scoreBonus}", ja = "✨ ランクボーナス +${g.scoreBonus} 込み"),
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ui.gold.copy(red = 0.85f, green = 0.6f, blue = 0f),
                        )
                    }
                    if (won && g.rabbitStreak > 0) RabbitResult(g.rabbitStreak, g.rabbitCoins)
                    if (won && g.unicornCoins > 0) UnicornResult(g.unicornCoins)
                    CoinSummary(g.mySkin, g.gameCoins, if (won) g.rabbitCoins + g.unicornCoins else 0)
                    if (rewardOffer != null) {
                        Text(
                            tr(
"📺 로비로 갈 때 광고를 보면 ${rewardOffer}점을 돌려받아요",
"📺 Watch an ad on the way out to get ${rewardOffer} pts back",
zh = "📺 离开时看个广告，可拿回 ${rewardOffer} 分", hi = "📺 बाहर जाते समय विज्ञापन देखें, ${rewardOffer} अंक वापस पाएँ", es = "📺 Mira un anuncio al salir y recupera ${rewardOffer} pts", fr = "📺 Regarde une pub en sortant pour récupérer ${rewardOffer} pts", ar = "📺 شاهد إعلانًا عند الخروج لتستعيد ${rewardOffer} نقطة", de = "📺 Sieh beim Verlassen eine Werbung und hol dir ${rewardOffer} Pkt zurück", pt = "📺 Assista a um anúncio ao sair e recupere ${rewardOffer} pts", ru = "📺 Посмотрите рекламу при выходе и верните ${rewardOffer} очк.", ja = "📺 退出時に広告を見ると ${rewardOffer}点が戻ります",
),
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ui.accent,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                // 📣 이겼으면 승리 카드 공유 (내 추천 코드 링크 포함 → 친구가 들어오면 둘 다 +30점)
                if (won) {
                    Button(
                        onClick = { ShareCard.share(context, winCard(g, refCode)) },
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Ui.accent),
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        Text(tr("📣 자랑하기", "📣 Share my win", zh = "📣 炫耀一下", hi = "📣 जीत शेयर करें", es = "📣 Presumir", fr = "📣 Partager ma victoire", ar = "📣 شارك فوزك", de = "📣 Sieg teilen", pt = "📣 Compartilhar vitória", ru = "📣 Похвастаться", ja = "📣 自慢する"), fontWeight = FontWeight.Black)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (g.aiLevel != null) {
                        OutlinedButton(onClick = onAgain, shape = RoundedCornerShape(18.dp)) { Text(tr("한 판 더", "Play again", zh = "再来一局", hi = "फिर से खेलें", es = "Jugar de nuevo", fr = "Rejouer", ar = "العب مجددًا", de = "Nochmal spielen", pt = "Jogar de novo", ru = "Ещё раз", ja = "もう一回")) }
                    }
                    Button(
                        onClick = onLobby, shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Ui.primary),
                    ) { Text(tr("로비로", "Lobby", zh = "大厅", hi = "लॉबी", es = "Sala", fr = "Accueil", ar = "الردهة", de = "Lobby", pt = "Lobby", ru = "Лобби", ja = "ロビー")) }
                }
            }
        }
    }
}

/** 승리 공유 카드 내용 (화면 언어로) */
private fun winCard(g: GameUi, refCode: String?): ShareCard.Win {
    val d = g.scoreDelta
    val after = (g.myScore ?: 0) + (d ?: 0)
    val rank = Rank.of(after)
    val pts = when {
        d == null || !g.online -> ""
        d > 0 -> tr("+${d}점 · ", "+$d pts · ", zh = "+$d 分 · ", ja = "+${d}点 · ")
        else -> ""
    }
    val vs = when {
        g.friend -> tr("👫 친구 대전", "👫 Friend battle", zh = "👫 好友对战", es = "👫 Duelo entre amigos", fr = "👫 Duel entre amis", de = "👫 Freundesduell", pt = "👫 Duelo entre amigos", ru = "👫 Бой с другом", ja = "👫 友だち対戦")
        g.aiLevel != null -> tr("🤖 AI 대전", "🤖 AI battle", zh = "🤖 AI 对战", es = "🤖 Batalla IA", fr = "🤖 Combat IA", de = "🤖 KI-Duell", pt = "🤖 Batalha IA", ru = "🤖 Бой с ИИ", ja = "🤖 AI対戦")
        else -> tr("🎲 랜덤 매칭", "🎲 Random match", zh = "🎲 随机匹配", es = "🎲 Partida aleatoria", fr = "🎲 Match aléatoire", de = "🎲 Zufallsmatch", pt = "🎲 Partida aleatória", ru = "🎲 Случайный матч", ja = "🎲 ランダムマッチ")
    }
    val link = refCode?.let { Invite.link(it) } ?: Invite.PAGE
    val footer = if (refCode != null) {
        tr("추천 코드 $refCode · 둘 다 +30점 🎁", "Code $refCode · we both get +30 🎁", zh = "邀请码 $refCode · 双方各 +30 🎁", es = "Código $refCode · ambos +30 🎁", fr = "Code $refCode · +30 pour nous deux 🎁", de = "Code $refCode · beide +30 🎁", pt = "Código $refCode · os dois +30 🎁", ru = "Код $refCode · обоим +30 🎁", ja = "招待コード $refCode · 2人とも+30 🎁")
    } else {
        tr("고양이 vs 햄스터 1:1 방울 대결", "Cats vs Hamsters 1:1 bubble battle", zh = "猫咪 vs 仓鼠 1:1 泡泡对决", es = "Gatos vs Hámsteres 1:1", fr = "Chats contre Hamsters 1:1", de = "Katzen gegen Hamster 1:1", pt = "Gatos vs Hamsters 1:1", ru = "Котики против хомяков 1:1", ja = "ネコ vs ハムスター 1対1")
    }
    val message = tr(
        "Bubble 냥햄대전에서 ${g.opponentName}을(를) 이겼어! 🏆 너도 한 판 해볼래?" + (refCode?.let { "\n내 추천 코드 $it 넣으면 우리 둘 다 +30점 🎁" } ?: "") + "\n$link",
        "I beat ${g.opponentName} in Bubble: Cats vs Hamsters! 🏆 Wanna try?" + (refCode?.let { "\nUse my code $it and we both get +30 pts 🎁" } ?: "") + "\n$link",
        zh = "我在 Bubble 猫咪大战仓鼠里赢了 ${g.opponentName}！🏆 你也来一局？" + (refCode?.let { "\n输入我的邀请码 $it，我们各得 +30 分 🎁" } ?: "") + "\n$link",
        es = "¡Le gané a ${g.opponentName} en Bubble! 🏆 ¿Te animas?" + (refCode?.let { "\nUsa mi código $it y ambos ganamos +30 pts 🎁" } ?: "") + "\n$link",
        fr = "J’ai battu ${g.opponentName} dans Bubble ! 🏆 Tu tentes ?" + (refCode?.let { "\nAvec mon code $it, on gagne +30 pts chacun 🎁" } ?: "") + "\n$link",
        de = "Ich habe ${g.opponentName} in Bubble besiegt! 🏆 Probierst du’s?" + (refCode?.let { "\nMit meinem Code $it bekommen wir beide +30 Pkt 🎁" } ?: "") + "\n$link",
        pt = "Venci ${g.opponentName} no Bubble! 🏆 Bora jogar?" + (refCode?.let { "\nUse meu código $it e nós dois ganhamos +30 pts 🎁" } ?: "") + "\n$link",
        ru = "Я победил ${g.opponentName} в Bubble! 🏆 Сыграешь?" + (refCode?.let { "\nВведи мой код $it — получим по +30 очк. 🎁" } ?: "") + "\n$link",
        ja = "Bubble で ${g.opponentName} に勝ったよ！🏆 一緒にやろう？" + (refCode?.let { "\n招待コード $it を入れると2人とも +30点 🎁" } ?: "") + "\n$link",
    )
    return ShareCard.Win(
        title = tr("승리!", "Victory!", zh = "胜利！", hi = "जीत!", es = "¡Victoria!", fr = "Victoire !", ar = "فوز!", de = "Sieg!", pt = "Vitória!", ru = "Победа!", ja = "勝利！"),
        matchLine = "$vs · ${g.myName} vs ${g.opponentName}",
        scoreLine = pts + "${rank.emoji} ${rank.label}",
        emoji = if (g.mySide == Side.CAT) "🐱" else "🐹",
        footer = footer,
        message = message,
    )
}

/** 남은 분할 횟수. 줄어들면 "-1"이 떠오르며 사라집니다. */
@Composable
private fun SplitCounter(left: Int) {
    var previous by remember { mutableStateOf(left) }
    val drop = remember { Animatable(1f) }
    val bump = remember { Animatable(1f) }
    var lost by remember { mutableStateOf(0) }
    LaunchedEffect(left) {
        if (left < previous) {
            lost = previous - left
            launch {
                bump.snapTo(1.35f)
                bump.animateTo(1f, spring(dampingRatio = 0.35f))
            }
            drop.snapTo(0f)
            drop.animateTo(1f, tween(1_400))
        }
        previous = left
    }
    Box(contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .scale(bump.value)
                .background(
                    if (left == 0) Color(0xFFE0E0E0) else Ui.accent.copy(alpha = 0.18f),
                    RoundedCornerShape(50),
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✂️", fontSize = 14.sp)
            Spacer(Modifier.width(4.dp))
            repeat(GameLogic.MAX_SPLITS) { i ->
                Box(
                    Modifier
                        .padding(horizontal = 1.5.dp)
                        .size(9.dp)
                        .background(if (i < left) Ui.accent else Color(0xFFBDBDBD), RoundedCornerShape(50)),
                )
            }
            Spacer(Modifier.width(5.dp))
            Text(
                "$left/${GameLogic.MAX_SPLITS}",
                maxLines = 1, softWrap = false,
                fontWeight = FontWeight.Black, fontSize = 14.sp,
                color = if (left == 0) Color(0xFF9E9E9E) else Ui.accent,
            )
        }
        if (drop.value < 1f) {
            Text(
                "-$lost",
                fontSize = 22.sp, fontWeight = FontWeight.Black, color = Ui.danger,
                modifier = Modifier
                    .offset(y = (-18 - drop.value * 26).dp)
                    .scale(1f + (1f - drop.value) * 0.4f)
                    .alpha(1f - drop.value),
            )
        }
    }
}

/**
 * 보상형 전면 광고 안내 화면 (AdMob 정책: 광고 전에 보상 내용을 알리고 건너뛸 수 있어야 함).
 * 5초 뒤 자동으로 광고가 시작되며, 언제든 건너뛸 수 있습니다.
 */
@Composable
private fun RewardIntro(points: Int, onSkip: () -> Unit, onWatch: () -> Unit) {
    var remain by remember { mutableStateOf(5) }
    LaunchedEffect(Unit) {
        while (remain > 0) {
            delay(1_000)
            remain--
        }
        onWatch()
    }
    BackHandler(onBack = onSkip)
    Box(
        Modifier.fillMaxSize().background(Color(0xCC201530)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.padding(28.dp),
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📺", fontSize = 44.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    tr(
"광고를 보면\n잃은 점수 중 ${points}점을 돌려드려요",
"Watch an ad to get\n${points} of your lost points back",
zh = "看广告可拿回\n你损失的 ${points} 分", hi = "विज्ञापन देखें और\nखोए हुए ${points} अंक वापस पाएँ", es = "Mira un anuncio y recupera\n${points} de los puntos perdidos", fr = "Regarde une pub pour récupérer\n${points} de tes points perdus", ar = "شاهد إعلانًا لتستعيد\n${points} من نقاطك المفقودة", de = "Sieh eine Werbung und hol dir\n${points} verlorene Punkte zurück", pt = "Assista a um anúncio e recupere\n${points} dos pontos perdidos", ru = "Посмотрите рекламу и верните\n${points} потерянных очков", ja = "広告を見ると\n失った点数のうち${points}点が戻ります",
),
                    fontSize = 20.sp, fontWeight = FontWeight.Black, color = Ui.text,
                    textAlign = TextAlign.Center, lineHeight = 28.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    tr("${remain}초 후 광고가 시작돼요", "Ad starts in ${remain}s", zh = "广告将在 ${remain} 秒后开始", hi = "विज्ञापन ${remain} सेकंड में शुरू होगा", es = "El anuncio empieza en ${remain} s", fr = "La pub commence dans ${remain} s", ar = "يبدأ الإعلان خلال ${remain} ث", de = "Werbung startet in ${remain} s", pt = "O anúncio começa em ${remain} s", ru = "Реклама через ${remain} с", ja = "${remain}秒後に広告が始まります"),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ui.accent,
                )
                Text(
                    tr("광고를 끝까지 봐야 점수가 돌아와요", "Watch to the end to get your points back", zh = "看完整个广告才能拿回分数", hi = "अंक वापस पाने के लिए पूरा विज्ञापन देखें", es = "Míralo hasta el final para recuperar tus puntos", fr = "Regarde jusqu’au bout pour récupérer tes points", ar = "شاهده حتى النهاية لتستعيد نقاطك", de = "Bis zum Ende ansehen, um die Punkte zu bekommen", pt = "Assista até o fim para recuperar seus pontos", ru = "Досмотрите до конца, чтобы вернуть очки", ja = "最後まで見ると点数が戻ります"),
                    fontSize = 12.sp, color = Ui.text.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onSkip, shape = RoundedCornerShape(18.dp)) {
                        Text(tr("괜찮아요 (건너뛰기)", "No thanks (skip)", zh = "不用了（跳过）", hi = "नहीं, धन्यवाद (छोड़ें)", es = "No, gracias (saltar)", fr = "Non merci (passer)", ar = "لا، شكرًا (تخطٍّ)", de = "Nein danke (überspringen)", pt = "Não, obrigado (pular)", ru = "Нет, спасибо (пропустить)", ja = "いいえ（スキップ）"), color = Ui.text)
                    }
                    Button(
                        onClick = onWatch, shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Ui.accent),
                    ) { Text(tr("지금 보기", "Watch now", zh = "现在观看", hi = "अभी देखें", es = "Ver ahora", fr = "Regarder", ar = "شاهد الآن", de = "Jetzt ansehen", pt = "Assistir agora", ru = "Смотреть", ja = "今すぐ見る"), fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ======================= 상점 =======================

@Composable
private fun ShopIconButton(onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "shopIcon")
    val bob by t.animateFloat(-3f, 3f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "bob")
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Ui.gold, contentColor = Ui.text),
        modifier = Modifier.offset(y = bob.dp),
    ) {
        Text(tr("🛒 상점", "🛒 Shop", zh = "🛒 商店", hi = "🛒 दुकान", es = "🛒 Tienda", fr = "🛒 Boutique", ar = "🛒 المتجر", de = "🛒 Shop", pt = "🛒 Loja", ru = "🛒 Магазин", ja = "🛒 ショップ"), fontWeight = FontWeight.Black, fontSize = 16.sp)
    }
}

/** 상점 안내: 아이템이 어떤 대전에서 효과가 있는지 한눈에 */
@Composable
private fun ItemUsageCard() {
    val yes = Color(0xFF2E9E5B)
    val no = Ui.text.copy(alpha = 0.35f)
    @Composable
    fun Cell(text: String, color: Color, bg: Color, modifier: Modifier) {
        Box(modifier.padding(horizontal = 3.dp), contentAlignment = Alignment.Center) {
            Text(
                text, color = color, fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 1, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(bg, RoundedCornerShape(10.dp)).padding(vertical = 6.dp),
            )
        }
    }
    val on = tr("✓ 효과", "✓ Works", zh = "✓ 有效", hi = "✓ असर", es = "✓ Sí", fr = "✓ Oui", ar = "✓ يعمل", de = "✓ Ja", pt = "✓ Sim", ru = "✓ Да", ja = "✓ 効果")
    val off = tr("— 없음", "— None", zh = "— 无", hi = "— नहीं", es = "— No", fr = "— Non", ar = "— لا", de = "— Nein", pt = "— Não", ru = "— Нет", ja = "— なし")
    val look = tr("👀 모습만", "👀 Look only", zh = "👀 仅外观", hi = "👀 सिर्फ़ रूप", es = "👀 Solo look", fr = "👀 Look seul", ar = "👀 الشكل فقط", de = "👀 Nur Look", pt = "👀 Só visual", ru = "👀 Только вид", ja = "👀 見た目だけ")
    val rows = listOf(
        tr("💖 부활물약", "💖 Potion", zh = "💖 药水", hi = "💖 पोशन", es = "💖 Poción", fr = "💖 Potion", ar = "💖 جرعة", de = "💖 Trank", pt = "💖 Poção", ru = "💖 Зелье", ja = "💖 復活薬") to listOf(true, true, true),
        tr("🚀 ×3 부스터", "🚀 x3 Booster", zh = "🚀 ×3 加成", hi = "🚀 ×3 बूस्टर", es = "🚀 x3", fr = "🚀 Booster x3", ar = "🚀 ×3", de = "🚀 x3-Booster", pt = "🚀 x3", ru = "🚀 Бустер x3", ja = "🚀 ×3") to listOf(true, false, false),
        tr("🐾 동물 친구", "🐾 Animals", zh = "🐾 动物", hi = "🐾 जानवर", es = "🐾 Animales", fr = "🐾 Animaux", ar = "🐾 حيوانات", de = "🐾 Tiere", pt = "🐾 Animais", ru = "🐾 Зверята", ja = "🐾 どうぶつ") to listOf(true, null, false),
    )
    SoftCard {
        Text(
            tr("✨ 아이템은 어디서 효과가 있나요?", "✨ Where do items work?", zh = "✨ 道具在哪里有效？", hi = "✨ आइटम कहाँ काम करते हैं?", es = "✨ ¿Dónde funcionan los objetos?", fr = "✨ Où marchent les objets ?", ar = "✨ أين تعمل العناصر؟", de = "✨ Wo wirken Items?", pt = "✨ Onde os itens funcionam?", ru = "✨ Где работают предметы?", ja = "✨ アイテムはどこで効く？"),
            fontWeight = FontWeight.Black, color = Ui.text, fontSize = 16.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1.3f))
            listOf(
                tr("🎲 랜덤", "🎲 Random", zh = "🎲 随机", hi = "🎲 रैंडम", es = "🎲 Aleatoria", fr = "🎲 Aléatoire", ar = "🎲 عشوائي", de = "🎲 Zufall", pt = "🎲 Aleatória", ru = "🎲 Случайный", ja = "🎲 ランダム"),
                tr("🤖 AI", "🤖 AI", zh = "🤖 AI", es = "🤖 IA", fr = "🤖 IA", ar = "🤖 AI", de = "🤖 KI", pt = "🤖 IA", ru = "🤖 ИИ", ja = "🤖 AI"),
                tr("👫 친구", "👫 Friend", zh = "👫 好友", hi = "👫 दोस्त", es = "👫 Amigo", fr = "👫 Ami", ar = "👫 صديق", de = "👫 Freund", pt = "👫 Amigo", ru = "👫 Друг", ja = "👫 友だち"),
            ).forEach { h ->
                Text(h, Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ui.text.copy(alpha = 0.75f), textAlign = TextAlign.Center, maxLines = 1)
            }
        }
        rows.forEach { (label, cols) ->
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1.3f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ui.text, maxLines = 1)
                cols.forEach { c ->
                    when (c) {
                        true -> Cell(on, yes, yes.copy(alpha = 0.13f), Modifier.weight(1f))
                        false -> Cell(off, no, Ui.text.copy(alpha = 0.06f), Modifier.weight(1f))
                        null -> Cell(look, Ui.accent, Ui.accent.copy(alpha = 0.12f), Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            tr(
                "🤖 AI 대전과 👫 친구 대전에서는 부스터와 동물이 쓰이지 않아요. 개수도 줄지 않으니 안심하세요!",
                "🤖 AI and 👫 friend battles don't use boosters or animals, so you won't lose any!",
                zh = "🤖 AI 对战和 👫 好友对战不会使用加成和动物，数量也不会减少，放心吧！",
                hi = "🤖 AI और 👫 दोस्त मुकाबलों में बूस्टर और जानवर इस्तेमाल नहीं होते, इसलिए घटते भी नहीं!",
                es = "🤖 En batallas contra la IA y 👫 entre amigos no se usan potenciadores ni animales, ¡no pierdes ninguno!",
                fr = "🤖 Les combats contre l’IA et 👫 entre amis n’utilisent ni boosters ni animaux : tu n’en perds aucun !",
                ar = "🤖 معارك الذكاء الاصطناعي و👫 الأصدقاء لا تستخدم المضاعِف ولا الحيوانات، فلن تخسر شيئًا!",
                de = "🤖 KI- und 👫 Freundesduelle verbrauchen keine Booster oder Tiere – du verlierst also nichts!",
                pt = "🤖 Batalhas contra a IA e 👫 entre amigos não usam impulsos nem animais, então você não perde nenhum!",
                ru = "🤖 В боях с ИИ и 👫 с друзьями бустеры и зверята не тратятся — ничего не потеряете!",
                ja = "🤖 AI対戦と 👫 友だち対戦ではブースターとどうぶつは使われません。数も減らないので安心！",
            ),
            fontSize = 12.sp, color = Ui.text.copy(alpha = 0.75f), lineHeight = 17.sp,
        )
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ShopScreen(vm: AppViewModel) {
    val ui by vm.lobby.collectAsState()
    val p = ui.profile
    BackHandler(onBack = vm::closeShop)
    var confirm by remember { mutableStateOf<Purchase?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::closeShop) { Text(tr("← 로비", "← Lobby", zh = "← 大厅", hi = "← लॉबी", es = "← Sala", fr = "← Accueil", ar = "→ الردهة", de = "← Lobby", pt = "← Lobby", ru = "← Лобби", ja = "← ロビー"), color = Ui.text) }
            Spacer(Modifier.weight(1f))
            if (p != null) {
                Text(tr("내 점수 ", "My score ", zh = "我的分数 ", hi = "मेरा स्कोर ", es = "Mi puntuación ", fr = "Mon score ", ar = "نقاطي ", de = "Mein Punktestand ", pt = "Minha pontuação ", ru = "Мой счёт ", ja = "自分のスコア "), color = Ui.text, fontSize = 14.sp)
                Text(tr("${p.score}점", "${p.score} pts", zh = "${p.score} 分", hi = "${p.score} अंक", es = "${p.score} pts", fr = "${p.score} pts", ar = "${p.score} نقطة", de = "${p.score} Pkt", pt = "${p.score} pts", ru = "${p.score} очк.", ja = "${p.score}点"), color = Ui.primaryDark, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text("  💰 ${p.coins}", color = Color(0xFFC88A00), fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
        }
        Text(tr("🛒 상점", "🛒 Shop", zh = "🛒 商店", hi = "🛒 दुकान", es = "🛒 Tienda", fr = "🛒 Boutique", ar = "🛒 المتجر", de = "🛒 Shop", pt = "🛒 Loja", ru = "🛒 Магазин", ja = "🛒 ショップ"), fontSize = 34.sp, fontWeight = FontWeight.Black, color = Ui.primaryDark)
        Text(tr(
                "💰 상점 포인트를 먼저 쓰고, 모자라면 점수에서 써요 (점수가 줄면 계급이 내려갈 수 있어요)",
                "💰 Shop points are spent first, then your score (your rank can drop if your score does)",
                zh = "先使用 💰 商店积分，不够时再扣分数（分数减少等级可能下降）",
                hi = "पहले 💰 शॉप पॉइंट खर्च होंगे, फिर आपका स्कोर (स्कोर घटा तो रैंक भी घट सकती है)",
                es = "Primero se gastan los 💰 puntos de tienda y luego tu puntuación (tu rango puede bajar)",
                fr = "Les 💰 points boutique sont utilisés d’abord, puis ton score (ton rang peut baisser)",
                ar = "تُستخدم 💰 نقاط المتجر أولًا ثم نقاطك (قد تنخفض رتبتك)",
                de = "Zuerst werden 💰 Shop-Punkte verwendet, dann dein Punktestand (dein Rang kann sinken)",
                pt = "Primeiro são usados os 💰 pontos da loja, depois sua pontuação (seu rank pode cair)",
                ru = "Сначала тратятся 💰 очки магазина, потом ваш счёт (ранг может понизиться)",
                ja = "💰 ショップポイントから先に使い、足りない分はスコアから（スコアが減るとランクが下がることも）",
            ), fontSize = 12.sp, color = Ui.text.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        ui.info?.let { Text(it, color = Ui.accent, fontWeight = FontWeight.Bold) }
        ui.error?.let { Text(it, color = Ui.danger, fontWeight = FontWeight.Bold) }
        if (p == null) return@Column

        PointsCard(vm)
        ItemUsageCard()

        // 부활물약
        ShopCard(
            title = tr("💖 부활물약", "💖 Revive Potion", zh = "💖 复活药水", hi = "💖 रिवाइव पोशन", es = "💖 Poción de revivir", fr = "💖 Potion de résurrection", ar = "💖 جرعة الإحياء", de = "💖 Wiederbelebungstrank", pt = "💖 Poção de reviver", ru = "💖 Зелье оживления", ja = "💖 復活薬"),
            desc = tr(
"내 차례에 터진(0마리) 방울 하나를 1마리로 살려요.\n한 판에 최대 ${Shop.MAX_REVIVES_PER_GAME}번 (랜덤 매칭 1번) · 쓸 때마다 1개 소모 · 온라인 대전 전용",
"On your turn, bring a popped (empty) bubble back with 1 critter.\nUp to ${Shop.MAX_REVIVES_PER_GAME} per game (1 in random matches) · 1 used each time · online only",
zh = "在你的回合，让一个破掉（空）的泡泡带着 1 只复活。\n每局最多 ${Shop.MAX_REVIVES_PER_GAME} 次（随机匹配 1 次） · 每次消耗 1 瓶 · 仅限在线对战", hi = "अपनी बारी में फटे (खाली) बबल को 1 जीव के साथ वापस लाएँ।\nहर गेम में अधिकतम ${Shop.MAX_REVIVES_PER_GAME} (रैंडम मैच में 1) · हर बार 1 खर्च · सिर्फ़ ऑनलाइन", es = "En tu turno, revive una burbuja reventada (vacía) con 1 bichito.\nHasta ${Shop.MAX_REVIVES_PER_GAME} por partida (1 en aleatorias) · gasta 1 cada vez · solo en línea", fr = "À ton tour, ranime une bulle éclatée (vide) avec 1 bestiole.\nJusqu’à ${Shop.MAX_REVIVES_PER_GAME} par partie (1 en aléatoire) · 1 utilisée à chaque fois · en ligne uniquement", ar = "في دورك، أعِد فقاعة منفجرة (فارغة) بمخلوق واحد.\nحتى ${Shop.MAX_REVIVES_PER_GAME} في كل مباراة (1 في العشوائية) · تُستهلك واحدة كل مرة · عبر الإنترنت فقط", de = "Hol in deinem Zug eine geplatzte (leere) Blase mit 1 Tierchen zurück.\nBis zu ${Shop.MAX_REVIVES_PER_GAME} pro Partie (1 im Zufallsmatch) · 1 pro Einsatz · nur online", pt = "Na sua vez, reviva uma bolha estourada (vazia) com 1 bichinho.\nAté ${Shop.MAX_REVIVES_PER_GAME} por partida (1 em aleatórias) · gasta 1 por uso · só online", ru = "В свой ход верните лопнувший (пустой) пузырь с 1 зверьком.\nДо ${Shop.MAX_REVIVES_PER_GAME} за партию (1 в случайном) · 1 за раз · только онлайн", ja = "自分の番に、割れた（空の）バブルを1匹で復活させます。\n1ゲーム最大${Shop.MAX_REVIVES_PER_GAME}回（ランダムマッチは1回） · 使うたびに1個消費 · オンライン対戦のみ",
),
            price = Shop.POTION_PRICE,
            owned = tr("보유 ${p.potions}개", "Owned: ${p.potions}", zh = "拥有：${p.potions}", hi = "पास में: ${p.potions}", es = "Tienes: ${p.potions}", fr = "Possédées : ${p.potions}", ar = "لديك: ${p.potions}", de = "Im Besitz: ${p.potions}", pt = "Você tem: ${p.potions}", ru = "Есть: ${p.potions}", ja = "所持：${p.potions}個"),
            art = { PotionArt(Modifier.size(84.dp)) },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // 묶음 할인: 1 · 3 · 5 · 10개
                BundleButtons("POTION", p.score + p.coins, !ui.shopBusy) { qty, price ->
                    val name = tr("부활물약", "Revive Potion", zh = "复活药水", hi = "रिवाइव पोशन", es = "Poción de revivir", fr = "Potion de résurrection", ar = "جرعة الإحياء", de = "Wiederbelebungstrank", pt = "Poção de reviver", ru = "Зелье оживления", ja = "復活薬")
                    confirm = Purchase("POTION", if (qty == 1) name else "$name ×$qty", price, qty)
                }
                PotionAdButton(vm, p)
            }
        }

        // ✏️ 닉네임 변경권
        ShopCard(
            title = tr("✏️ 닉네임 변경권", "✏️ Rename Ticket", zh = "✏️ 改名券", es = "✏️ Cupón de cambio de apodo", fr = "✏️ Ticket de pseudo",
                de = "✏️ Namensticket", pt = "✏️ Cupom de troca de apelido", ru = "✏️ Талон смены ника", ja = "✏️ ニックネーム変更券"),
            desc = tr("닉네임을 한 번 바꿀 수 있어요. 첫 변경은 무료!", "Change your nickname once. Your first change is free!",
                zh = "可以修改一次昵称。第一次免费！", es = "Cambia tu apodo una vez. ¡El primer cambio es gratis!", fr = "Change ton pseudo une fois. Le premier est gratuit !",
                de = "Ändere deinen Namen einmal. Die erste Änderung ist gratis!", pt = "Troque seu apelido uma vez. A primeira é grátis!",
                ru = "Смените ник один раз. Первая смена бесплатно!", ja = "ニックネームを1回変更できます。最初の変更は無料！") +
                (if (!p.freeRenameUsed) "\n" + tr("🎁 아직 무료 변경이 남아 있어요", "🎁 You still have a free change", zh = "🎁 你还有一次免费修改", es = "🎁 Aún tienes un cambio gratis",
                    fr = "🎁 Il te reste un changement gratuit", de = "🎁 Du hast noch eine Gratis-Änderung", pt = "🎁 Você ainda tem uma troca grátis",
                    ru = "🎁 У вас ещё есть бесплатная смена", ja = "🎁 無料変更がまだ残っています") else ""),
            price = Shop.RENAME_PRICE,
            owned = if (p.renameTickets > 0) tr("보유 ${p.renameTickets}장", "Owned: ${p.renameTickets}", zh = "拥有 ${p.renameTickets} 张", es = "Tienes: ${p.renameTickets}",
                fr = "Possédés : ${p.renameTickets}", de = "Im Besitz: ${p.renameTickets}", pt = "Você tem: ${p.renameTickets}", ru = "Есть: ${p.renameTickets}", ja = "所持 ${p.renameTickets}枚") else null,
            art = { Text("✏️", fontSize = 54.sp) },
        ) {
            Button(
                onClick = { confirm = Purchase("RENAME", tr("닉네임 변경권", "Rename Ticket", zh = "改名券", es = "Cupón de cambio", fr = "Ticket de pseudo", de = "Namensticket", pt = "Cupom de troca", ru = "Талон смены ника", ja = "変更券"), Shop.RENAME_PRICE) },
                enabled = !ui.shopBusy && p.score + p.coins >= Shop.RENAME_PRICE,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ui.primary),
            ) { Text(tr("${Shop.RENAME_PRICE}점에 사기", "Buy for ${Shop.RENAME_PRICE} pts", zh = "${Shop.RENAME_PRICE} 分购买", es = "Comprar por ${Shop.RENAME_PRICE} pts", fr = "Acheter pour ${Shop.RENAME_PRICE} pts", de = "Für ${Shop.RENAME_PRICE} Pkt kaufen", pt = "Comprar por ${Shop.RENAME_PRICE} pts", ru = "Купить за ${Shop.RENAME_PRICE} очк.", ja = "${Shop.RENAME_PRICE}点で買う"), fontWeight = FontWeight.Bold) }
        }

        // ×3 점수 부스터
        ShopCard(
            title = tr("×3 점수 부스터", "x3 Score Booster", zh = "×3 分数加成", hi = "×3 स्कोर बूस्टर", es = "Potenciador x3",
                fr = "Booster x3", ar = "مضاعِف النقاط ×3", de = "x3-Punkte-Booster", pt = "Impulso de pontos x3",
                ru = "Бустер очков x3", ja = "×3 スコアブースター"),
            desc = tr("켜두면 다음 랜덤 매칭에서 1개를 쓰고, 이기면 얻는 점수가 3배! (AI 대전에는 쓰지 않아요)",
                "When on, one is used in your next random match — win and your points are tripled! (Not used in AI battles)",
                zh = "开启后，下一局随机匹配会使用 1 个，获胜得分翻 3 倍！（AI 对战不使用）",
                hi = "चालू होने पर अगले रैंडम मैच में 1 इस्तेमाल होगा — जीते तो अंक 3 गुना! (AI मुकाबलों में नहीं)",
                es = "Activado, se usa uno en tu próxima partida aleatoria: ¡si ganas, puntos x3! (No se usa contra la IA)",
                fr = "Activé, un est utilisé à ton prochain match aléatoire : gagne et tes points sont triplés ! (Pas contre l’IA)",
                ar = "عند تشغيله يُستخدم واحد في مباراتك العشوائية التالية — فُز وتتضاعف نقاطك 3 مرات! (لا يُستخدم ضد الذكاء الاصطناعي)",
                de = "Eingeschaltet wird im nächsten Zufallsmatch einer verbraucht — gewinnst du, gibt es 3-fache Punkte! (Nicht gegen die KI)",
                pt = "Ligado, um é usado na próxima partida aleatória — vença e seus pontos triplicam! (Não é usado contra a IA)",
                ru = "Если включён, в следующем случайном матче тратится 1 — победите, и очки утроятся! (В боях с ИИ не тратится)",
                ja = "ONにすると次のランダムマッチで1個使い、勝つと獲得点数が3倍！（AI対戦では使いません）"),
            price = Shop.BOOST_PRICE,
            owned = if (p.boosts > 0) {
                tr("보유 ${p.boosts}개", "Owned: ${p.boosts}", zh = "拥有 ${p.boosts} 个", hi = "पास में: ${p.boosts}", es = "Tienes: ${p.boosts}",
                    fr = "Possédés : ${p.boosts}", ar = "لديك: ${p.boosts}", de = "Im Besitz: ${p.boosts}", pt = "Você tem: ${p.boosts}",
                    ru = "Есть: ${p.boosts}", ja = "所持 ${p.boosts}個") + if (p.boostOn) tr("  ·  🔥 사용 중", "  ·  🔥 On",
                    zh = "  ·  🔥 使用中", hi = "  ·  🔥 चालू", es = "  ·  🔥 Activo", fr = "  ·  🔥 Activé", ar = "  ·  🔥 مُفعَّل",
                    de = "  ·  🔥 An", pt = "  ·  🔥 Ligado", ru = "  ·  🔥 Вкл.", ja = "  ·  🔥 使用中") else ""
            } else {
                null
            },
            art = { BoostArt() },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { confirm = Purchase("BOOST", tr("×3 부스터", "x3 booster", zh = "×3 加成", hi = "×3 बूस्टर", es = "potenciador x3",
                        fr = "booster x3", ar = "المضاعِف ×3", de = "x3-Booster", pt = "impulso x3", ru = "бустер x3", ja = "×3 ブースター"), Shop.BOOST_PRICE) },
                    enabled = !ui.shopBusy && p.score + p.coins >= Shop.BOOST_PRICE,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ui.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(tr("${Shop.BOOST_PRICE}점에 사기", "Buy for ${Shop.BOOST_PRICE} pts", zh = "${Shop.BOOST_PRICE} 分购买",
                        hi = "${Shop.BOOST_PRICE} अंक में खरीदें", es = "Comprar por ${Shop.BOOST_PRICE} pts", fr = "Acheter pour ${Shop.BOOST_PRICE} pts",
                        ar = "اشترِ مقابل ${Shop.BOOST_PRICE} نقطة", de = "Für ${Shop.BOOST_PRICE} Pkt kaufen", pt = "Comprar por ${Shop.BOOST_PRICE} pts",
                        ru = "Купить за ${Shop.BOOST_PRICE} очк.", ja = "${Shop.BOOST_PRICE}点で買う"), fontWeight = FontWeight.Bold)
                }
                if (p.boosts > 0) {
                    OutlinedButton(onClick = { vm.setBoost(!p.boostOn) }, enabled = !ui.shopBusy, shape = RoundedCornerShape(16.dp)) {
                        Text(
                            if (p.boostOn) tr("끄기", "Turn off", zh = "关闭", hi = "बंद करें", es = "Desactivar", fr = "Désactiver",
                                ar = "إيقاف", de = "Ausschalten", pt = "Desligar", ru = "Выключить", ja = "OFFにする")
                            else tr("켜기", "Turn on", zh = "开启", hi = "चालू करें", es = "Activar", fr = "Activer",
                                ar = "تشغيل", de = "Einschalten", pt = "Ligar", ru = "Включить", ja = "ONにする"),
                            color = Ui.text,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(tr("🐾 동물 친구들", "🐾 Animal Friends", zh = "🐾 动物伙伴", hi = "🐾 जानवर दोस्त", es = "🐾 Amigos animales", fr = "🐾 Amis animaux", ar = "🐾 أصدقاء الحيوانات", de = "🐾 Tierfreunde", pt = "🐾 Amigos animais", ru = "🐾 Друзья-зверята", ja = "🐾 どうぶつの仲間"), fontSize = 22.sp, fontWeight = FontWeight.Black, color = Ui.text, modifier = Modifier.fillMaxWidth())
        Text(tr(
                "소모품: 1개 = 랜덤 매칭 1판. 장착한 채 매칭되면 1개를 쓰고, 그 판에 내 캐릭터가 바뀌고 상대의 턴이 짧아져요",
                "Consumable: 1 = one random match. If equipped when a match starts, one is used: you change character and your opponent's turn gets shorter",
                zh = "消耗品：1 个 = 1 局随机匹配。装备时匹配成功会消耗 1 个，该局你的角色会改变，对手回合变短",
                hi = "उपभोग्य: 1 = एक रैंडम मैच। लगा होने पर मैच शुरू होते ही 1 खर्च होगा: आपका किरदार बदलेगा और विरोधी की बारी छोटी होगी",
                es = "Consumible: 1 = una partida aleatoria. Si está equipado al empezar, se gasta uno: cambias de personaje y el turno rival se acorta",
                fr = "Consommable : 1 = un match aléatoire. S’il est équipé au début d’un match, un est utilisé : ton perso change et le tour adverse raccourcit",
                ar = "استهلاكي: 1 = مباراة عشوائية واحدة. إذا كان مجهّزًا عند بدء المباراة يُستخدم واحد: تتغير شخصيتك ويقصر دور خصمك",
                de = "Verbrauchsgegenstand: 1 = ein Zufallsmatch. Ist er beim Matchstart ausgerüstet, wird einer verbraucht: deine Figur ändert sich und der Gegnerzug wird kürzer",
                pt = "Consumível: 1 = uma partida aleatória. Se estiver equipado ao começar, um é usado: seu personagem muda e o turno do rival encurta",
                ru = "Расходник: 1 шт. = один случайный матч. Если надет при старте матча, тратится 1: меняется персонаж, а ход соперника короче",
                ja = "消耗品：1個 = ランダムマッチ1回。装備したままマッチすると1個使い、その試合はキャラが変わり相手のターンが短くなります",
            ), fontSize = 12.sp, color = Ui.text.copy(alpha = 0.7f), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))

        // 무료 햄스터 옷: 사지 않고 바로 장착 (모습만 바뀌고 효과 없음)
        val hamOn = p.equipped == Animal.HAMSTER
        ShopCard(
            title = tr("🐹 햄스터", "🐹 Hamster", zh = "🐹 仓鼠", hi = "🐹 हैम्स्टर", es = "🐹 Hámster", fr = "🐹 Hamster", ar = "🐹 هامستر", de = "🐹 Hamster", pt = "🐹 Hamster", ru = "🐹 Хомяк", ja = "🐹 ハムスター"),
            desc = tr(
                "🎁 무료! 내 캐릭터를 햄스터로 바꿔요.\n효과는 없고 몇 판을 해도 줄지 않아요 (모든 대전에서 모습만)",
                "🎁 Free! Turn your character into a hamster.\nNo effect and never used up (look only, in every battle)",
                zh = "🎁 免费！把你的角色换成仓鼠。\n没有效果，也不会消耗（所有对战中仅外观）",
                hi = "🎁 मुफ़्त! अपने किरदार को हैम्स्टर बनाएँ।\nकोई असर नहीं, कभी खत्म नहीं होता (हर मुकाबले में सिर्फ़ रूप)",
                es = "🎁 ¡Gratis! Convierte tu personaje en hámster.\nSin efecto y nunca se gasta (solo aspecto, en todas las batallas)",
                fr = "🎁 Gratuit ! Transforme ton perso en hamster.\nAucun effet, jamais consommé (look seulement, dans tous les combats)",
                ar = "🎁 مجانًا! حوّل شخصيتك إلى هامستر.\nبلا تأثير ولا يُستهلك أبدًا (الشكل فقط في كل المعارك)",
                de = "🎁 Gratis! Mach deine Figur zum Hamster.\nKein Effekt, wird nie verbraucht (nur Look, in jedem Duell)",
                pt = "🎁 Grátis! Transforme seu personagem em hamster.\nSem efeito e nunca acaba (só visual, em todas as batalhas)",
                ru = "🎁 Бесплатно! Превратите персонажа в хомяка.\nБез эффекта и не тратится (только вид, в любом бою)",
                ja = "🎁 無料！自分のキャラをハムスターに。\n効果なし・減りません（どの対戦でも見た目だけ）",
            ),
            price = null,
            owned = if (hamOn) tr("✅ 장착 중", "✅ Equipped", zh = "✅ 已装备", hi = "✅ लगाया हुआ", es = "✅ Equipado", fr = "✅ Équipé", ar = "✅ مُجهَّز", de = "✅ Ausgerüstet", pt = "✅ Equipado", ru = "✅ Надето", ja = "✅ 装備中") else null,
            art = { Critter(Side.HAMSTER, Modifier.size(84.dp), skin = Animal.HAMSTER, happy = hamOn) },
        ) {
            if (hamOn) {
                OutlinedButton(onClick = { vm.equip(null) }, enabled = !ui.shopBusy, shape = RoundedCornerShape(16.dp)) {
                    Text(tr("장착 해제", "Unequip", zh = "卸下", hi = "हटाएँ", es = "Quitar", fr = "Retirer", ar = "إزالة", de = "Ablegen", pt = "Desequipar", ru = "Снять", ja = "外す"), color = Ui.text)
                }
            } else {
                Button(
                    onClick = { vm.equip(Animal.HAMSTER) }, enabled = !ui.shopBusy, shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ui.accent),
                ) { Text(tr("무료로 장착", "Equip for free", zh = "免费装备", hi = "मुफ़्त में लगाएँ", es = "Equipar gratis", fr = "Équiper gratuitement", ar = "تجهيز مجانًا", de = "Gratis ausrüsten", pt = "Equipar grátis", ru = "Надеть бесплатно", ja = "無料で装備"), fontWeight = FontWeight.Bold) }
            }
        }

        // ✨ 유료 스킨: 한 번 사면 계속, 모습만 바뀜
        Spacer(Modifier.height(6.dp))
        Text(
            tr("✨ 스킨 (한 번 사면 계속)", "✨ Skins (yours forever)", zh = "✨ 皮肤（买一次永久拥有）", hi = "✨ स्किन (हमेशा के लिए)",
                es = "✨ Aspectos (para siempre)", fr = "✨ Skins (à vie)", ar = "✨ الأشكال (لك للأبد)", de = "✨ Skins (für immer)",
                pt = "✨ Skins (para sempre)", ru = "✨ Скины (навсегда)", ja = "✨ スキン（買い切り）"),
            fontWeight = FontWeight.Black, fontSize = 20.sp, color = Ui.text, modifier = Modifier.fillMaxWidth(),
        )
        Text(
            tr("승패에는 영향이 없어요. 💰 복냥이 · 🐼 판다 · 🐉 아기 용은 상점 포인트 보너스가 있어요",
                "No effect on winning. 💰 Lucky Cat, 🐼 Panda and 🐉 Dragon give shop-point bonuses",
                zh = "不影响胜负。💰 招财猫 · 🐼 熊猫 · 🐉 小龙有商店积分奖励", es = "No afectan al resultado. 💰 Gato, 🐼 Panda y 🐉 Dragón dan puntos de tienda",
                fr = "Aucun effet sur la victoire. 💰 Chat, 🐼 Panda et 🐉 Dragon donnent des points boutique", de = "Kein Einfluss auf Sieg. 💰 Katze, 🐼 Panda und 🐉 Drache geben Shop-Punkte",
                pt = "Não afetam a vitória. 💰 Gato, 🐼 Panda e 🐉 Dragão dão pontos da loja", ru = "На победу не влияют. 💰 Котик, 🐼 Панда и 🐉 Дракон дают очки магазина",
                ja = "勝敗には影響なし。💰 まねきネコ · 🐼 パンダ · 🐉 ドラゴンはポイントボーナスあり"),
            fontSize = 12.sp, color = Ui.text.copy(alpha = 0.7f), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Animal.entries.filter { it.cosmetic }.forEach { a ->
            val have = a in p.skins
            val on = p.equipped == a
            val side = if (a.looksLikeCat) Side.CAT else Side.HAMSTER
            ShopCard(
                title = a.label,
                desc = skinDesc(a, p),
                price = if (have) null else a.price,
                owned = when {
                    on -> tr("✅ 장착 중", "✅ Equipped", zh = "✅ 已装备", hi = "✅ लगाया हुआ", es = "✅ Equipado", fr = "✅ Équipé", ar = "✅ مُجهَّز", de = "✅ Ausgerüstet", pt = "✅ Equipado", ru = "✅ Надето", ja = "✅ 装備中")
                    have -> tr("💎 보유 중", "💎 Owned", zh = "💎 已拥有", es = "💎 Tuyo", fr = "💎 Possédé", de = "💎 Im Besitz", pt = "💎 Seu", ru = "💎 Есть", ja = "💎 所持中")
                    else -> null
                },
                art = { Critter(side, Modifier.size(84.dp), skin = a, happy = on, ribbons = if (a == Animal.RABBIT) p.winStreak.coerceIn(0, Shop.RABBIT_MAX_RIBBONS).coerceAtLeast(if (have) 0 else 3) else 0) },
            ) {
                when {
                    on -> OutlinedButton(onClick = { vm.equip(null) }, enabled = !ui.shopBusy, shape = RoundedCornerShape(16.dp)) {
                        Text(tr("장착 해제", "Unequip", zh = "卸下", hi = "हटाएँ", es = "Quitar", fr = "Retirer", ar = "إزالة", de = "Ablegen", pt = "Desequipar", ru = "Снять", ja = "外す"), color = Ui.text)
                    }
                    have -> Button(
                        onClick = { vm.equip(a) }, enabled = !ui.shopBusy, shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Ui.accent),
                    ) { Text(tr("장착하기", "Equip", zh = "装备", hi = "लगाएँ", es = "Equipar", fr = "Équiper", ar = "تجهيز", de = "Ausrüsten", pt = "Equipar", ru = "Надеть", ja = "装備する"), fontWeight = FontWeight.Bold) }
                    else -> Button(
                        onClick = { confirm = Purchase(a.name, a.label, a.price) },
                        enabled = !ui.shopBusy && p.score + p.coins >= a.price,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Ui.primary),
                    ) { Text(tr("${a.price}점에 사기", "Buy for ${a.price} pts", zh = "${a.price} 分购买", hi = "${a.price} अंक में खरीदें", es = "Comprar por ${a.price} pts", fr = "Acheter pour ${a.price} pts", ar = "اشترِ مقابل ${a.price} نقطة", de = "Für ${a.price} Pkt kaufen", pt = "Comprar por ${a.price} pts", ru = "Купить за ${a.price} очк.", ja = "${a.price}点で買う"), fontWeight = FontWeight.Bold) }
                }
            }
        }

        // 🦄 계급 캐릭터: 팔지 않고 다이아 계급이면 무료로 장착
        Animal.entries.filter { it.rankOnly }.forEach { a ->
            val unlocked = p.score >= a.rankMin
            val on = p.equipped == a
            ShopCard(
                title = a.label,
                desc = unicornDesc(p, a.rankMin),
                price = null,
                owned = when {
                    on -> tr("✅ 장착 중", "✅ Equipped", zh = "✅ 已装备", es = "✅ Equipado", fr = "✅ Équipé", de = "✅ Ausgerüstet", pt = "✅ Equipado", ru = "✅ Надето", ja = "✅ 装備中")
                    unlocked -> tr("💎 해금됨", "💎 Unlocked", zh = "💎 已解锁", es = "💎 Desbloqueado", fr = "💎 Débloqué", de = "💎 Freigeschaltet", pt = "💎 Desbloqueado", ru = "💎 Открыто", ja = "💎 解放済み")
                    else -> tr("🔒 앞으로 ${a.rankMin - p.score}점", "🔒 ${a.rankMin - p.score} pts to go", zh = "🔒 还差 ${a.rankMin - p.score} 分", es = "🔒 Faltan ${a.rankMin - p.score} pts",
                        fr = "🔒 Encore ${a.rankMin - p.score} pts", de = "🔒 Noch ${a.rankMin - p.score} Pkt", pt = "🔒 Faltam ${a.rankMin - p.score} pts", ru = "🔒 Ещё ${a.rankMin - p.score} очк.", ja = "🔒 あと${a.rankMin - p.score}点")
                },
                art = { Critter(Side.HAMSTER, Modifier.size(84.dp).alpha(if (unlocked) 1f else 0.55f), skin = a, happy = on) },
            ) {
                when {
                    on -> OutlinedButton(onClick = { vm.equip(null) }, enabled = !ui.shopBusy, shape = RoundedCornerShape(16.dp)) {
                        Text(tr("장착 해제", "Unequip", zh = "卸下", es = "Quitar", fr = "Retirer", de = "Ablegen", pt = "Desequipar", ru = "Снять", ja = "外す"), color = Ui.text)
                    }
                    else -> Button(
                        onClick = { vm.equip(a) }, enabled = unlocked && !ui.shopBusy, shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Ui.accent),
                    ) { Text(if (unlocked) tr("장착하기", "Equip", zh = "装备", es = "Equipar", fr = "Équiper", de = "Ausrüsten", pt = "Equipar", ru = "Надеть", ja = "装備する") else "🔒", fontWeight = FontWeight.Bold) }
                }
            }
        }

        Text(tr("🐾 소모품 동물", "🐾 Consumable animals", zh = "🐾 消耗品动物", es = "🐾 Animales consumibles", fr = "🐾 Animaux consommables",
            de = "🐾 Verbrauchs-Tiere", pt = "🐾 Animais consumíveis", ru = "🐾 Расходные зверята", ja = "🐾 消耗品どうぶつ"),
            fontWeight = FontWeight.Black, fontSize = 17.sp, color = Ui.text, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        Animal.entries.filter { it.consumable }.forEach { a ->
            val count = p.animalCount(a)
            val have = count > 0
            val on = p.equipped == a && have
            ShopCard(
                title = a.label,
                desc = tr(
"⏱ 상대 턴 시간 ${Shop.DEFAULT_TURN_SEC}초 → ${a.opponentTurnSec}초" + when (a) { Animal.SHEEP -> " · 이기면 구름 둥실"; Animal.DOG -> " · 이기면 발자국 콩콩"; else -> " · 이기면 잎사귀 회오리" },
"⏱ Opponent turn ${Shop.DEFAULT_TURN_SEC}s → ${a.opponentTurnSec}s" + when (a) { Animal.SHEEP -> " · win: floating clouds"; Animal.DOG -> " · win: paw prints"; else -> " · win: leaf whirl" },
zh = "⏱ 对手回合 ${Shop.DEFAULT_TURN_SEC}秒 → ${a.opponentTurnSec}秒", hi = "⏱ विरोधी की बारी ${Shop.DEFAULT_TURN_SEC}से → ${a.opponentTurnSec}से", es = "⏱ Turno del rival ${Shop.DEFAULT_TURN_SEC}s → ${a.opponentTurnSec}s", fr = "⏱ Tour adverse ${Shop.DEFAULT_TURN_SEC}s → ${a.opponentTurnSec}s", ar = "⏱ دور الخصم ${Shop.DEFAULT_TURN_SEC}ث ← ${a.opponentTurnSec}ث", de = "⏱ Gegnerzug ${Shop.DEFAULT_TURN_SEC}s → ${a.opponentTurnSec}s", pt = "⏱ Turno do rival ${Shop.DEFAULT_TURN_SEC}s → ${a.opponentTurnSec}s", ru = "⏱ Ход соперника ${Shop.DEFAULT_TURN_SEC}с → ${a.opponentTurnSec}с", ja = "⏱ 相手のターン ${Shop.DEFAULT_TURN_SEC}秒 → ${a.opponentTurnSec}秒",
),
                price = a.price,
                owned = when {
                    have -> tr("보유 ${count}개", "Owned: $count", zh = "拥有 $count 个", hi = "पास में: $count", es = "Tienes: $count",
                        fr = "Possédés : $count", ar = "لديك: $count", de = "Im Besitz: $count", pt = "Você tem: $count",
                        ru = "Есть: $count", ja = "所持 ${count}個") + if (on) tr("  ·  ✅ 장착 중", "  ·  ✅ Equipped", zh = "  ·  ✅ 已装备",
                        hi = "  ·  ✅ लगाया हुआ", es = "  ·  ✅ Equipado", fr = "  ·  ✅ Équipé", ar = "  ·  ✅ مُجهَّز", de = "  ·  ✅ Ausgerüstet",
                        pt = "  ·  ✅ Equipado", ru = "  ·  ✅ Надето", ja = "  ·  ✅ 装備中") else ""
                    else -> null
                },
                art = { Critter(Side.CAT, Modifier.size(84.dp), skin = a, happy = on) },
            ) {
                // 묶음 할인: 1 · 3개
                BundleButtons(a.name, p.score + p.coins, !ui.shopBusy) { qty, price ->
                    confirm = Purchase(a.name, if (qty == 1) a.label else "${a.label} ×$qty", price, qty)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (on) {
                        OutlinedButton(onClick = { vm.equip(null) }, enabled = !ui.shopBusy, shape = RoundedCornerShape(16.dp)) {
                            Text(tr("장착 해제", "Unequip", zh = "卸下", hi = "हटाएँ", es = "Quitar", fr = "Retirer", ar = "إزالة", de = "Ablegen", pt = "Desequipar", ru = "Снять", ja = "外す"), color = Ui.text)
                        }
                    } else if (have) {
                        Button(
                            onClick = { vm.equip(a) }, enabled = !ui.shopBusy, shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Ui.accent),
                        ) { Text(tr("장착하기", "Equip", zh = "装备", hi = "लगाएँ", es = "Equipar", fr = "Équiper", ar = "تجهيز", de = "Ausrüsten", pt = "Equipar", ru = "Надеть", ja = "装備する"), fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }

    confirm?.let { (item, label, price, qty) ->
        // 상점 포인트 먼저, 모자라면 점수 (서버와 같은 규칙)
        val fromCoins = minOf(p?.coins ?: 0, price)
        val fromScore = price - fromCoins
        val after = (p?.score ?: 0) - fromScore
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(tr("$label 살까요?", "Buy $label?", zh = "购买 ${label}？", hi = "$label खरीदें?", es = "¿Comprar $label?", fr = "Acheter $label ?", ar = "شراء ${label}؟", de = "$label kaufen?", pt = "Comprar $label?", ru = "Купить $label?", ja = "${label}を買いますか？"), fontWeight = FontWeight.Black) },
            text = {
                Column {
                    Text(tr(
                        "💰 상점 포인트 $fromCoins + 점수 $fromScore 사용\n남는 점수: ${after}점",
                        "Uses 💰 $fromCoins shop points + $fromScore score\nScore left: $after",
                        zh = "使用 💰 商店积分 $fromCoins + 分数 $fromScore\n剩余分数：$after",
                        hi = "💰 $fromCoins शॉप पॉइंट + $fromScore स्कोर लगेगा\nबचा स्कोर: $after",
                        es = "Usa 💰 $fromCoins puntos de tienda + $fromScore de puntuación\nPuntuación restante: $after",
                        fr = "Utilise 💰 $fromCoins points boutique + $fromScore de score\nScore restant : $after",
                        ar = "يستخدم 💰 $fromCoins من نقاط المتجر + $fromScore من النقاط\nالمتبقي: $after",
                        de = "Nutzt 💰 $fromCoins Shop-Punkte + $fromScore Punktestand\nVerbleibend: $after",
                        pt = "Usa 💰 $fromCoins pontos da loja + $fromScore de pontuação\nPontuação restante: $after",
                        ru = "Будет списано 💰 $fromCoins очков магазина + $fromScore счёта\nОстанется: $after",
                        ja = "💰 ショップポイント${fromCoins} + スコア${fromScore}を使用\n残りスコア：$after",
                    ), fontWeight = FontWeight.Bold)
                    if (p != null && Rank.of(after) != p.rank) {
                        Spacer(Modifier.height(6.dp))
                        Text(tr(
"⚠️ 계급이 ${p.rank.label} → ${Rank.of(after).label}(으)로 내려가요",
"⚠️ Your rank will drop from ${p.rank.label} to ${Rank.of(after).label}",
zh = "⚠️ 你的等级会从${p.rank.label}降到${Rank.of(after).label}", hi = "⚠️ आपकी रैंक ${p.rank.label} से ${Rank.of(after).label} हो जाएगी", es = "⚠️ Tu rango bajará de ${p.rank.label} a ${Rank.of(after).label}", fr = "⚠️ Ton rang passera de ${p.rank.label} à ${Rank.of(after).label}", ar = "⚠️ ستنخفض رتبتك من ${p.rank.label} إلى ${Rank.of(after).label}", de = "⚠️ Dein Rang sinkt von ${p.rank.label} auf ${Rank.of(after).label}", pt = "⚠️ Seu rank vai cair de ${p.rank.label} para ${Rank.of(after).label}", ru = "⚠️ Ранг понизится: ${p.rank.label} → ${Rank.of(after).label}", ja = "⚠️ ランクが${p.rank.label}から${Rank.of(after).label}に下がります",
), color = Ui.danger, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.buy(item, label, qty); confirm = null }) { Text(tr("살게요", "Buy", zh = "购买", hi = "खरीदें", es = "Comprar", fr = "Acheter", ar = "شراء", de = "Kaufen", pt = "Comprar", ru = "Купить", ja = "買う")) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(tr("취소", "Cancel", zh = "取消", hi = "रद्द करें", es = "Cancelar", fr = "Annuler", ar = "إلغاء", de = "Abbrechen", pt = "Cancelar", ru = "Отмена", ja = "キャンセル")) } },
        )
    }
}

/** 상점에서 살 것 (묶음이면 qty개) */
private data class Purchase(val item: String, val label: String, val price: Int, val qty: Int = 1)

/** 묶음 할인 버튼들: "1개 50점", "3개 135점 −10%" … 두 개씩 한 줄 */
@Composable
private fun BundleButtons(item: String, budget: Int, enabled: Boolean, onPick: (qty: Int, price: Int) -> Unit) {
    val list = Shop.bundles(item)
    val unit = list.first().second
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        list.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (qty, price) ->
                    val off = 100 - price * 100 / (unit * qty)
                    Button(
                        onClick = { onPick(qty, price) },
                        enabled = enabled && budget >= price,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (qty == 1) Ui.primary else Ui.accent),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        // 글자 크기를 키운 폰에서도 잘리지 않게: 개수 / 가격 / 할인을 줄을 나눠 표시
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                tr("${qty}개", "×$qty", zh = "${qty} 个", ja = "${qty}個"),
                                fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 1, softWrap = false,
                            )
                            Text(
                                tr("${price}점", "$price pts", zh = "${price} 分", es = "$price pts", fr = "$price pts",
                                    de = "$price Pkt", pt = "$price pts", ru = "$price очк.", ja = "${price}点"),
                                fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false,
                            )
                            if (off > 0) Text("🔥 −$off%", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Ui.gold, maxLines = 1, softWrap = false)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ShopCard(
    title: String,
    desc: String,
    /** 점수 가격 (null이면 표시 안 함) */
    price: Int?,
    owned: String?,
    art: @Composable () -> Unit,
    action: @Composable () -> Unit,
) {
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(96.dp).background(Ui.bgTop, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) { art() }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 제목이 길면 제목만 줄바꿈되고 가격은 한 줄로
                    Text(title, fontWeight = FontWeight.Black, fontSize = 20.sp, color = Ui.text, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
if (price != null) Text(maxLines = 1, softWrap = false, text = tr("${price}점", "${price} pts", zh = "${price} 分", hi = "${price} अंक", es = "${price} pts", fr = "${price} pts", ar = "${price} نقطة", de = "${price} Pkt", pt = "${price} pts", ru = "${price} очк.", ja = "${price}点"), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ui.primaryDark)
                }
                Text(desc, fontSize = 13.sp, color = Ui.text.copy(alpha = 0.8f), lineHeight = 18.sp)
                if (owned != null) Text(owned, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ui.accent)
                Spacer(Modifier.height(6.dp))
                action()
            }
        }
    }
}

/** 하트 모양 병에 담긴 부활물약. */
@Composable
private fun PotionArt(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "potion")
    val wobble by t.animateFloat(-5f, 5f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "w")
    val tw by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1_200)), label = "tw")
    androidx.compose.foundation.Canvas(modifier.graphicsLayer { rotationZ = wobble }) {
        val w = size.width
        val h = size.height
        val c = androidx.compose.ui.geometry.Offset(w / 2, h * 0.6f)
        val r = w * 0.3f
        // 병목, 코르크
        drawRect(Color(0xFFE3F2FD), androidx.compose.ui.geometry.Offset(w * 0.42f, h * 0.18f), androidx.compose.ui.geometry.Size(w * 0.16f, h * 0.2f))
        drawRoundRect(Color(0xFFB7794A), androidx.compose.ui.geometry.Offset(w * 0.4f, h * 0.1f), androidx.compose.ui.geometry.Size(w * 0.2f, h * 0.1f), androidx.compose.ui.geometry.CornerRadius(6f))
        // 병
        drawCircle(Color(0xFFE3F2FD), r, c)
        // 하트 물약
        val heart = androidx.compose.ui.graphics.Path().apply {
            val hx = c.x
            val hy = c.y - r * 0.15f
            val k = r * 0.7f
            moveTo(hx, hy + k * 0.9f)
            cubicTo(hx - k * 1.3f, hy, hx - k * 0.6f, hy - k * 0.9f, hx, hy - k * 0.3f)
            cubicTo(hx + k * 0.6f, hy - k * 0.9f, hx + k * 1.3f, hy, hx, hy + k * 0.9f)
            close()
        }
        drawPath(heart, Color(0xFFFF5C8A))
        drawCircle(Color.White.copy(alpha = 0.8f), r * 0.14f, c + androidx.compose.ui.geometry.Offset(-r * 0.45f, -r * 0.45f))
        drawCircle(Color.White, r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
        val a = kotlin.math.sin(tw * Math.PI).toFloat()
        sparkle(c + androidx.compose.ui.geometry.Offset(r * 0.9f, -r * 0.9f), 9f * a + 2f, Ui.gold.copy(alpha = a))
        sparkle(c + androidx.compose.ui.geometry.Offset(-r * 1.0f, r * 0.2f), 6f * (1 - a) + 2f, Color.White.copy(alpha = 1 - a))
    }
}

/** 이번 판에 남은 부활물약 (없으면 표시 안 함). */
@Composable
private fun ReviveCounter(left: Int) {
    if (left <= 0) return
    Spacer(Modifier.width(6.dp))
    Text(
        "💖×$left",
        fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFFE0457B),
        modifier = Modifier
            .background(Color(0x33FF5C8A), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** 계급표 팝업: 모든 계급과 점수 범위, 내 계급, 다음 계급까지 남은 점수. */
@Composable
private fun RanksDialog(myScore: Int, onDismiss: () -> Unit) {
    val mine = Rank.of(myScore)
    val next = Rank.entries.getOrNull(mine.ordinal + 1)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("닫기", "Close", zh = "关闭", hi = "बंद करें", es = "Cerrar", fr = "Fermer", ar = "إغلاق", de = "Schließen", pt = "Fechar", ru = "Закрыть", ja = "閉じる"), fontWeight = FontWeight.Bold) } },
        title = { Text(tr("🏅 계급표", "🏅 Ranks", zh = "🏅 等级表", hi = "🏅 रैंक", es = "🏅 Rangos", fr = "🏅 Rangs", ar = "🏅 الرتب", de = "🏅 Ränge", pt = "🏅 Ranks", ru = "🏅 Ранги", ja = "🏅 ランク表"), fontWeight = FontWeight.Black, fontSize = 24.sp) },
        text = {
            // 계급이 8개라 작은 화면에선 길어짐 → 스크롤
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Rank.entries.reversed().forEach { r ->
                    val isMine = r == mine
                    val range = when (r) {
                        Rank.DIRT -> tr("1000점 이하", "Up to 1000 pts", zh = "1000 分以下", hi = "1000 अंक तक", es = "Hasta 1000 pts", fr = "Jusqu’à 1000 pts", ar = "حتى 1000 نقطة", de = "Bis 1000 Pkt", pt = "Até 1000 pts", ru = "До 1000 очк.", ja = "1000点以下")
                        Rank.PINK_DIAMOND -> tr("${r.minScore}점 이상", "${r.minScore}+ pts", zh = "${r.minScore} 分以上", hi = "${r.minScore}+ अंक", es = "${r.minScore}+ pts", fr = "${r.minScore}+ pts", ar = "${r.minScore}+ نقطة", de = "${r.minScore}+ Pkt", pt = "${r.minScore}+ pts", ru = "${r.minScore}+ очк.", ja = "${r.minScore}点以上")
                        else -> tr(
"${r.minScore} ~ ${Rank.entries[r.ordinal + 1].minScore - 1}점",
"${r.minScore} – ${Rank.entries[r.ordinal + 1].minScore - 1} pts",
zh = "${r.minScore} – ${Rank.entries[r.ordinal + 1].minScore - 1} 分", hi = "${r.minScore} – ${Rank.entries[r.ordinal + 1].minScore - 1} अंक", es = "${r.minScore} – ${Rank.entries[r.ordinal + 1].minScore - 1} pts", fr = "${r.minScore} – ${Rank.entries[r.ordinal + 1].minScore - 1} pts", ar = "${r.minScore} – ${Rank.entries[r.ordinal + 1].minScore - 1} نقطة", de = "${r.minScore} – ${Rank.entries[r.ordinal + 1].minScore - 1} Pkt", pt = "${r.minScore} – ${Rank.entries[r.ordinal + 1].minScore - 1} pts", ru = "${r.minScore} – ${Rank.entries[r.ordinal + 1].minScore - 1} очк.", ja = "${r.minScore}〜${Rank.entries[r.ordinal + 1].minScore - 1}点",
)
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .background(
                                if (isMine) Ui.primary.copy(alpha = 0.15f) else Color.Transparent,
                                RoundedCornerShape(14.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 배지 폭을 맞춰 표처럼 보이게
                        Box(Modifier.width(140.dp), contentAlignment = Alignment.CenterStart) {
                            RankBadge(if (r == Rank.DIRT) 1000 else r.minScore, large = true)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(range, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ui.text)
                            Text(
                                if (r.bonusPercent > 0) tr(
"🎁 얻는 점수 +${r.bonusPercent}% 추가",
"🎁 +${r.bonusPercent}% bonus on points earned",
zh = "🎁 获得分数额外 +${r.bonusPercent}%", hi = "🎁 कमाए अंकों पर +${r.bonusPercent}% बोनस", es = "🎁 +${r.bonusPercent}% extra en los puntos ganados", fr = "🎁 +${r.bonusPercent}% de bonus sur les points gagnés", ar = "🎁 مكافأة +${r.bonusPercent}% على النقاط المكتسبة", de = "🎁 +${r.bonusPercent}% Bonus auf gewonnene Punkte", pt = "🎁 +${r.bonusPercent}% de bônus nos pontos ganhos", ru = "🎁 +${r.bonusPercent}% к заработанным очкам", ja = "🎁 獲得点数 +${r.bonusPercent}%",
) else tr("보너스 없음", "No bonus", zh = "无奖励", hi = "कोई बोनस नहीं", es = "Sin bonus", fr = "Pas de bonus", ar = "بلا مكافأة", de = "Kein Bonus", pt = "Sem bônus", ru = "Без бонуса", ja = "ボーナスなし"),
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = if (r.bonusPercent > 0) Color(0xFFD08A00) else Ui.text.copy(alpha = 0.5f),
                            )
                            if (isMine) Text(tr("👈 지금 내 계급", "👈 Your rank", zh = "👈 你的等级", hi = "👈 आपकी रैंक", es = "👈 Tu rango", fr = "👈 Ton rang", ar = "👈 رتبتك", de = "👈 Dein Rang", pt = "👈 Seu rank", ru = "👈 Ваш ранг", ja = "👈 今のランク"), fontSize = 12.sp, fontWeight = FontWeight.Black, color = Ui.primaryDark)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (next != null) {
                        tr(
"내 점수 ${myScore}점 · ${next.label}까지 ${next.minScore - myScore}점 남았어요!",
"Your score ${myScore} · ${next.minScore - myScore} pts to ${next.label}!",
zh = "我的分数 ${myScore} · 距离${next.label}还差 ${next.minScore - myScore} 分！", hi = "आपका स्कोर ${myScore} · ${next.label} तक ${next.minScore - myScore} अंक बाकी!", es = "Tu puntuación ${myScore} · ¡faltan ${next.minScore - myScore} pts para ${next.label}!", fr = "Ton score ${myScore} · encore ${next.minScore - myScore} pts avant ${next.label} !", ar = "نقاطك ${myScore} · تبقّى ${next.minScore - myScore} نقطة للوصول إلى ${next.label}!", de = "Dein Stand ${myScore} · noch ${next.minScore - myScore} Pkt bis ${next.label}!", pt = "Sua pontuação ${myScore} · faltam ${next.minScore - myScore} pts para ${next.label}!", ru = "Ваш счёт ${myScore} · до ранга ${next.label} ещё ${next.minScore - myScore} очк.!", ja = "スコア ${myScore} · ${next.label}まであと${next.minScore - myScore}点！",
)
                    } else {
                        tr("내 점수 ${myScore}점 · 최고 계급이에요! 💎", "Your score ${myScore} · Top rank! 💎", zh = "我的分数 ${myScore} · 已是最高等级！💎", hi = "आपका स्कोर ${myScore} · सबसे ऊँची रैंक! 💎", es = "Tu puntuación ${myScore} · ¡rango máximo! 💎", fr = "Ton score ${myScore} · rang maximum ! 💎", ar = "نقاطك ${myScore} · أعلى رتبة! 💎", de = "Dein Stand ${myScore} · höchster Rang! 💎", pt = "Sua pontuação ${myScore} · rank máximo! 💎", ru = "Ваш счёт ${myScore} · высший ранг! 💎", ja = "スコア ${myScore} · 最高ランク！💎")
                    },
                    fontWeight = FontWeight.Black, fontSize = 15.sp, color = Ui.accent,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    tr(
"🎁 계급 보너스: 이겨서 얻는 점수(랜덤 매칭·AI 대전)에 계급만큼 점수를 더 받아요. ",
"🎁 Rank bonus: wins (random match & AI battle) earn extra points based on your rank. ",
zh = "🎁 等级奖励：获胜（随机匹配和 AI 对战）时根据等级额外加分。", hi = "🎁 रैंक बोनस: जीत (रैंडम मैच और AI मुकाबला) पर रैंक के हिसाब से अतिरिक्त अंक मिलते हैं। ", es = "🎁 Bonus de rango: las victorias (partida aleatoria y contra IA) dan puntos extra según tu rango. ", fr = "🎁 Bonus de rang : les victoires (match aléatoire et IA) rapportent des points en plus selon ton rang. ", ar = "🎁 مكافأة الرتبة: الانتصارات (المباراة العشوائية ومعارك الذكاء الاصطناعي) تمنح نقاطًا إضافية حسب رتبتك. ", de = "🎁 Rangbonus: Siege (Zufallsmatch und KI-Duell) bringen je nach Rang Extrapunkte. ", pt = "🎁 Bônus de rank: vitórias (partida aleatória e contra IA) dão pontos extras conforme seu rank. ", ru = "🎁 Бонус ранга: за победы (случайный матч и бой с ИИ) дают дополнительные очки в зависимости от ранга. ", ja = "🎁 ランクボーナス：勝利（ランダムマッチ・AI対戦）でランクに応じて点数が増えます。",
) +
                        tr(
                        "예) 다이아가 랜덤 매칭에서 이기면 30점 + 30%(9점) = 39점",
                        "e.g. a Diamond player winning a random match gets 30 + 30% (9) = 39 pts",
                        zh = "例：钻石玩家赢得随机匹配可得 30 + 30%（9）= 39 分",
                        hi = "उदा. डायमंड खिलाड़ी रैंडम मैच जीतने पर 30 + 30% (9) = 39 अंक पाता है",
                        es = "p. ej. un jugador Diamante que gana una partida aleatoria recibe 30 + 30% (9) = 39 pts",
                        fr = "ex. un joueur Diamant qui gagne un match aléatoire reçoit 30 + 30 % (9) = 39 pts",
                        ar = "مثال: لاعب الماس الفائز في مباراة عشوائية يحصل على 30 + 30% (9) = 39 نقطة",
                        de = "z. B. bekommt ein Diamant-Spieler für einen Zufallsmatch-Sieg 30 + 30 % (9) = 39 Pkt",
                        pt = "ex.: um jogador Diamante que vence uma partida aleatória ganha 30 + 30% (9) = 39 pts",
                        ru = "напр., игрок ранга Алмаз за победу в случайном матче получает 30 + 30% (9) = 39 очк.",
                        ja = "例：ダイヤがランダムマッチで勝つと 30 + 30%（9）= 39点",
                    ),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ui.text,
                    lineHeight = 19.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x22FFC94D), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    tr(
"높은 계급일수록 배지가 더 반짝여요 ✨",
"Higher ranks get sparklier badges ✨",
zh = "等级越高，徽章越闪亮 ✨", hi = "रैंक जितनी ऊँची, बैज उतना चमकीला ✨", es = "Cuanto más alto el rango, más brilla la insignia ✨", fr = "Plus le rang est haut, plus le badge brille ✨", ar = "كلما ارتفعت الرتبة لمعت الشارة أكثر ✨", de = "Je höher der Rang, desto mehr funkelt das Abzeichen ✨", pt = "Quanto maior o rank, mais o emblema brilha ✨", ru = "Чем выше ранг, тем ярче значок ✨", ja = "ランクが高いほどバッジがキラキラ ✨",
),
                    fontSize = 12.sp, color = Ui.text.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

/** 로비 왼쪽 위의 작은 언어 선택 메뉴 (🌐 한국어 / English). */
@Composable
private fun LanguageMenu() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            "🌐 ${Lang.current.label} ▾",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Ui.text,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.7f))
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Lang.Code.entries.forEach { code ->
                DropdownMenuItem(
                    text = {
                        Text(
                            (if (code == Lang.current) "✓ " else "   ") + code.label,
                            fontWeight = if (code == Lang.current) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        Lang.set(context, code)
                        open = false
                    },
                )
            }
        }
    }
}

/** 친구 초대 카드: 내 추천 코드 공유 + 친구 코드 입력. */
/** 로비: 📅 출석 체크(7일 주기) + 🎯 오늘의 미션 3개 */
@Composable
private fun DailyCard(vm: AppViewModel, p: com.bubble.game.data.Profile, busy: Boolean) {
    val today = OnlineRepository.today()
    val yesterday = OnlineRepository.today(System.currentTimeMillis() - 86_400_000L)
    val checkedToday = p.checkinDay == today
    val streak = if (checkedToday || p.checkinDay == yesterday) p.checkinStreak else 0
    // 오늘 받으면 며칠째인지
    val nextDay = if (checkedToday) null else (streak % 7) + 1
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("📅 출석 체크", "📅 Daily check-in", zh = "📅 每日签到", hi = "📅 रोज़ की हाज़िरी", es = "📅 Asistencia diaria", fr = "📅 Présence du jour", ar = "📅 تسجيل الحضور", de = "📅 Täglicher Check-in", pt = "📅 Presença diária", ru = "📅 Ежедневный вход", ja = "📅 出席チェック"),
                fontWeight = FontWeight.Black, color = Ui.text, modifier = Modifier.weight(1f))
            Button(
                onClick = vm::claimDaily,
                enabled = !busy && nextDay != null,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ui.primary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    if (nextDay == null) tr("✅ 내일 또!", "✅ See you tomorrow!", zh = "✅ 明天见！", es = "✅ ¡Hasta mañana!", fr = "✅ À demain !", de = "✅ Bis morgen!", pt = "✅ Até amanhã!", ru = "✅ До завтра!", ja = "✅ また明日！")
                    else tr("받기 💰+${OnlineRepository.CHECKIN_COINS[nextDay - 1]}", "Claim 💰+${OnlineRepository.CHECKIN_COINS[nextDay - 1]}", zh = "领取 💰+${OnlineRepository.CHECKIN_COINS[nextDay - 1]}", es = "Recoger 💰+${OnlineRepository.CHECKIN_COINS[nextDay - 1]}", fr = "Prendre 💰+${OnlineRepository.CHECKIN_COINS[nextDay - 1]}", de = "Holen 💰+${OnlineRepository.CHECKIN_COINS[nextDay - 1]}", pt = "Pegar 💰+${OnlineRepository.CHECKIN_COINS[nextDay - 1]}", ru = "Забрать 💰+${OnlineRepository.CHECKIN_COINS[nextDay - 1]}", ja = "受け取る 💰+${OnlineRepository.CHECKIN_COINS[nextDay - 1]}"),
                    fontWeight = FontWeight.Bold, fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OnlineRepository.CHECKIN_COINS.forEachIndexed { i, coins ->
                val d = i + 1
                val done = d <= streak
                val isNext = d == nextDay
                Column(
                    Modifier
                        .weight(1f)
                        .background(
                            when {
                                done -> Ui.primary.copy(alpha = 0.25f)
                                isNext -> Ui.gold.copy(alpha = 0.35f)
                                else -> Ui.text.copy(alpha = 0.06f)
                            },
                            RoundedCornerShape(10.dp),
                        )
                        .padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(tr("${d}일", "D$d", zh = "第${d}天", ja = "${d}日目"), fontSize = 10.sp, color = Ui.text.copy(alpha = 0.7f), maxLines = 1)
                    Text(if (done) "✅" else if (d == 7) "💖" else "💰", fontSize = 14.sp)
                    Text("$coins", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Ui.text, maxLines = 1)
                }
            }
        }
        Text(
            tr("하루라도 빠지면 1일부터 다시 시작해요 · 7일째는 💖 부활물약도!", "Miss a day and it restarts at day 1 · Day 7 adds a 💖 potion!",
                zh = "中断一天就从第 1 天重新开始 · 第 7 天还送 💖 复活药水！", es = "Si fallas un día, vuelves al día 1 · ¡El día 7 trae una 💖 poción!",
                fr = "Un jour manqué et tout recommence au jour 1 · Le jour 7 donne une 💖 potion !", de = "Ein Tag verpasst – zurück zu Tag 1 · Tag 7 gibt einen 💖 Trank!",
                pt = "Perdeu um dia, volta ao dia 1 · O dia 7 dá uma 💖 poção!", ru = "Пропустите день — начнёте с 1-го · На 7-й день ещё 💖 зелье!",
                ja = "1日でも休むと1日目から · 7日目は 💖 復活薬も！"),
            fontSize = 11.sp, color = Ui.text.copy(alpha = 0.6f), modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(12.dp))
        Text(tr("🎯 오늘의 미션", "🎯 Today's missions", zh = "🎯 今日任务", hi = "🎯 आज के मिशन", es = "🎯 Misiones de hoy", fr = "🎯 Missions du jour", ar = "🎯 مهام اليوم", de = "🎯 Heutige Missionen", pt = "🎯 Missões de hoje", ru = "🎯 Задания дня", ja = "🎯 今日のミッション"),
            fontWeight = FontWeight.Black, color = Ui.text)
        val sameDay = p.missionDay == today
        val missions = listOf(
            Triple("PLAY3", tr("아무 대전 3판 하기", "Play 3 battles", zh = "进行 3 场对战", hi = "3 मुकाबले खेलें", es = "Juega 3 batallas", fr = "Joue 3 combats", ar = "العب 3 معارك", de = "Spiele 3 Duelle", pt = "Jogue 3 batalhas", ru = "Сыграйте 3 боя", ja = "対戦を3回する"), Triple(if (sameDay) p.mPlay else 0, 3, 20)),
            Triple("WIN2", tr("2판 이기기", "Win 2 battles", zh = "赢 2 场", hi = "2 मुकाबले जीतें", es = "Gana 2 batallas", fr = "Gagne 2 combats", ar = "اربح معركتين", de = "Gewinne 2 Duelle", pt = "Vença 2 batalhas", ru = "Выиграйте 2 боя", ja = "2回勝つ"), Triple(if (sameDay) p.mWin else 0, 2, 30)),
            Triple("RANDOM1", tr("랜덤 매칭 1판 하기", "Play 1 random match", zh = "进行 1 场随机匹配", hi = "1 रैंडम मैच खेलें", es = "Juega 1 partida aleatoria", fr = "Joue 1 match aléatoire", ar = "العب مباراة عشوائية واحدة", de = "Spiele 1 Zufallsmatch", pt = "Jogue 1 partida aleatória", ru = "Сыграйте 1 случайный матч", ja = "ランダムマッチを1回する"), Triple(if (sameDay) p.mRandom else 0, 1, 20)),
        )
        missions.forEach { (id, label, prog) ->
            val (now, goal, coins) = prog
            val claimed = sameDay && id in p.mClaimed
            val ready = now >= goal && !claimed
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ui.text)
                    Text("${now.coerceAtMost(goal)}/$goal · 💰 +$coins", fontSize = 12.sp, color = Ui.text.copy(alpha = 0.65f))
                }
                if (claimed) {
                    Text("✅", fontSize = 18.sp)
                } else {
                    Button(
                        onClick = { vm.claimMission(id, label) },
                        enabled = !busy && ready,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Ui.accent),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) { Text(tr("받기", "Claim", zh = "领取", hi = "लें", es = "Recoger", fr = "Prendre", ar = "استلام", de = "Holen", pt = "Pegar", ru = "Забрать", ja = "受け取る"), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        Text(
            tr("AI 대전·친구 대전(점수 걸린 판)도 들어가요 · 매일 0시에 새로 시작", "AI and scored friend battles count too · resets daily at 00:00 (KST)",
                zh = "AI 对战和计分好友对战也算 · 每天 0 点（韩国时间）重置", es = "Cuentan la IA y los duelos con puntos · se reinicia a las 00:00 (KST)",
                fr = "L’IA et les duels notés comptent aussi · remise à zéro à 00h (KST)", de = "KI- und gewertete Freundesduelle zählen · Neustart um 0 Uhr (KST)",
                pt = "IA e duelos valendo pontos contam · reinicia às 00h (KST)", ru = "Бои с ИИ и с друзьями на очки тоже считаются · сброс в 00:00 (KST)",
                ja = "AI対戦・点数付きの友だち対戦も対象 · 毎日0時（韓国時間）にリセット"),
            fontSize = 11.sp, color = Ui.text.copy(alpha = 0.6f), modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun InviteCard(vm: AppViewModel, p: com.bubble.game.data.Profile, busy: Boolean) {
    val context = LocalContext.current
    LaunchedEffect(p.uid) { vm.loadReferralCode() }
    var input by remember { mutableStateOf("") }
    SoftCard {
        Text(
            tr("🎁 친구 초대", "🎁 Invite friends", zh = "🎁 邀请好友", hi = "🎁 दोस्तों को बुलाएँ", es = "🎁 Invita a tus amigos",
                fr = "🎁 Invite tes amis", ar = "🎁 ادعُ أصدقاءك", de = "🎁 Freunde einladen", pt = "🎁 Convide amigos",
                ru = "🎁 Пригласить друзей", ja = "🎁 友だち招待"),
            fontWeight = FontWeight.Bold, color = Ui.text,
        )
        Text(
            tr(
                "친구가 내 코드를 입력하면 나도 친구도 +30점!", "When a friend enters your code, you both get +30 pts!",
                zh = "好友输入你的邀请码，你们俩都得 +30 分！", hi = "दोस्त आपका कोड डाले तो आप दोनों को +30 अंक!", es = "¡Cuando un amigo use tu código, ambos ganan +30 pts!", fr = "Quand un ami entre ton code, vous gagnez tous les deux +30 pts !", ar = "عندما يُدخل صديقك رمزك يحصل كلاكما على +30 نقطة!", de = "Gibt ein Freund deinen Code ein, bekommt ihr beide +30 Pkt!", pt = "Quando um amigo usar seu código, vocês dois ganham +30 pts!", ru = "Когда друг введёт ваш код, вы оба получите +30 очк.!", ja = "友だちが自分のコードを入力すると、2人とも +30点！",
            ),
            fontSize = 13.sp, color = Ui.text.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                p.refCode ?: "······",
                fontSize = 24.sp, fontWeight = FontWeight.Black, color = Ui.primaryDark, letterSpacing = 3.sp,
                modifier = Modifier
                    .background(Ui.bgTop, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    val code = p.refCode ?: return@Button
                    val link = Invite.link(code)
                    val msg = tr(
                        "Bubble 냥햄대전에서 같이 놀자! 🐱🐹\n추천 코드: $code\n$link",
                        "Play Bubble: Cats vs Hamsters with me! 🐱🐹\nMy referral code: $code\n$link",
                        zh = "来和我一起玩 Bubble 猫咪大战仓鼠吧！🐱🐹\n邀请码：$code\n$link",
                        hi = "मेरे साथ Bubble खेलो! 🐱🐹\nमेरा रेफ़रल कोड: $code\n$link",
                        es = "¡Juega conmigo a Bubble: Gatos vs Hámsteres! 🐱🐹\nMi código: $code\n$link",
                        fr = "Viens jouer à Bubble : Chats contre Hamsters avec moi ! 🐱🐹\nMon code : $code\n$link",
                        ar = "العب معي Bubble: قطط ضد هامستر! 🐱🐹\nرمز الدعوة: $code\n$link",
                        de = "Spiel mit mir Bubble: Katzen gegen Hamster! 🐱🐹\nMein Code: $code\n$link",
                        pt = "Jogue Bubble: Gatos vs Hamsters comigo! 🐱🐹\nMeu código: $code\n$link",
                        ru = "Сыграй со мной в Bubble: котики против хомяков! 🐱🐹\nМой код: $code\n$link",
                        ja = "一緒に Bubble ネコ vs ハムスター で遊ぼう！🐱🐹\n招待コード：$code\n$link",
                    )
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, msg)
                    }
                    context.startActivity(android.content.Intent.createChooser(send, null))
                },
                enabled = p.refCode != null,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ui.accent),
            ) {
                Text(tr("공유하기", "Share", zh = "分享", hi = "शेयर करें", es = "Compartir", fr = "Partager", ar = "مشاركة",
                    de = "Teilen", pt = "Compartilhar", ru = "Поделиться", ja = "共有"), fontWeight = FontWeight.Bold)
            }
        }
        // 초대 링크 복사
        p.refCode?.let { code ->
            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            var copied by remember { mutableStateOf(false) }
            TextButton(onClick = {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(Invite.link(code)))
                copied = true
            }) {
                Text(
                    if (copied) {
                        tr("✅ 링크를 복사했어요", "✅ Link copied", zh = "✅ 链接已复制", hi = "✅ लिंक कॉपी हो गया", es = "✅ Enlace copiado",
                            fr = "✅ Lien copié", ar = "✅ تم نسخ الرابط", de = "✅ Link kopiert", pt = "✅ Link copiado",
                            ru = "✅ Ссылка скопирована", ja = "✅ リンクをコピーしました")
                    } else {
                        tr("🔗 초대 링크 복사", "🔗 Copy invite link", zh = "🔗 复制邀请链接", hi = "🔗 इनवाइट लिंक कॉपी करें",
                            es = "🔗 Copiar enlace de invitación", fr = "🔗 Copier le lien d’invitation", ar = "🔗 نسخ رابط الدعوة",
                            de = "🔗 Einladungslink kopieren", pt = "🔗 Copiar link de convite", ru = "🔗 Скопировать ссылку",
                            ja = "🔗 招待リンクをコピー")
                    },
                    color = Ui.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                )
            }
        }
        if (p.referrals > 0) {
            Text(
                tr("초대한 친구 ${p.referrals}명", "Friends invited: ${p.referrals}", zh = "已邀请好友：${p.referrals}",
                    hi = "बुलाए गए दोस्त: ${p.referrals}", es = "Amigos invitados: ${p.referrals}", fr = "Amis invités : ${p.referrals}",
                    ar = "الأصدقاء المدعوون: ${p.referrals}", de = "Eingeladene Freunde: ${p.referrals}",
                    pt = "Amigos convidados: ${p.referrals}", ru = "Приглашено друзей: ${p.referrals}", ja = "招待した友だち：${p.referrals}人"),
                fontSize = 12.sp, color = Ui.accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (!p.referred) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) },
                    singleLine = true,
                    label = {
                        Text(tr("친구 코드 입력", "Friend's code", zh = "好友的邀请码", hi = "दोस्त का कोड", es = "Código de un amigo",
                            fr = "Code d’un ami", ar = "رمز صديق", de = "Code eines Freundes", pt = "Código de um amigo",
                            ru = "Код друга", ja = "友だちのコード"), fontSize = 12.sp)
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { vm.redeemReferral(input); input = "" },
                    enabled = input.length == 6 && !busy,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(tr("확인", "Apply", zh = "确认", hi = "लागू करें", es = "Aplicar", fr = "Valider", ar = "تطبيق",
                        de = "Einlösen", pt = "Aplicar", ru = "Ввести", ja = "決定"), color = Ui.accent)
                }
            }
        }
    }
}


/** 스킨 상점 설명 (효과가 있는 캐릭터는 조건을 숫자 그대로) */
private fun skinDesc(a: Animal, p: com.bubble.game.data.Profile): String = when (a) {
    Animal.LUCKY_CAT -> luckyDesc(p)
    Animal.OCTOPUS -> tr("🐙 분할하면 먹물이 퍽! · 이기면 화면에 먹물이 퍽퍽 · 승패 영향 없음", "🐙 Ink splash when you split · ink splats when you win · no effect on winning", zh = "🐙 分裂时喷墨！· 仅外观",
        es = "🐙 ¡Tinta al dividir! · solo aspecto", fr = "🐙 Jet d’encre en divisant · look seulement", de = "🐙 Tinte beim Teilen · nur Look",
        pt = "🐙 Tinta ao dividir · só visual", ru = "🐙 Чернила при делении · только вид", ja = "🐙 分割でスミがぷしゅっ · 見た目だけ")
    Animal.PENGUIN -> tr("🐧 공격하면 빨간 썰매를 타고 쌩! 눈가루·얼음 조각이 튀고 부딪히면 얼음이 와장창\n이기면 썰매 퍼레이드와 눈보라 · 승패 영향 없음",
        "🐧 Attacks ride a red sled — snow spray, ice shards and an icy crash\nWin: sled parade in a snowstorm · no effect on winning",
        zh = "🐧 攻击时坐红色雪橇冲过去！雪粉冰片飞溅，撞上时冰块碎裂\n获胜：雪橇游行与暴风雪 · 不影响胜负",
        es = "🐧 Ataca en trineo rojo: nieve, esquirlas de hielo y choque helado\nVictoria: desfile en trineo con nevada · sin efecto",
        fr = "🐧 Attaque en luge rouge : neige, éclats de glace et choc glacé\nVictoire : parade en luge sous la neige · aucun effet",
        de = "🐧 Angriff auf rotem Schlitten: Schnee, Eissplitter, eisiger Aufprall\nSieg: Schlittenparade im Schneesturm · kein Effekt",
        pt = "🐧 Ataca de trenó vermelho: neve, lascas de gelo e choque gelado\nVitória: desfile de trenó na nevasca · sem efeito",
        ru = "🐧 Атака на красных санках: снег, осколки льда, ледяной удар\nПобеда: парад на санках в метель · без эффекта",
        ja = "🐧 攻撃は赤いソリでシュー！雪と氷のかけら、当たると氷がパリーン\n勝利：ソリのパレードと吹雪 · 勝敗に影響なし")
    Animal.PANDA -> tr("🐼 장착하고 출석하면 💰 +${Shop.PANDA_CHECKIN_BONUS} 더 (하루 1번) · 이기면 대나무잎 회오리 · 영구", "🐼 Check in while equipped for 💰 +${Shop.PANDA_CHECKIN_BONUS} more (once a day) · permanent",
        zh = "🐼 装备后签到多得 💰 +${Shop.PANDA_CHECKIN_BONUS}（每天 1 次）· 永久", es = "🐼 Equipado: 💰 +${Shop.PANDA_CHECKIN_BONUS} extra al registrarte (1 al día) · permanente",
        fr = "🐼 Équipé : 💰 +${Shop.PANDA_CHECKIN_BONUS} en plus à la présence (1/jour) · permanent", de = "🐼 Ausgerüstet: 💰 +${Shop.PANDA_CHECKIN_BONUS} beim Check-in (1×/Tag) · dauerhaft",
        pt = "🐼 Equipado: 💰 +${Shop.PANDA_CHECKIN_BONUS} na presença (1 por dia) · permanente", ru = "🐼 Надета: +${Shop.PANDA_CHECKIN_BONUS} 💰 за вход (1 раз в день) · навсегда",
        ja = "🐼 装備して出席すると 💰 +${Shop.PANDA_CHECKIN_BONUS}（1日1回）· 永久")
    Animal.DRAGON -> {
        val today = if (p.dragonDay == OnlineRepository.today()) p.dragonCoins else 0
        val mx = Shop.DRAGON_DAILY_MAX
        tr("🐉 상대 방울을 터뜨릴 때마다 💰 +${Shop.DRAGON_POP_COINS}\n(하루 최대 $mx · 오늘 $today/$mx) · 이기면 불꽃이 활활 · 영구",
            "🐉 💰 +${Shop.DRAGON_POP_COINS} every time you pop a rival bubble\n(max $mx a day · today $today/$mx) · permanent",
            zh = "🐉 每次打爆对手泡泡 💰 +${Shop.DRAGON_POP_COINS}\n（每天最多 $mx · 今天 $today/$mx）· 永久",
            es = "🐉 💰 +${Shop.DRAGON_POP_COINS} cada vez que revientas una burbuja rival\n(máx. $mx al día · hoy $today/$mx) · permanente",
            fr = "🐉 💰 +${Shop.DRAGON_POP_COINS} à chaque bulle adverse éclatée\n(max $mx par jour · aujourd’hui $today/$mx) · permanent",
            de = "🐉 💰 +${Shop.DRAGON_POP_COINS} für jede geplatzte Gegnerblase\n(max. $mx pro Tag · heute $today/$mx) · dauerhaft",
            pt = "🐉 💰 +${Shop.DRAGON_POP_COINS} a cada bolha rival estourada\n(máx. $mx por dia · hoje $today/$mx) · permanente",
            ru = "🐉 💰 +${Shop.DRAGON_POP_COINS} за каждый лопнутый пузырь соперника\n(макс. $mx в день · сегодня $today/$mx) · навсегда",
            ja = "🐉 相手の泡を割るたびに 💰 +${Shop.DRAGON_POP_COINS}\n（1日最大$mx · 今日 $today/$mx）· 永久")
    }
    Animal.PINK_CAT -> tr("✨ 영구 스킨 · 💗 하트 타이머, 이기면 하트 비 (승패 영향 없음)", "✨ Permanent skin · 💗 Heart timer, heart rain when you win (no effect on winning)",
        zh = "✨ 永久皮肤 · 💗 获胜时下起爱心雨（不影响胜负）", es = "✨ Aspecto permanente · 💗 Lluvia de corazones al ganar", fr = "✨ Skin permanent · 💗 Pluie de cœurs à la victoire",
        de = "✨ Dauerhafter Skin · 💗 Herzregen beim Sieg", pt = "✨ Skin permanente · 💗 Chuva de corações ao vencer", ru = "✨ Навсегда · 💗 Дождь из сердечек при победе", ja = "✨ 永久スキン · 💗 勝つとハートの雨")
    Animal.BLACK_CAT -> tr("✨ 영구 스킨 · 🌙 밤하늘 타이머, 이기면 달과 별이 쏟아져요 (승패 영향 없음)", "✨ Permanent skin · 🌙 Night-sky timer, moons and stars rain when you win (no effect on winning)",
        zh = "✨ 永久皮肤 · 🌙 获胜时月亮星星落下（不影响胜负）", es = "✨ Aspecto permanente · 🌙 Lluvia de lunas y estrellas al ganar", fr = "✨ Skin permanent · 🌙 Pluie de lunes et d’étoiles à la victoire",
        de = "✨ Dauerhafter Skin · 🌙 Monde und Sterne beim Sieg", pt = "✨ Skin permanente · 🌙 Chuva de luas e estrelas ao vencer", ru = "✨ Навсегда · 🌙 Луны и звёзды при победе", ja = "✨ 永久スキン · 🌙 勝つと月と星が降る")
    Animal.SNOW_HAMSTER -> tr("✨ 영구 스킨 · ❄️ 내 타이머가 얼음 게이지로, 이기면 눈송이 비 (승패 영향 없음)", "✨ Permanent skin · ❄️ Icy timer, snow rain when you win (no effect on winning)",
        zh = "✨ 永久皮肤 · ❄️ 冰雪计时条，获胜时下雪（不影响胜负）", es = "✨ Aspecto permanente · ❄️ Temporizador de hielo y nieve al ganar", fr = "✨ Skin permanent · ❄️ Minuteur de glace, neige à la victoire",
        de = "✨ Dauerhafter Skin · ❄️ Eis-Timer, Schnee beim Sieg", pt = "✨ Skin permanente · ❄️ Timer de gelo e neve ao vencer", ru = "✨ Навсегда · ❄️ Ледяной таймер, снег при победе", ja = "✨ 永久スキン · ❄️ 氷のタイマー、勝つと雪")
    Animal.GOLD_HAMSTER -> tr("✨ 영구 스킨 · 🏆 황금 타이머, 이기면 금가루 폭죽이 팡팡 (승패 영향 없음)", "✨ Permanent skin · 🏆 Golden timer, gold fireworks when you win (no effect on winning)",
        zh = "✨ 永久皮肤 · 🏆 黄金计时条，获胜时金粉烟花（不影响胜负）", es = "✨ Aspecto permanente · 🏆 Temporizador dorado y fuegos de oro al ganar", fr = "✨ Skin permanent · 🏆 Minuteur doré, feux d’or à la victoire",
        de = "✨ Dauerhafter Skin · 🏆 Gold-Timer, Goldfeuerwerk beim Sieg", pt = "✨ Skin permanente · 🏆 Timer dourado e fogos de ouro ao vencer", ru = "✨ Навсегда · 🏆 Золотой таймер, золотой салют при победе", ja = "✨ 永久スキン · 🏆 黄金タイマー、勝つと金粉の花火")
    Animal.RABBIT -> rabbitDesc(p)
    else -> tr("✨ 영구 스킨 · 효과 없음", "✨ Permanent skin · no effect", zh = "✨ 永久皮肤 · 无效果", es = "✨ Aspecto permanente · sin efecto",
        fr = "✨ Skin permanent · aucun effet", de = "✨ Dauerhafter Skin · kein Effekt", pt = "✨ Skin permanente · sem efeito",
        ru = "✨ Навсегда · без эффекта", ja = "✨ 永久スキン · 効果なし")
}

/** 🦄 유니콘 상점 설명: 무지개 축복 */
private fun unicornDesc(p: com.bubble.game.data.Profile, min: Int): String {
    val today = if (p.unicornDay == OnlineRepository.today()) p.unicornCoins else 0
    val c = Shop.UNICORN_WIN_COINS
    val mx = Shop.UNICORN_DAILY_MAX
    return tr("🌈 무지개 축복!\n· 이길 때마다 💰 +$c (하루 최대 $mx · 오늘 $today/$mx)\n· 매칭되면 상대에게 \"다이아 전용 유니콘 등장!\"\n· 랭킹에서 닉네임이 무지개색 · 이기면 무지개와 별 폭죽\n· 판매하지 않아요: 💎 다이아 계급(${min}점)부터 무료로 장착\n· 랜덤 매칭·점수 걸린 친구 대전만",
        "🌈 Rainbow blessing!\n· 💰 +$c per win (max $mx a day · today $today/$mx)\n· Rivals see \"A Diamond-only Unicorn appears!\"\n· Rainbow name on the leaderboard · rainbow & star fireworks when you win\n· Not for sale: free from 💎 Diamond (${min} pts)\n· Random & scored friend matches",
        zh = "🌈 彩虹祝福！\n· 每胜 💰 +$c（每天最多 $mx · 今天 $today/$mx）\n· 匹配时对手会看到“钻石专属独角兽登场！”\n· 排行榜上名字变彩虹色\n· 非卖品：💎 钻石（${min} 分）起免费装备",
        es = "🌈 ¡Bendición arcoíris!\n· 💰 +$c por victoria (máx. $mx al día · hoy $today/$mx)\n· Tu rival verá el aviso del unicornio\n· Nombre arcoíris en la clasificación\n· No se vende: gratis desde 💎 Diamante (${min} pts)",
        fr = "🌈 Bénédiction arc-en-ciel !\n· 💰 +$c par victoire (max $mx par jour · aujourd’hui $today/$mx)\n· L’adversaire voit l’arrivée de la licorne\n· Pseudo arc-en-ciel au classement\n· Pas à vendre : gratuit dès 💎 Diamant (${min} pts)",
        de = "🌈 Regenbogensegen!\n· 💰 +$c pro Sieg (max. $mx pro Tag · heute $today/$mx)\n· Gegner sehen: Diamant-Einhorn erscheint\n· Regenbogenname in der Bestenliste\n· Nicht käuflich: gratis ab 💎 Diamant (${min} Pkt)",
        pt = "🌈 Bênção arco-íris!\n· 💰 +$c por vitória (máx. $mx por dia · hoje $today/$mx)\n· O rival vê o aviso do unicórnio\n· Nome arco-íris no ranking\n· Não vende: grátis a partir de 💎 Diamante (${min} pts)",
        ru = "🌈 Радужное благословение!\n· 💰 +$c за победу (макс. $mx в день · сегодня $today/$mx)\n· Соперник увидит появление единорога\n· Радужное имя в рейтинге\n· Не продаётся: бесплатно с 💎 Алмаза (${min} очк.)",
        ja = "🌈 にじの祝福！\n· 勝つたびに 💰 +$c（1日最大$mx · 今日 $today/$mx）\n· マッチすると相手に「ダイヤ専用ユニコーン登場！」\n· ランキングで名前が虹色\n· 非売品：💎 ダイヤ（${min}点）から無料で装備")
}

/** 🐰 연승 토끼 상점 설명: 연승 보상 숫자 + 지금 연승 + 오늘 받은 양 */
private fun rabbitDesc(p: com.bubble.game.data.Profile): String {
    val today = if (p.rabbitDay == OnlineRepository.today()) p.rabbitCoins else 0
    val mx = Shop.RABBIT_DAILY_MAX
    val a = Shop.RABBIT_3_COINS
    val b = Shop.RABBIT_5_COINS
    val c = Shop.RABBIT_MORE_COINS
    val n = p.winStreak
    return tr("🐰 연승할수록 💰 보상!\n· 3연승 +$a · 5연승 +$b · 6연승부터 이길 때마다 +$c\n· 지면 연승이 처음부터 (다시 3연승하면 또 +$a)\n· 하루 최대 $mx (오늘 $today/$mx)\n· 연승마다 리본이 하나씩, 5연승이면 금빛 리본\n· 랜덤 매칭·점수 걸린 친구 대전만 · 지금 ${n}연승",
        "🐰 Win streaks pay 💰!\n· 3 wins +$a · 5 wins +$b · from 6, +$c per win\n· A loss resets the streak (3 again = +$a again)\n· Max $mx a day (today $today/$mx)\n· A ribbon per win, golden ribbons at 5\n· Random & scored friend matches · streak $n",
        zh = "🐰 连胜越多 💰 越多！\n· 3连胜 +$a · 5连胜 +$b · 6连胜起每胜 +$c\n· 输了连胜重来（再3连胜又 +$a）\n· 每天最多 $mx（今天 $today/$mx）\n· 每胜一个蝴蝶结，5连胜变金色\n· 随机匹配和计分好友对战 · 当前 ${n} 连胜",
        es = "🐰 ¡Las rachas dan 💰!\n· 3 seguidas +$a · 5 seguidas +$b · desde 6, +$c por victoria\n· Perder reinicia la racha\n· Máx. $mx al día (hoy $today/$mx)\n· Un lazo por victoria, dorados a las 5 · racha $n",
        fr = "🐰 Les séries rapportent 💰 !\n· 3 victoires +$a · 5 victoires +$b · dès 6, +$c par victoire\n· Une défaite remet la série à zéro\n· Max $mx par jour (aujourd’hui $today/$mx)\n· Un ruban par victoire, dorés à 5 · série $n",
        de = "🐰 Siegesserien bringen 💰!\n· 3 Siege +$a · 5 Siege +$b · ab 6 +$c pro Sieg\n· Eine Niederlage setzt die Serie zurück\n· Max. $mx pro Tag (heute $today/$mx)\n· Eine Schleife pro Sieg, golden ab 5 · Serie $n",
        pt = "🐰 Sequências dão 💰!\n· 3 vitórias +$a · 5 vitórias +$b · a partir de 6, +$c por vitória\n· Perder zera a sequência\n· Máx. $mx por dia (hoje $today/$mx)\n· Um laço por vitória, dourados aos 5 · sequência $n",
        ru = "🐰 Серии побед дают 💰!\n· 3 победы +$a · 5 побед +$b · с 6-й +$c за победу\n· Поражение обнуляет серию\n· Макс. $mx в день (сегодня $today/$mx)\n· Бантик за победу, золотые на 5 · серия $n",
        ja = "🐰 連勝するほど 💰！\n· 3連勝 +$a · 5連勝 +$b · 6連勝からは勝つたびに +$c\n· 負けると連勝はリセット（また3連勝で +$a）\n· 1日最大$mx（今日 $today/$mx）\n· 連勝ごとにリボン、5連勝で金色 · 今${n}連勝")
}

/**
 * 결과 화면 💰 정리 (이겨도 져도): 이번 판에 캐릭터로 받은 💰 [inGame]과,
 * 승리 보상(토끼·유니콘) [winBonus]까지 합친 총액.
 */
@Composable
private fun CoinSummary(skin: Animal?, inGame: Int, winBonus: Int) {
    if (inGame <= 0 && winBonus <= 0) return
    if (inGame > 0) {
        val who = skin?.label ?: tr("내 캐릭터", "your character", zh = "我的角色", es = "tu personaje", fr = "ton perso", de = "deinem Charakter", pt = "seu personagem", ru = "персонаж", ja = "キャラ")
        Text(
            tr("💰 이번 판 $who 효과로 +$inGame", "💰 +$inGame this game from $who", zh = "💰 本局 $who 效果 +$inGame", es = "💰 +$inGame en esta partida con $who",
                fr = "💰 +$inGame cette partie grâce à $who", de = "💰 +$inGame in dieser Partie durch $who", pt = "💰 +$inGame nesta partida com $who",
                ru = "💰 +$inGame за партию: $who", ja = "💰 この対戦で${who}の効果 +$inGame"),
            fontSize = 16.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, color = Color(0xFFC77700),
            modifier = Modifier
                .padding(top = 6.dp)
                .background(Color(0xFFFFF3C4), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
    // 여러 곳에서 받았으면 총액도
    if (inGame > 0 && winBonus > 0) {
        Text(
            tr("💰 이번 판 총 +${inGame + winBonus}", "💰 Total this game +${inGame + winBonus}", zh = "💰 本局合计 +${inGame + winBonus}", es = "💰 Total de la partida +${inGame + winBonus}",
                fr = "💰 Total de la partie +${inGame + winBonus}", de = "💰 Gesamt in dieser Partie +${inGame + winBonus}", pt = "💰 Total da partida +${inGame + winBonus}",
                ru = "💰 Всего за партию +${inGame + winBonus}", ja = "💰 この対戦の合計 +${inGame + winBonus}"),
            fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFFC77700), modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** 🦄 결과 화면: 무지개 축복 💰 (무지개색으로 흐르는 글자) */
@Composable
private fun UnicornResult(coins: Int) {
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(700); com.bubble.game.audio.Music.sfx(com.bubble.game.audio.Music.Sfx.COIN_UNICORN) }
    val t = rememberInfiniteTransition(label = "uni")
    val x by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2_400, easing = androidx.compose.animation.core.LinearEasing)), label = "x")
    Text(
        tr("🦄 무지개 축복 💰+$coins!", "🦄 Rainbow blessing 💰+$coins!", zh = "🦄 彩虹祝福 💰+$coins！", es = "🦄 ¡Bendición arcoíris 💰+$coins!",
            fr = "🦄 Bénédiction arc-en-ciel 💰+$coins !", de = "🦄 Regenbogensegen 💰+$coins!", pt = "🦄 Bênção arco-íris 💰+$coins!",
            ru = "🦄 Радужное благословение 💰+$coins!", ja = "🦄 にじの祝福 💰+$coins！"),
        style = androidx.compose.ui.text.TextStyle(
            brush = Brush.linearGradient(rainbowText, start = androidx.compose.ui.geometry.Offset(x * 600f - 300f, 0f), end = androidx.compose.ui.geometry.Offset(x * 600f, 0f), tileMode = androidx.compose.ui.graphics.TileMode.Mirror),
            fontSize = 17.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
        ),
        modifier = Modifier
            .padding(top = 6.dp)
            .background(Color(0xFFF6EEFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** 무지개 글자색 (진하게: 흰 바탕에서 읽히게) */
internal val rainbowText = listOf(Color(0xFFE5397A), Color(0xFFE07A00), Color(0xFF2E9E4F), Color(0xFF2B7FD6), Color(0xFF7B4BD8))

/** 🐰 결과 화면: 연승 보상 또는 다음 보상 안내 */
@Composable
private fun RabbitResult(streak: Int, coins: Int) {
    LaunchedEffect(coins) { if (coins > 0) { kotlinx.coroutines.delay(700); com.bubble.game.audio.Music.sfx(com.bubble.game.audio.Music.Sfx.COIN_RABBIT) } }
    val text = when {
        coins > 0 -> tr("🐰 ${streak}연승 보상 💰+$coins!", "🐰 $streak-win streak reward 💰+$coins!", zh = "🐰 ${streak}连胜奖励 💰+$coins！",
            es = "🐰 ¡Premio por $streak seguidas 💰+$coins!", fr = "🐰 Récompense $streak victoires 💰+$coins !", de = "🐰 $streak Siege in Folge 💰+$coins!",
            pt = "🐰 Prêmio por $streak seguidas 💰+$coins!", ru = "🐰 Награда за $streak побед подряд 💰+$coins!", ja = "🐰 ${streak}連勝ボーナス 💰+$coins！")
        streak < 3 -> tr("🐰 ${streak}연승! 3연승이면 💰+${Shop.RABBIT_3_COINS}", "🐰 $streak in a row! 3 wins = 💰+${Shop.RABBIT_3_COINS}",
            zh = "🐰 ${streak}连胜！3连胜 💰+${Shop.RABBIT_3_COINS}", es = "🐰 ¡$streak seguidas! 3 = 💰+${Shop.RABBIT_3_COINS}", fr = "🐰 $streak d’affilée ! 3 = 💰+${Shop.RABBIT_3_COINS}",
            de = "🐰 $streak in Folge! 3 = 💰+${Shop.RABBIT_3_COINS}", pt = "🐰 $streak seguidas! 3 = 💰+${Shop.RABBIT_3_COINS}", ru = "🐰 $streak подряд! 3 = 💰+${Shop.RABBIT_3_COINS}", ja = "🐰 ${streak}連勝！3連勝で 💰+${Shop.RABBIT_3_COINS}")
        streak == 4 -> tr("🐰 4연승! 한 번 더 이기면 💰+${Shop.RABBIT_5_COINS}", "🐰 4 in a row! One more win = 💰+${Shop.RABBIT_5_COINS}",
            zh = "🐰 4连胜！再赢一次 💰+${Shop.RABBIT_5_COINS}", es = "🐰 ¡4 seguidas! Una más = 💰+${Shop.RABBIT_5_COINS}", fr = "🐰 4 d’affilée ! Encore une = 💰+${Shop.RABBIT_5_COINS}",
            de = "🐰 4 in Folge! Noch einer = 💰+${Shop.RABBIT_5_COINS}", pt = "🐰 4 seguidas! Mais uma = 💰+${Shop.RABBIT_5_COINS}", ru = "🐰 4 подряд! Ещё одна = 💰+${Shop.RABBIT_5_COINS}", ja = "🐰 4連勝！あと1勝で 💰+${Shop.RABBIT_5_COINS}")
        else -> tr("🐰 ${streak}연승! 오늘 연승 보상은 다 받았어요 (최대 💰${Shop.RABBIT_DAILY_MAX})", "🐰 $streak in a row! Today's streak rewards are maxed (💰${Shop.RABBIT_DAILY_MAX})",
            zh = "🐰 ${streak}连胜！今天的连胜奖励已领满（💰${Shop.RABBIT_DAILY_MAX}）", es = "🐰 ¡$streak seguidas! Premios de hoy al máximo", fr = "🐰 $streak d’affilée ! Récompenses du jour au max",
            de = "🐰 $streak in Folge! Tagesmaximum erreicht", pt = "🐰 $streak seguidas! Prêmios de hoje no máximo", ru = "🐰 $streak подряд! Лимит наград на сегодня", ja = "🐰 ${streak}連勝！今日の連勝ボーナスは上限です")
    }
    Text(
        text, fontSize = if (coins > 0) 17.sp else 14.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
        color = if (coins > 0) Color(0xFFC77700) else Ui.primaryDark,
        modifier = Modifier
            .padding(top = 6.dp)
            .background(if (coins > 0) Color(0xFFFFF3C4) else Color(0xFFFFE6F2), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** 💰 복냥이 상점 설명: 확률·하루 최대를 숫자 그대로 표시 (확률 정보 공개) + 오늘 받은 양 */
private fun luckyDesc(p: com.bubble.game.data.Profile): String {
    val today = if (p.luckyDay == OnlineRepository.today()) p.luckyCoins else 0
    val pct = Shop.LUCKY_CHANCE_PCT
    val max = Shop.LUCKY_DAILY_MAX
    return tr(
        "💰 상대를 공격할 때마다 ${pct}% 확률로 상점 포인트 +1\n(하루 최대 $max · 오늘 $today/$max) · 이기면 금화 폭죽 · 영구 캐릭터",
        "💰 Each attack has a ${pct}% chance to give +1 shop point\n(max $max a day · today $today/$max) · permanent",
        zh = "💰 每次攻击有 ${pct}% 概率获得 1 商店积分\n（每天最多 $max · 今天 $today/$max）· 永久",
        es = "💰 Cada ataque tiene un ${pct}% de dar +1 punto de tienda\n(máx. $max al día · hoy $today/$max) · permanente",
        fr = "💰 Chaque attaque a ${pct}% de chances de donner +1 point boutique\n(max $max par jour · aujourd’hui $today/$max) · permanent",
        de = "💰 Jeder Angriff: ${pct}% Chance auf +1 Shop-Punkt\n(max. $max pro Tag · heute $today/$max) · dauerhaft",
        pt = "💰 Cada ataque tem ${pct}% de chance de dar +1 ponto da loja\n(máx. $max por dia · hoje $today/$max) · permanente",
        ru = "💰 Каждая атака: ${pct}% шанс +1 очко магазина\n(макс. $max в день · сегодня $today/$max) · навсегда",
        ja = "💰 攻撃するたびに${pct}%の確率でショップポイント+1\n（1日最大$max · 今日 $today/$max）· 永久",
    )
}

/** "⏰ 가입 후 7일 한정 · N일 M시간 남음" */
private fun firstBuyLeftText(ms: Long): String {
    val h = (ms / 3_600_000L).coerceAtLeast(0)
    val d = h / 24
    val hh = h % 24
    val m = (ms / 60_000L) % 60
    return when {
        d > 0 -> tr("⏰ 가입 후 7일 한정 · ${d}일 ${hh}시간 남음", "⏰ First 7 days only · ${d}d ${hh}h left", zh = "⏰ 注册后 7 天内有效 · 剩 ${d} 天 ${hh} 小时",
            es = "⏰ Solo los 7 primeros días · quedan ${d} d ${hh} h", fr = "⏰ 7 premiers jours seulement · encore ${d} j ${hh} h",
            de = "⏰ Nur in den ersten 7 Tagen · noch ${d} T ${hh} Std", pt = "⏰ Só nos 7 primeiros dias · faltam ${d} d ${hh} h",
            ru = "⏰ Только первые 7 дней · осталось ${d} д ${hh} ч", ja = "⏰ 登録から7日間限定 · 残り${d}日${hh}時間")
        else -> tr("⏰ 가입 후 7일 한정 · ${hh}시간 ${m}분 남음!", "⏰ First 7 days only · ${hh}h ${m}m left!", zh = "⏰ 注册后 7 天内有效 · 剩 ${hh} 小时 ${m} 分！",
            es = "⏰ Solo los 7 primeros días · ¡quedan ${hh} h ${m} min!", fr = "⏰ 7 premiers jours seulement · plus que ${hh} h ${m} min !",
            de = "⏰ Nur in den ersten 7 Tagen · noch ${hh} Std ${m} Min!", pt = "⏰ Só nos 7 primeiros dias · faltam ${hh} h ${m} min!",
            ru = "⏰ Только первые 7 дней · осталось ${hh} ч ${m} мин!", ja = "⏰ 登録から7日間限定 · 残り${hh}時間${m}分！")
    }
}

/** 상점 맨 위: 포인트 충전 (Google Play 결제). 100 · 500 · 1000 · 2000점 */
@Composable
private fun PointsCard(vm: AppViewModel) {
    val prices by vm.billing.prices.collectAsState()
    val lobby by vm.lobby.collectAsState()
    // 첫 구매 2배는 가입 후 7일 동안만 (남은 시간 표시)
    val firstBuyLeft = OnlineRepository.firstBuyLeftMs(lobby.profile)
    val firstBuy = firstBuyLeft != null
    val activity = LocalContext.current as Activity
    SoftCard {
        Text(
            tr("💰 포인트 충전", "💰 Get points", zh = "💰 充值积分", hi = "💰 अंक खरीदें", es = "💰 Conseguir puntos",
                fr = "💰 Obtenir des points", ar = "💰 احصل على نقاط", de = "💰 Punkte kaufen", pt = "💰 Comprar pontos",
                ru = "💰 Купить очки", ja = "💰 ポイント購入"),
            fontWeight = FontWeight.Black, fontSize = 20.sp, color = Ui.text,
        )
        Text(
            tr("결제한 포인트는 💰 상점 포인트로 들어가요. 상점 전용이라 계급·랭킹 점수와는 별개예요.",
                "Purchased points go to 💰 shop points. They're for the shop only and don't count toward your rank or leaderboard score.",
                zh = "购买的积分会存入 💰 商店积分，仅限商店使用，不计入等级和排行榜分数。",
                hi = "खरीदे गए पॉइंट 💰 शॉप पॉइंट में जाते हैं। ये सिर्फ़ दुकान के लिए हैं, रैंक या लीडरबोर्ड स्कोर में नहीं गिने जाते।",
                es = "Los puntos comprados van a 💰 puntos de tienda. Solo sirven en la tienda y no cuentan para tu rango ni la clasificación.",
                fr = "Les points achetés vont dans les 💰 points boutique. Ils servent uniquement en boutique et ne comptent ni pour le rang ni pour le classement.",
                ar = "تذهب النقاط المشتراة إلى 💰 نقاط المتجر. وهي للمتجر فقط ولا تُحتسب في الرتبة أو لوحة المتصدرين.",
                de = "Gekaufte Punkte landen als 💰 Shop-Punkte. Sie gelten nur im Shop und zählen nicht für Rang oder Bestenliste.",
                pt = "Os pontos comprados vão para 💰 pontos da loja. São só para a loja e não contam para rank ou ranking.",
                ru = "Купленные очки идут в 💰 очки магазина. Они только для магазина и не влияют на ранг и рейтинг.",
                ja = "購入したポイントは 💰 ショップポイントになります。ショップ専用で、ランクやランキングのスコアには入りません。"),
            fontSize = 12.sp, color = Ui.text.copy(alpha = 0.75f), lineHeight = 17.sp,
        )
        if (firstBuy) {
            Spacer(Modifier.height(6.dp))
            Text(
                tr("🎁 첫 구매 2배! 처음 충전하는 포인트는 두 배로 드려요", "🎁 First purchase x2! Your first top-up is doubled",
                    zh = "🎁 首充双倍！第一次充值的积分翻倍", hi = "🎁 पहली खरीद ×2! पहली बार के अंक दोगुने",
                    es = "🎁 ¡Primera compra x2! Tu primera recarga vale el doble", fr = "🎁 Premier achat x2 ! Ta première recharge est doublée",
                    ar = "🎁 الشراء الأول ×2! أول شحن يُضاعف", de = "🎁 Erster Kauf x2! Deine erste Aufladung wird verdoppelt",
                    pt = "🎁 Primeira compra x2! Sua primeira recarga vale em dobro", ru = "🎁 Первая покупка x2! Первое пополнение удваивается",
                    ja = "🎁 初回購入2倍！最初のチャージは2倍になります") + "\n" + firstBuyLeftText(firstBuyLeft ?: 0),
                fontWeight = FontWeight.Black, fontSize = 14.sp, color = Ui.danger, lineHeight = 19.sp,
                modifier = Modifier.fillMaxWidth().background(Ui.danger.copy(alpha = 0.08f), RoundedCornerShape(12.dp)).padding(8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        BillingManager.PRODUCTS.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                row.forEach { (id, points) ->
                    Button(
                        onClick = { vm.buyPoints(activity, id) },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Ui.gold, contentColor = Ui.text),
                        contentPadding = PaddingValues(vertical = 10.dp, horizontal = 6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "💰 " + "$points",
                                fontWeight = FontWeight.Black, fontSize = 17.sp,
                            )
                            Text(prices[id] ?: "₩$points", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            if (firstBuy) Text("🎁 → 💰 ${points * 2}", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Ui.danger)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // 🚫 광고 제거 (한 번 사면 계속)
        if (lobby.profile?.noAds == true) {
            Text(
                tr("✅ 광고 제거됨 · 전면 광고가 나오지 않아요", "✅ Ads removed · no full-screen ads", zh = "✅ 已去除广告 · 不再显示全屏广告",
                    hi = "✅ विज्ञापन हटाए गए", es = "✅ Anuncios eliminados", fr = "✅ Pubs supprimées", ar = "✅ تمت إزالة الإعلانات",
                    de = "✅ Werbung entfernt", pt = "✅ Anúncios removidos", ru = "✅ Реклама отключена", ja = "✅ 広告削除済み"),
                fontWeight = FontWeight.Bold, color = Ui.accent, fontSize = 13.sp,
            )
        } else {
            OutlinedButton(
                onClick = { vm.buyPoints(activity, BillingManager.REMOVE_ADS) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        tr("🚫 광고 제거", "🚫 Remove ads", zh = "🚫 去除广告", hi = "🚫 विज्ञापन हटाएँ", es = "🚫 Quitar anuncios", fr = "🚫 Supprimer les pubs",
                            ar = "🚫 إزالة الإعلانات", de = "🚫 Werbung entfernen", pt = "🚫 Remover anúncios", ru = "🚫 Убрать рекламу", ja = "🚫 広告を削除") +
                            "  " + (prices[BillingManager.REMOVE_ADS] ?: "₩3,000"),
                        fontWeight = FontWeight.Black, color = Ui.text,
                    )
                    Text(
                        tr("한 번 사면 계속 · 게임 사이 전면 광고 없음 (원할 때 보는 보상 광고는 그대로)",
                            "One-time · no ads between games (optional reward ads stay)",
                            zh = "一次购买永久有效 · 对局间不再有全屏广告（自愿观看的奖励广告保留）",
                            es = "Pago único · sin anuncios entre partidas (los anuncios con recompensa opcionales siguen)",
                            fr = "Achat unique · plus de pubs entre les parties (les pubs récompensées facultatives restent)",
                            de = "Einmalig · keine Werbung zwischen Partien (freiwillige Belohnungs-Werbung bleibt)",
                            pt = "Compra única · sem anúncios entre partidas (anúncios opcionais com recompensa continuam)",
                            ru = "Один раз · без рекламы между играми (добровольная реклама за награду остаётся)",
                            ja = "買い切り · 対戦の合間の広告なし（任意のリワード広告は残ります）"),
                        fontSize = 11.sp, color = Ui.text.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** 상점 부활물약 카드: 광고 보고 1개 받기 (하루 [OnlineRepository.POTION_ADS_PER_DAY]번) */
@Composable
private fun PotionAdButton(vm: AppViewModel, p: com.bubble.game.data.Profile) {
    val activity = LocalContext.current as Activity
    val ready by AdManager.rewardedReady.collectAsState()
    val used = if (p.potionAdDay == OnlineRepository.today()) p.potionAds else 0
    val left = (OnlineRepository.POTION_ADS_PER_DAY - used).coerceAtLeast(0)
    OutlinedButton(
        onClick = { vm.watchAdForPotion(activity) },
        enabled = ready && left > 0,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            if (left > 0) tr("📺 광고 보고 1개 받기 ($left/${OnlineRepository.POTION_ADS_PER_DAY})", "📺 Watch ad for 1 ($left/${OnlineRepository.POTION_ADS_PER_DAY})",
                zh = "📺 看广告领 1 个（$left/${OnlineRepository.POTION_ADS_PER_DAY}）", es = "📺 Mira un anuncio: 1 gratis ($left/${OnlineRepository.POTION_ADS_PER_DAY})",
                fr = "📺 Pub = 1 gratuite ($left/${OnlineRepository.POTION_ADS_PER_DAY})", de = "📺 Werbung = 1 gratis ($left/${OnlineRepository.POTION_ADS_PER_DAY})",
                pt = "📺 Anúncio = 1 grátis ($left/${OnlineRepository.POTION_ADS_PER_DAY})", ru = "📺 Реклама = 1 даром ($left/${OnlineRepository.POTION_ADS_PER_DAY})",
                ja = "📺 広告を見て1個もらう（$left/${OnlineRepository.POTION_ADS_PER_DAY}）")
            else tr("📺 오늘은 다 받았어요 (내일 또!)", "📺 All used today (more tomorrow!)", zh = "📺 今天已领完（明天再来！）", es = "📺 Hoy ya no quedan (¡mañana más!)",
                fr = "📺 Plus rien aujourd’hui (demain !)", de = "📺 Heute aufgebraucht (morgen wieder!)", pt = "📺 Acabou por hoje (amanhã tem mais!)",
                ru = "📺 На сегодня всё (завтра ещё!)", ja = "📺 今日はもう受け取りました（また明日！）"),
            color = Ui.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        )
    }
}

/** 가입 환영 팝업 */
@Composable
private fun WelcomeDialog(kind: String, score: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Critter(Side.CAT, Modifier.size(72.dp), happy = true) },
        title = {
            Text(
                tr("🎉 가입을 환영해요!", "🎉 Welcome to Bubble!", zh = "🎉 欢迎加入！", hi = "🎉 स्वागत है!",
                    es = "🎉 ¡Bienvenido a Bubble!", fr = "🎉 Bienvenue dans Bubble !", ar = "🎉 أهلًا بك في Bubble!",
                    de = "🎉 Willkommen bei Bubble!", pt = "🎉 Bem-vindo ao Bubble!", ru = "🎉 Добро пожаловать в Bubble!",
                    ja = "🎉 ようこそ Bubble へ！"),
                fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Text(
                when (kind) {
                    "returning" -> tr(
                        "다시 오신 걸 환영해요! 탈퇴했던 계정이라 0점부터 시작해요.",
                        "Welcome back! This account was deleted before, so you start from 0 pts.",
                        zh = "欢迎回来！此账号曾被删除，因此从 0 分开始。",
                        hi = "वापसी पर स्वागत है! यह खाता पहले हटाया गया था, इसलिए आप 0 अंक से शुरू करेंगे।",
                        es = "¡Bienvenido de nuevo! Esta cuenta se eliminó antes, así que empiezas con 0 pts.",
                        fr = "Bon retour ! Ce compte a déjà été supprimé, tu repars donc de 0 pt.",
                        ar = "مرحبًا بعودتك! حُذف هذا الحساب من قبل، لذا ستبدأ من 0 نقطة.",
                        de = "Willkommen zurück! Dieses Konto wurde schon einmal gelöscht, daher startest du bei 0 Pkt.",
                        pt = "Bem-vindo de volta! Esta conta foi excluída antes, então você começa com 0 pts.",
                        ru = "С возвращением! Этот аккаунт уже удаляли, поэтому вы начинаете с 0 очк.",
                        ja = "おかえりなさい！以前に削除したアカウントなので0点からのスタートです。",
                    )
                    "admin" -> tr(
                        "관리자 계정으로 ${score}점이 지급됐어요. 동시 접속자 수가 로비 위에 보여요.",
                        "Admin account: you got $score pts. The online player count shows at the top of the lobby.",
                        zh = "管理员账号：已获得 $score 分。大厅顶部会显示在线人数。",
                        hi = "एडमिन खाता: आपको $score अंक मिले। ऑनलाइन खिलाड़ियों की संख्या लॉबी में ऊपर दिखेगी।",
                        es = "Cuenta de administrador: recibiste $score pts. Arriba verás cuántos jugadores hay en línea.",
                        fr = "Compte administrateur : tu as reçu $score pts. Le nombre de joueurs en ligne s’affiche en haut.",
                        ar = "حساب المسؤول: حصلت على $score نقطة. يظهر عدد المتصلين أعلى الردهة.",
                        de = "Admin-Konto: Du hast $score Pkt erhalten. Oben in der Lobby siehst du, wie viele online sind.",
                        pt = "Conta de administrador: você recebeu $score pts. O número de jogadores online aparece no topo.",
                        ru = "Аккаунт администратора: начислено $score очк. Число игроков в сети видно вверху лобби.",
                        ja = "管理者アカウントとして${score}点が付与されました。ロビー上部に同時接続数が表示されます。",
                    )
                    else -> tr(
                        "시작 점수 ${score}점을 드렸어요. 랜덤 매칭과 AI 대전으로 계급을 올려 보세요!",
                        "You start with $score pts. Climb the ranks in random matches and AI battles!",
                        zh = "你获得了 $score 分起始分。通过随机匹配和 AI 对战提升等级吧！",
                        hi = "आप $score अंकों से शुरू करते हैं। रैंडम मैच और AI मुकाबलों से रैंक बढ़ाइए!",
                        es = "Empiezas con $score pts. ¡Sube de rango en partidas aleatorias y contra la IA!",
                        fr = "Tu commences avec $score pts. Grimpe dans les rangs en match aléatoire et contre l’IA !",
                        ar = "تبدأ بـ $score نقطة. ارتقِ في الرتب عبر المباريات العشوائية ومعارك الذكاء الاصطناعي!",
                        de = "Du startest mit $score Pkt. Steig in Zufallsmatches und KI-Duellen im Rang auf!",
                        pt = "Você começa com $score pts. Suba de rank em partidas aleatórias e contra a IA!",
                        ru = "Вы начинаете с $score очк. Повышайте ранг в случайных матчах и боях с ИИ!",
                        ja = "スタート時に${score}点をプレゼント！ランダムマッチとAI対戦でランクを上げよう！",
                    )
                },
                textAlign = TextAlign.Center, lineHeight = 22.sp, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss, shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ui.primary),
            ) {
                Text(tr("시작하기", "Let's go", zh = "开始", hi = "शुरू करें", es = "¡Vamos!", fr = "C’est parti", ar = "لنبدأ",
                    de = "Los geht’s", pt = "Vamos lá", ru = "Поехали", ja = "はじめる"), fontWeight = FontWeight.Bold)
            }
        },
    )
}

/** 매칭 직후 VS 소개: 양쪽 나라·닉네임·계급 */
@Composable
private fun VsIntro(g: GameUi, onClose: () -> Unit) {
    // ⚔️ 매칭 성공 효과음
    LaunchedEffect(Unit) { com.bubble.game.audio.Music.sfx(com.bubble.game.audio.Music.Sfx.MATCH) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF0201530))
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
            Text(
                if (g.fallbackAi) {
                    tr("상대를 찾지 못해 🤖 AI(어려움)와 대결해요", "No opponent found, so you'll face 🤖 AI (Hard)",
                        zh = "没有找到对手，将与 🤖 AI（困难）对战", hi = "विरोधी नहीं मिला, इसलिए 🤖 AI (कठिन) से मुकाबला",
                        es = "No hubo rival, así que juegas contra 🤖 IA (Difícil)", fr = "Aucun adversaire trouvé : tu affrontes 🤖 l’IA (Difficile)",
                        ar = "لم يُعثر على خصم، لذا ستواجه 🤖 الذكاء الاصطناعي (صعب)", de = "Kein Gegner gefunden, du spielst gegen 🤖 KI (Schwer)",
                        pt = "Nenhum rival encontrado, você vai enfrentar 🤖 IA (Difícil)", ru = "Соперник не найден — играете с 🤖 ИИ (Сложно)",
                        ja = "相手が見つからなかったので 🤖 AI（むずかしい）と対戦します")
                } else if (g.friend) {
                    tr("👫 친구와 대전!", "👫 Friend battle!", zh = "👫 好友对战！", es = "👫 ¡Duelo entre amigos!", fr = "👫 Duel entre amis !", de = "👫 Freundesduell!", pt = "👫 Duelo entre amigos!", ru = "👫 Бой с другом!", ja = "👫 友だちと対戦！")
                } else {
                    tr("⚔️ 매칭 완료!", "⚔️ Match found!", zh = "⚔️ 匹配成功！", hi = "⚔️ मैच मिल गया!", es = "⚔️ ¡Rival encontrado!",
                        fr = "⚔️ Adversaire trouvé !", ar = "⚔️ تم العثور على خصم!", de = "⚔️ Gegner gefunden!", pt = "⚔️ Rival encontrado!",
                        ru = "⚔️ Соперник найден!", ja = "⚔️ マッチング成立！")
                },
                fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                VsCard(g.mySide, g.mySkin, g.myName, g.myCountry, g.myScore, isAi = false, Modifier.weight(1f))
                Text("VS", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Ui.gold, modifier = Modifier.padding(horizontal = 10.dp))
                VsCard(g.mySide.opponent, g.opponentSkin, g.opponentName, g.opponentCountry, g.opponentScore, isAi = g.aiLevel != null, Modifier.weight(1f))
            }
            // 동물(시간 줄이기)을 썼으면 크게 알려줌: 갑자기 시간이 줄어 당황하지 않게
            g.opponentSkin?.takeIf { it.consumable }?.let { a ->
                Spacer(Modifier.height(16.dp))
                VsNotice(
                    tr("⏱ 상대가 ${a.label}을(를) 썼어요! 내 턴이 ${a.opponentTurnSec}초로 줄어요", "⏱ Your rival used a ${a.label}! Your turn is ${a.opponentTurnSec}s",
                        zh = "⏱ 对手用了${a.label}！你的回合缩短为 ${a.opponentTurnSec} 秒", es = "⏱ ¡Tu rival usó ${a.label}! Tu turno dura ${a.opponentTurnSec} s",
                        fr = "⏱ Ton adversaire a utilisé ${a.label} ! Ton tour dure ${a.opponentTurnSec} s", de = "⏱ Dein Gegner nutzt ${a.label}! Dein Zug: ${a.opponentTurnSec} s",
                        pt = "⏱ Seu rival usou ${a.label}! Seu turno tem ${a.opponentTurnSec} s", ru = "⏱ Соперник использовал: ${a.label}! Ваш ход — ${a.opponentTurnSec} с",
                        ja = "⏱ 相手が${a.label}を使った！自分のターンは${a.opponentTurnSec}秒"),
                    Ui.danger,
                )
            }
            g.mySkin?.takeIf { it.consumable }?.let { a ->
                Spacer(Modifier.height(10.dp))
                VsNotice(
                    tr("✨ 내 ${a.label} 효과! 상대 턴 ${a.opponentTurnSec}초", "✨ Your ${a.label}: rival's turn is ${a.opponentTurnSec}s",
                        zh = "✨ 我的${a.label}效果！对手回合 ${a.opponentTurnSec} 秒", es = "✨ Tu ${a.label}: el turno rival dura ${a.opponentTurnSec} s",
                        fr = "✨ Ton ${a.label} : tour adverse ${a.opponentTurnSec} s", de = "✨ Dein ${a.label}: Gegnerzug ${a.opponentTurnSec} s",
                        pt = "✨ Seu ${a.label}: turno do rival ${a.opponentTurnSec} s", ru = "✨ Ваш ${a.label}: ход соперника ${a.opponentTurnSec} с",
                        ja = "✨ 自分の${a.label}効果！相手のターン${a.opponentTurnSec}秒"),
                    Ui.accent,
                )
            }
            // 🦄 유니콘 등장 / 내 무지개 축복
            if (g.opponentSkin == Animal.UNICORN) {
                Spacer(Modifier.height(10.dp))
                VsNotice(
                    tr("🦄 다이아 전용 유니콘 등장!", "🦄 A Diamond-only Unicorn appears!", zh = "🦄 钻石专属独角兽登场！", es = "🦄 ¡Aparece un unicornio exclusivo de Diamante!",
                        fr = "🦄 Une licorne réservée au Diamant apparaît !", de = "🦄 Ein Diamant-Einhorn erscheint!", pt = "🦄 Surge um unicórnio exclusivo do Diamante!",
                        ru = "🦄 Появился единорог для Алмазов!", ja = "🦄 ダイヤ専用ユニコーン登場！"),
                    Color(0xFF8E6CF0),
                )
            }
            if (g.mySkin == Animal.UNICORN) {
                Spacer(Modifier.height(10.dp))
                VsNotice(
                    tr("🌈 무지개 축복: 이기면 💰+${Shop.UNICORN_WIN_COINS}", "🌈 Rainbow blessing: win = 💰+${Shop.UNICORN_WIN_COINS}", zh = "🌈 彩虹祝福：赢了 💰+${Shop.UNICORN_WIN_COINS}",
                        es = "🌈 Bendición arcoíris: gana = 💰+${Shop.UNICORN_WIN_COINS}", fr = "🌈 Bénédiction : victoire = 💰+${Shop.UNICORN_WIN_COINS}", de = "🌈 Regenbogensegen: Sieg = 💰+${Shop.UNICORN_WIN_COINS}",
                        pt = "🌈 Bênção arco-íris: vitória = 💰+${Shop.UNICORN_WIN_COINS}", ru = "🌈 Благословение: победа = 💰+${Shop.UNICORN_WIN_COINS}", ja = "🌈 にじの祝福：勝つと 💰+${Shop.UNICORN_WIN_COINS}"),
                    Color(0xFFFF7EB3),
                )
            }
            // 🐰 연승 토끼 5연승 이상이면 알림
            if (g.opponentSkin == Animal.RABBIT && g.opponentRibbons >= Shop.RABBIT_MAX_RIBBONS) {
                Spacer(Modifier.height(10.dp))
                VsNotice(
                    tr("🔥 상대가 연승 토끼로 5연승 이상 중!", "🔥 Your rival's Streak Bunny is on a 5+ win streak!",
                        zh = "🔥 对手的连胜兔已经 5 连胜以上！", es = "🔥 ¡El conejo de tu rival lleva 5+ victorias seguidas!", fr = "🔥 Le lapin adverse est sur 5+ victoires d’affilée !",
                        de = "🔥 Der Siegeshase deines Gegners hat 5+ Siege in Folge!", pt = "🔥 O coelho do rival está com 5+ vitórias seguidas!", ru = "🔥 Зайка соперника — серия 5+ побед!", ja = "🔥 相手の連勝ウサギが5連勝以上中！"),
                    Color(0xFFFF8A3D),
                )
            }
            if (g.mySkin == Animal.RABBIT && g.myRibbons > 0) {
                Spacer(Modifier.height(10.dp))
                val n = g.myRibbons
                VsNotice(
                    if (n >= Shop.RABBIT_MAX_RIBBONS) {
                        tr("🔥 내 연승 토끼 5연승 이상! 이기면 💰+${Shop.RABBIT_MORE_COINS}", "🔥 Your bunny is on 5+ wins! Win = 💰+${Shop.RABBIT_MORE_COINS}",
                            zh = "🔥 我的连胜兔 5 连胜以上！赢了 💰+${Shop.RABBIT_MORE_COINS}", es = "🔥 ¡Tu conejo lleva 5+! Gana = 💰+${Shop.RABBIT_MORE_COINS}", fr = "🔥 Ton lapin est à 5+ ! Victoire = 💰+${Shop.RABBIT_MORE_COINS}",
                            de = "🔥 Dein Hase hat 5+ Siege! Sieg = 💰+${Shop.RABBIT_MORE_COINS}", pt = "🔥 Seu coelho está com 5+! Vitória = 💰+${Shop.RABBIT_MORE_COINS}", ru = "🔥 Ваш зайка: 5+ побед! Победа = 💰+${Shop.RABBIT_MORE_COINS}", ja = "🔥 自分のウサギが5連勝以上！勝つと 💰+${Shop.RABBIT_MORE_COINS}")
                    } else {
                        val next = if (n < 2) 3 else 5
                        val coins = if (next == 3) Shop.RABBIT_3_COINS else Shop.RABBIT_5_COINS
                        tr("🐰 지금 ${n}연승 · ${next}연승이면 💰+$coins", "🐰 $n in a row · $next wins = 💰+$coins",
                            zh = "🐰 当前${n}连胜 · ${next}连胜 💰+$coins", es = "🐰 $n seguidas · $next = 💰+$coins", fr = "🐰 $n d’affilée · $next = 💰+$coins",
                            de = "🐰 $n in Folge · $next = 💰+$coins", pt = "🐰 $n seguidas · $next = 💰+$coins", ru = "🐰 $n подряд · $next = 💰+$coins", ja = "🐰 今${n}連勝 · ${next}連勝で 💰+$coins")
                    },
                    Color(0xFFFF7EB3),
                )
            }
        }
    }
}

@Composable
private fun VsNotice(text: String, color: Color) {
    Text(
        text, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center,
        modifier = Modifier.background(color, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun VsCard(side: Side, skin: Animal?, name: String, country: String?, score: Int?, isAi: Boolean, modifier: Modifier) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = modifier.fillMaxHeight(),
    ) {
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Critter(side, Modifier.size(72.dp), skin = skin, happy = true)
            Text(if (isAi) "🤖" else Country.flag(country), fontSize = 30.sp)
            if (!isAi && country != null) {
                Text(Country.name(country), fontSize = 12.sp, color = Ui.text.copy(alpha = 0.7f), textAlign = TextAlign.Center, maxLines = 1)
            }
            Text(
                name, fontWeight = FontWeight.Black, fontSize = 15.sp, color = Ui.text,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            if (!isAi && score != null && score > 0) RankBadge(score)
        }
    }
}

/**
 * 남은 턴 시간: 큰 숫자 + 줄어드는 게이지.
 * 초록 → 주황 → 빨강으로 바뀌고, 마지막 5초는 깜빡입니다.
 */
@Composable
private fun TurnTimer(g: GameUi, now: Long) {
    val deadline = g.turnDeadline
    val total = (g.turnSec ?: 0) * 1000L
    if (g.finished || deadline == null || total <= 0) return
    val leftMs = (deadline - now).coerceIn(0, total)
    val leftSec = ((leftMs + 999) / 1000).toInt()
    val fraction = leftMs.toFloat() / total
    val urgent = leftMs <= 5_000
    val color = when {
        urgent -> Ui.danger
        fraction > 0.5f -> Color(0xFF43A047)
        else -> Color(0xFFFF9800)
    }
    val t = rememberInfiniteTransition(label = "timer")
    val blink by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(250), RepeatMode.Reverse), label = "blink")
    val alpha = if (urgent) blink else 1f
    val mine = g.turn == g.mySide
    // ❄️ 눈송이 · ✨ 황금 햄스터: 내 시간일 때 게이지 테마 (모습만, 시간은 그대로)
    val theme = if (mine) timerTheme(g.mySkin) else null
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2_400, easing = androidx.compose.animation.core.LinearEasing)), label = "drift")

    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (mine) tr("내 시간", "Your time", zh = "我的时间", hi = "आपका समय", es = "Tu tiempo", fr = "Ton temps",
                    ar = "وقتك", de = "Deine Zeit", pt = "Seu tempo", ru = "Ваше время", ja = "自分の時間")
                else tr("상대 시간", "Their time", zh = "对手时间", hi = "विरोधी का समय", es = "Tiempo rival", fr = "Temps adverse",
                    ar = "وقت الخصم", de = "Gegnerzeit", pt = "Tempo do rival", ru = "Время соперника", ja = "相手の時間"),
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ui.text.copy(alpha = 0.7f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${theme?.icon ?: "⏱"} $leftSec",
                fontSize = if (urgent) 40.sp else 32.sp,
                fontWeight = FontWeight.Black,
                color = if (theme != null && !urgent) theme.colors[2] else color,
                modifier = Modifier.alpha(alpha),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.7f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .alpha(alpha)
                    .then(
                        if (theme != null && !urgent) Modifier.background(Brush.horizontalGradient(theme.colors), RoundedCornerShape(50))
                        else Modifier.background(color, RoundedCornerShape(50)),
                    ),
            )
        }
        // 게이지 위로 눈송이 / 금화가 흩날림
        if (theme != null) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(26.dp).offset(y = (-19).dp)) {
                for (i in 0 until 7) {
                    val x = size.width * fraction * ((i + drift) / 7f)
                    val y = size.height * (0.5f + 0.4f * kotlin.math.sin((drift * 6.28f) + i * 1.7f))
                    fxShape(theme.flake, androidx.compose.ui.geometry.Offset(x, y), 4.dp.toPx() + (i % 3) * 1.dp.toPx(), theme.colors[1 + i % 2].copy(alpha = 0.9f), drift * 360f + i * 40f)
                }
            }
        }
    }
}

/** 부스터 그림: 반짝이는 ×3 */
@Composable
private fun BoostArt() {
    val t = rememberInfiniteTransition(label = "boost")
    val pulse by t.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulse")
    Box(contentAlignment = Alignment.Center) {
        Text(
            "×3",
            fontSize = 44.sp, fontWeight = FontWeight.Black,
            color = Ui.primaryDark,
            modifier = Modifier.scale(pulse),
        )
        Text("🚀", fontSize = 22.sp, modifier = Modifier.align(Alignment.TopEnd).offset(x = 14.dp, y = (-10).dp))
    }
}


/**
 * 🛠 관리자 창: 닉네임으로 찾아(빈 칸이면 나) 점수 · 💰 상점 포인트 · 💖 물약을 넣은 값으로 바꿈.
 * 서버가 관리자 이메일인지 다시 확인하고 기록(adminLogs)을 남김.
 */
@Composable
private fun AdminDialog(vm: AppViewModel, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<com.bubble.game.data.AdminUser>>(emptyList()) }
    var picked by remember { mutableStateOf<com.bubble.game.data.AdminUser?>(null) }
    var score by remember { mutableStateOf("") }
    var coins by remember { mutableStateOf("") }
    var potions by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf(false) }
    fun pick(u: com.bubble.game.data.AdminUser) {
        picked = u; score = u.score.toString(); coins = u.coins.toString(); potions = u.potions.toString()
    }
    LaunchedEffect(Unit) { vm.adminFind("") { list -> found = list; list.firstOrNull()?.let(::pick) } }
    val num = { s: String -> s.filter { it.isDigit() }.take(8) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("🛠 관리자 · 포인트 수정", fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(query, { query = it.take(30) }, singleLine = true, modifier = Modifier.weight(1f),
                        label = { Text("닉네임 (빈 칸 = 나)") })
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = { vm.adminFind(query) { list -> found = list; searched = true; list.firstOrNull()?.let(::pick) } }) { Text("찾기") }
                }
                if (searched && found.isEmpty()) Text("찾는 사용자가 없어요", color = Ui.danger, fontSize = 13.sp)
                found.forEach { u ->
                    Text(
                        (if (picked?.uid == u.uid) "✅ " else "• ") + "${u.name} · ${u.score}점 · 💰${u.coins} · 💖${u.potions}",
                        fontSize = 13.sp, fontWeight = if (picked?.uid == u.uid) FontWeight.Black else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth().clickable { pick(u) }.padding(vertical = 4.dp),
                    )
                }
                picked?.let { u ->
                    Text("고칠 사람: ${u.name}", fontWeight = FontWeight.Bold, color = Ui.accent)
                    OutlinedTextField(score, { score = num(it) }, singleLine = true, label = { Text("점수 (랭킹·계급)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                    OutlinedTextField(coins, { coins = num(it) }, singleLine = true, label = { Text("💰 상점 포인트") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                    OutlinedTextField(potions, { potions = num(it) }, singleLine = true, label = { Text("💖 부활물약") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                    Text("넣은 값으로 바뀌어요 (0 ~ 1000만) · 기록이 남아요", fontSize = 11.sp, color = Ui.text.copy(alpha = 0.6f))
                }
            }
        },
        confirmButton = {
            val u = picked
            TextButton(
                enabled = u != null && score.isNotEmpty() && coins.isNotEmpty() && potions.isNotEmpty(),
                onClick = { if (u != null) { vm.adminSet(u, score.toInt(), coins.toInt(), potions.toInt()); onClose() } },
            ) { Text("저장", fontWeight = FontWeight.Black) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("닫기") } },
    )
}
