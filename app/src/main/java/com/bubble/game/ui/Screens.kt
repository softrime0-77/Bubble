package com.bubble.game.ui

import com.bubble.game.i18n.tr
import com.bubble.game.data.Invite
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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

@Composable
fun BubbleApp(vm: AppViewModel) {
    val screen by vm.screen.collectAsState()
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
                Screen.Game -> GameScreen(vm)
                Screen.Shop -> ShopScreen(vm)
            }
        }
        val lobby by vm.lobby.collectAsState()
        val rankUp = lobby.rankUpScore
        if (rankUp != null && screen == Screen.Lobby) RankUpCelebration(rankUp, onDismiss = vm::dismissRankUp)
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
"익명의 상대와 1:1 대결 · 승리 +${OnlineRepository.WIN_POINTS}점",
"1:1 vs an anonymous player · Win +${OnlineRepository.WIN_POINTS} pts",
zh = "与匿名玩家 1:1 对战 · 胜利 +${OnlineRepository.WIN_POINTS} 分", hi = "गुमनाम खिलाड़ी से 1:1 · जीत +${OnlineRepository.WIN_POINTS} अंक", es = "1:1 contra un jugador anónimo · Victoria +${OnlineRepository.WIN_POINTS} pts", fr = "1:1 contre un joueur anonyme · Victoire +${OnlineRepository.WIN_POINTS} pts", ar = "1:1 ضد لاعب مجهول · الفوز +${OnlineRepository.WIN_POINTS} نقطة", de = "1:1 gegen anonyme Gegner · Sieg +${OnlineRepository.WIN_POINTS} Pkt", pt = "1:1 contra um jogador anônimo · Vitória +${OnlineRepository.WIN_POINTS} pts", ru = "1:1 с анонимным игроком · Победа +${OnlineRepository.WIN_POINTS} очк.", ja = "匿名の相手と1対1 · 勝利 +${OnlineRepository.WIN_POINTS}点",
),
            enabled = ui.onlineAvailable && ui.profile != null && !ui.needsLogin,
            onClick = vm::startMatching,
        )
        Spacer(Modifier.height(12.dp))
        SoftCard {
            Text(if (ui.profile != null) tr("🤖 AI 대전", "🤖 AI Battle", zh = "🤖 AI 对战", hi = "🤖 AI मुकाबला", es = "🤖 Batalla contra IA", fr = "🤖 Combat contre l’IA", ar = "🤖 معركة ضد الذكاء الاصطناعي", de = "🤖 KI-Duell", pt = "🤖 Batalha contra IA", ru = "🤖 Бой с ИИ", ja = "🤖 AI対戦") else tr("🤖 AI와 연습하기", "🤖 Practice with AI", zh = "🤖 与 AI 练习", hi = "🤖 AI के साथ अभ्यास", es = "🤖 Practicar con IA", fr = "🤖 S’entraîner contre l’IA", ar = "🤖 تدرّب مع الذكاء الاصطناعي", de = "🤖 Gegen KI üben", pt = "🤖 Treinar com IA", ru = "🤖 Тренировка с ИИ", ja = "🤖 AIと練習"), fontWeight = FontWeight.Bold, color = Ui.text)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiLevel.entries.forEach { lv ->
                    OutlinedButton(
                        onClick = { vm.startAiGame(lv) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    ) { Text(lv.label, color = Ui.accent, maxLines = 1, softWrap = false, fontSize = 14.sp) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (ui.profile != null) {
                    AiLevel.entries.joinToString("  ·  ") { tr(
"${it.label} 승 +${it.winPoints} / 패 ${it.losePoints}",
"${it.label} W +${it.winPoints} / L ${it.losePoints}",
zh = "${it.label} 胜 +${it.winPoints} / 负 ${it.losePoints}", hi = "${it.label} जीत +${it.winPoints} / हार ${it.losePoints}", es = "${it.label} V +${it.winPoints} / D ${it.losePoints}", fr = "${it.label} V +${it.winPoints} / D ${it.losePoints}", ar = "${it.label} فوز +${it.winPoints} / خسارة ${it.losePoints}", de = "${it.label} S +${it.winPoints} / N ${it.losePoints}", pt = "${it.label} V +${it.winPoints} / D ${it.losePoints}", ru = "${it.label} П +${it.winPoints} / Пр ${it.losePoints}", ja = "${it.label} 勝 +${it.winPoints} / 敗 ${it.losePoints}",
) }
                } else {
                    tr(
"로그인하면 AI 대전도 점수가 걸려요",
"Sign in to earn points in AI battles too",
zh = "登录后 AI 对战也能得分", hi = "साइन इन करें, AI मुकाबलों में भी अंक मिलेंगे", es = "Inicia sesión para ganar puntos también contra la IA", fr = "Connecte-toi pour gagner des points contre l’IA aussi", ar = "سجّل الدخول لتكسب نقاطًا في معارك الذكاء الاصطناعي أيضًا", de = "Melde dich an, um auch gegen die KI Punkte zu sammeln", pt = "Entre para ganhar pontos também contra a IA", ru = "Войдите, чтобы получать очки и в боях с ИИ", ja = "ログインするとAI対戦でもポイントがもらえます",
)
                },
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ui.text.copy(alpha = 0.7f),
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
        ui.profile?.let { p -> InviteCard(vm, p, ui.shopBusy) }
        TextButton(onClick = { showRules = true }) { Text(tr("📖 게임 규칙 보기", "📖 How to play", zh = "📖 游戏规则", hi = "📖 कैसे खेलें", es = "📖 Cómo jugar", fr = "📖 Règles du jeu", ar = "📖 طريقة اللعب", de = "📖 Spielregeln", pt = "📖 Como jogar", ru = "📖 Как играть", ja = "📖 遊び方"), color = Ui.text) }
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
                Spacer(Modifier.height(6.dp))
                ui.leaderboard.forEachIndexed { i, p ->
                    val me = p.uid == ui.profile?.uid
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (me) Ui.primary.copy(alpha = 0.15f) else Color.Transparent, RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}" },
                            modifier = Modifier.width(32.dp), color = Ui.text, fontWeight = FontWeight.Bold,
                        )
                        Text(p.name + if (me) tr(" (나)", " (me)", zh = " (我)", hi = " (मैं)", es = " (yo)", fr = " (moi)", ar = " (أنا)", de = " (ich)", pt = " (eu)", ru = " (я)", ja = " (自分)") else "", modifier = Modifier.weight(1f), color = Ui.text)
                        RankBadge(p.score)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("${p.score}점", "${p.score} pts", zh = "${p.score} 分", hi = "${p.score} अंक", es = "${p.score} pts", fr = "${p.score} pts", ar = "${p.score} نقطة", de = "${p.score} Pkt", pt = "${p.score} pts", ru = "${p.score} очк.", ja = "${p.score}点"), color = Ui.primaryDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    ui.welcome?.let { (kind, score) -> WelcomeDialog(kind, score, onDismiss = vm::dismissWelcome) }
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
    if (renaming) {
        var name by remember { mutableStateOf(ui.profile?.name ?: "") }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(tr("닉네임", "Nickname", zh = "昵称", hi = "निकनेम", es = "Apodo", fr = "Pseudo", ar = "اللقب", de = "Spitzname", pt = "Apelido", ru = "Ник", ja = "ニックネーム")) },
            text = {
                OutlinedTextField(name, { name = it.take(12) }, singleLine = true, label = { Text(tr("최대 12자", "Up to 12 characters", zh = "最多 12 个字符", hi = "अधिकतम 12 अक्षर", es = "Hasta 12 caracteres", fr = "12 caractères max.", ar = "حتى 12 حرفًا", de = "Bis zu 12 Zeichen", pt = "Até 12 caracteres", ru = "До 12 символов", ja = "12文字まで")) })
            },
            confirmButton = { TextButton(onClick = { vm.rename(name); renaming = false }) { Text(tr("저장", "Save", zh = "保存", hi = "सेव करें", es = "Guardar", fr = "Enregistrer", ar = "حفظ", de = "Speichern", pt = "Salvar", ru = "Сохранить", ja = "保存")) } },
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
"• 💖 부활물약(상점)이 있으면 터진 방울을 1마리로 살릴 수 있어요. 한 판에 최대 3번!\n\n",
"• 💖 Revive potions (shop) bring a popped bubble back with 1 critter. Up to 3 per game!\n\n",
zh = "• 💖 复活药水（商店）能让破掉的泡泡带着 1 只复活。每局最多 3 次！\n\n", hi = "• 💖 रिवाइव पोशन (दुकान) फटे बबल को 1 जीव के साथ वापस लाता है। हर गेम में अधिकतम 3!\n\n", es = "• 💖 Las pociones de revivir (tienda) devuelven una burbuja reventada con 1 bichito. ¡Hasta 3 por partida!\n\n", fr = "• 💖 Les potions de résurrection (boutique) font revenir une bulle éclatée avec 1 bestiole. Jusqu’à 3 par partie !\n\n", ar = "• 💖 جرعات الإحياء (المتجر) تعيد الفقاعة المنفجرة بمخلوق واحد. حتى 3 في كل مباراة!\n\n", de = "• 💖 Wiederbelebungstränke (Shop) holen eine geplatzte Blase mit 1 Tierchen zurück. Bis zu 3 pro Partie!\n\n", pt = "• 💖 Poções de reviver (loja) trazem de volta uma bolha estourada com 1 bichinho. Até 3 por partida!\n\n", ru = "• 💖 Зелья оживления (магазин) возвращают лопнувший пузырь с 1 зверьком. До 3 за партию!\n\n", ja = "• 💖 復活薬（ショップ）で割れたバブルを1匹で復活できます。1ゲーム最大3回！\n\n",
) +
                    tr(
"• 온라인 대전은 한 턴에 ${OnlineRepository.TURN_LIMIT_MS / 1000}초! 시간을 넘기면 패배예요.\n",
"• Online turns last ${OnlineRepository.TURN_LIMIT_MS / 1000}s. Run out of time and you lose.\n",
zh = "• 在线对战每回合 ${OnlineRepository.TURN_LIMIT_MS / 1000} 秒，超时即判负。\n", hi = "• ऑनलाइन हर चाल ${OnlineRepository.TURN_LIMIT_MS / 1000} सेकंड। समय खत्म तो हार।\n", es = "• En línea cada turno dura ${OnlineRepository.TURN_LIMIT_MS / 1000} s. Si se acaba el tiempo, pierdes.\n", fr = "• En ligne, chaque tour dure ${OnlineRepository.TURN_LIMIT_MS / 1000} s. Temps écoulé = défaite.\n", ar = "• مدة الدور عبر الإنترنت ${OnlineRepository.TURN_LIMIT_MS / 1000} ثانية. إذا انتهى الوقت تخسر.\n", de = "• Online dauert ein Zug ${OnlineRepository.TURN_LIMIT_MS / 1000} s. Zeit abgelaufen = verloren.\n", pt = "• Online, cada turno dura ${OnlineRepository.TURN_LIMIT_MS / 1000} s. Se o tempo acabar, você perde.\n", ru = "• Онлайн ход длится ${OnlineRepository.TURN_LIMIT_MS / 1000} с. Время вышло — поражение.\n", ja = "• オンラインの1ターンは${OnlineRepository.TURN_LIMIT_MS / 1000}秒。時間切れで負けです。\n",
) +
                    tr(
                    "   상대가 양·강아지·기린을 썼다면 내 턴이 15·10·5초로 줄어요.\n\n",
                    "   If your opponent uses a sheep, puppy or giraffe, your turn drops to 15, 10 or 5s.\n\n",
                    zh = "   如果对手使用了绵羊、小狗或长颈鹿，你的回合会缩短为 15、10 或 5 秒。\n\n",
                    hi = "   अगर विरोधी ने भेड़, पिल्ला या जिराफ़ इस्तेमाल किया, तो आपकी बारी 15, 10 या 5 सेकंड की होगी।\n\n",
                    es = "   Si tu rival usa una oveja, un perrito o una jirafa, tu turno baja a 15, 10 o 5 s.\n\n",
                    fr = "   Si ton adversaire utilise un mouton, un chiot ou une girafe, ton tour passe à 15, 10 ou 5 s.\n\n",
                    ar = "   إذا استخدم خصمك خروفًا أو جروًا أو زرافة، يقل دورك إلى 15 أو 10 أو 5 ثوانٍ.\n\n",
                    de = "   Nutzt dein Gegner Schaf, Welpe oder Giraffe, sinkt dein Zug auf 15, 10 oder 5 s.\n\n",
                    pt = "   Se o rival usar ovelha, cachorrinho ou girafa, seu turno cai para 15, 10 ou 5 s.\n\n",
                    ru = "   Если соперник использует овечку, щенка или жирафа, ваш ход сокращается до 15, 10 или 5 с.\n\n",
                    ja = "   相手がヒツジ・子イヌ・キリンを使うと、自分のターンが15・10・5秒になります。\n\n",
                ) +
                    tr(
                        "• 계급: 1000점 이하 흙 → 구리(1001) → 은(1500) → 금(2000) → 루비(5000) → 사파이어(7000) → 다이아(10000)",
                        "• Ranks: Dirt (to 1000) → Copper (1001) → Silver (1500) → Gold (2000) → Ruby (5000) → Sapphire (7000) → Diamond (10000)",
                        zh = "• 等级：泥土（≤1000）→ 铜（1001）→ 银（1500）→ 金（2000）→ 红宝石（5000）→ 蓝宝石（7000）→ 钻石（10000）",
                        hi = "• रैंक: मिट्टी (1000 तक) → कॉपर (1001) → सिल्वर (1500) → गोल्ड (2000) → रूबी (5000) → नीलम (7000) → डायमंड (10000)",
                        es = "• Rangos: Tierra (hasta 1000) → Cobre (1001) → Plata (1500) → Oro (2000) → Rubí (5000) → Zafiro (7000) → Diamante (10000)",
                        fr = "• Rangs : Terre (jusqu’à 1000) → Cuivre (1001) → Argent (1500) → Or (2000) → Rubis (5000) → Saphir (7000) → Diamant (10000)",
                        ar = "• الرتب: تراب (حتى 1000) ← نحاس (1001) ← فضة (1500) ← ذهب (2000) ← ياقوت أحمر (5000) ← ياقوت أزرق (7000) ← ماس (10000)",
                        de = "• Ränge: Erde (bis 1000) → Kupfer (1001) → Silber (1500) → Gold (2000) → Rubin (5000) → Saphir (7000) → Diamant (10000)",
                        pt = "• Ranks: Terra (até 1000) → Cobre (1001) → Prata (1500) → Ouro (2000) → Rubi (5000) → Safira (7000) → Diamante (10000)",
                        ru = "• Ранги: Земля (до 1000) → Медь (1001) → Серебро (1500) → Золото (2000) → Рубин (5000) → Сапфир (7000) → Алмаз (10000)",
                        ja = "• ランク：土（〜1000）→ 銅（1001）→ 銀（1500）→ 金（2000）→ ルビー（5000）→ サファイア（7000）→ ダイヤ（10000）",
                    ),
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ui.text,
            )
        },
    )
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
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(20.dp)) { Text(tr("매칭 취소", "Cancel", zh = "取消", hi = "रद्द करें", es = "Cancelar", fr = "Annuler", ar = "إلغاء", de = "Abbrechen", pt = "Cancelar", ru = "Отмена", ja = "キャンセル"), color = Ui.text) }
    }
}

// ======================= 게임 =======================

@Composable
private fun GameScreen(vm: AppViewModel) {
    val g by vm.game.collectAsState()
    var selected by remember { mutableStateOf<Int?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }

    // 차례가 바뀌거나 판이 바뀌면 선택 해제
    LaunchedEffect(g.turn, g.board) {
        val s = selected
        if (!g.myTurn || (s != null && g.board.sets(g.mySide)[s] == 0)) selected = null
    }
    BackHandler { if (g.finished) vm.leaveGame() else confirmLeave = true }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(250); now = System.currentTimeMillis() } }
    val remain = g.turnDeadline?.let { ((it - now) / 1000).coerceAtLeast(0) }

    val me = g.mySide
    val opp = me.opponent
    val mine = g.board.sets(me)
    val theirs = g.board.sets(opp)
    val sel = selected
    val power = sel?.let { mine[it] } ?: 0

    fun previewFor(target: Int) = if (target + power >= GameLogic.EXPLODE_AT) tr("💥 펑!", "💥 POP!", zh = "💥 啪！", hi = "💥 फट!", es = "💥 ¡PUM!", fr = "💥 POP !", ar = "💥 بوب!", de = "💥 PLOPP!", pt = "💥 POP!", ru = "💥 ХЛОП!", ja = "💥 パン！") else "→ ${target + power}"

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 상단 바
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (g.finished) vm.leaveGame() else confirmLeave = true }) {
                    Text(tr("← 나가기", "← Leave", zh = "← 离开", hi = "← छोड़ें", es = "← Salir", fr = "← Quitter", ar = "→ خروج", de = "← Verlassen", pt = "← Sair", ru = "← Выйти", ja = "← 退出"), color = Ui.text)
                }
                Spacer(Modifier.weight(1f))
                if (remain != null && !g.finished) {
                    Text(
                        tr("⏱ ${remain}초", "⏱ ${remain}s", zh = "⏱ ${remain}秒", hi = "⏱ ${remain}से", es = "⏱ ${remain}s", fr = "⏱ ${remain}s", ar = "⏱ ${remain}ث", de = "⏱ ${remain}s", pt = "⏱ ${remain}s", ru = "⏱ ${remain}с", ja = "⏱ ${remain}秒"),
                        color = if (remain <= 10) Ui.danger else Ui.text,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerTag(opp, g.opponentName, g.opponentScore?.let { tr("${it}점", "${it} pts", zh = "${it} 分", hi = "${it} अंक", es = "${it} pts", fr = "${it} pts", ar = "${it} نقطة", de = "${it} Pkt", pt = "${it} pts", ru = "${it} очк.", ja = "${it}点") }, active = g.turn == opp && !g.finished, skin = g.opponentSkin, modifier = Modifier.weight(1f, fill = false))
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
                        preview = if (target) previewFor(theirs[i]) else null,
                        explodeKey = g.explosion?.takeIf { it.side == opp && it.index == i }?.id,
                        onClick = if (target) ({ vm.play(Move.Add(sel!!, true, i)); selected = null }) else null,
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            TurnBanner(g, sel != null)
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
                        preview = if (boostTarget) "💪 " + previewFor(mine[i]) else null,
                        explodeKey = g.explosion?.takeIf { it.side == me && it.index == i }?.id,
                        onClick = when {
                            boostTarget -> ({ vm.play(Move.Add(sel!!, false, i)); selected = null })
                            pickable -> ({ selected = if (isSel) null else i })
                            else -> null
                        },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerTag(me, if (g.myName == tr("나", "Me", zh = "我", hi = "मैं", es = "Yo", fr = "Moi", ar = "أنا", de = "Ich", pt = "Eu", ru = "Я", ja = "自分")) tr("나", "Me", zh = "我", hi = "मैं", es = "Yo", fr = "Moi", ar = "أنا", de = "Ich", pt = "Eu", ru = "Я", ja = "自分") else g.myName + tr(" (나)", " (me)", zh = " (我)", hi = " (मैं)", es = " (yo)", fr = " (moi)", ar = " (أنا)", de = " (ich)", pt = " (eu)", ru = " (я)", ja = " (自分)"), null, active = g.myTurn, skin = g.mySkin, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                SplitCounter(g.board.splitsLeft(me))
                ReviveCounter(g.board.revivesLeft(me))
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

        if (g.finished && g.winner != null) {
            val activity = LocalContext.current as Activity
            val rewardedReady by AdManager.rewardedReady.collectAsState()
            var showRewardIntro by remember { mutableStateOf(false) }
            val lostOnline = g.online && g.winner != g.mySide && (g.scoreDelta ?: 0) < 0
            val canOfferReward = lostOnline && rewardedReady && vm.rewardTarget() != null
            ResultOverlay(
                g,
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
        Spacer(Modifier.width(6.dp))
        // 이름이 길면 말줄임 (옆의 분할·물약 표시가 찌그러지지 않게)
        Text(
            "${skin?.label ?: side.label} · $name",
            color = Ui.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
        )
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
private fun ResultOverlay(g: GameUi, rewardOffer: Int?, onLobby: () -> Unit, onAgain: () -> Unit) {
    val won = g.winner == g.mySide
    val t = rememberInfiniteTransition(label = "result")
    val jump by t.animateFloat(0f, 1f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "jump")
    Box(
        Modifier.fillMaxSize().background(Color(0x88302040)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.padding(32.dp),
        ) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Critter(
                    g.mySide, Modifier.size(120.dp).offset(y = (if (won) -jump * 16 else 0f).dp),
                    happy = won,
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
                    if (g.scoreBonus > 0) {
                        Text(
                            tr("✨ 계급 보너스 +${g.scoreBonus}점 포함", "✨ Includes rank bonus +${g.scoreBonus}", zh = "✨ 含等级奖励 +${g.scoreBonus}", hi = "✨ रैंक बोनस +${g.scoreBonus} शामिल", es = "✨ Incluye bonus de rango +${g.scoreBonus}", fr = "✨ Bonus de rang inclus +${g.scoreBonus}", ar = "✨ يشمل مكافأة الرتبة +${g.scoreBonus}", de = "✨ Inkl. Rangbonus +${g.scoreBonus}", pt = "✨ Inclui bônus de rank +${g.scoreBonus}", ru = "✨ С бонусом ранга +${g.scoreBonus}", ja = "✨ ランクボーナス +${g.scoreBonus} 込み"),
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ui.gold.copy(red = 0.85f, green = 0.6f, blue = 0f),
                        )
                    }
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

@Composable
private fun ShopScreen(vm: AppViewModel) {
    val ui by vm.lobby.collectAsState()
    val p = ui.profile
    BackHandler(onBack = vm::closeShop)
    var confirm by remember { mutableStateOf<Triple<String, String, Int>?>(null) }

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

        // 부활물약
        ShopCard(
            title = tr("💖 부활물약", "💖 Revive Potion", zh = "💖 复活药水", hi = "💖 रिवाइव पोशन", es = "💖 Poción de revivir", fr = "💖 Potion de résurrection", ar = "💖 جرعة الإحياء", de = "💖 Wiederbelebungstrank", pt = "💖 Poção de reviver", ru = "💖 Зелье оживления", ja = "💖 復活薬"),
            desc = tr(
"내 차례에 터진(0마리) 방울 하나를 1마리로 살려요.\n한 판에 최대 ${Shop.MAX_REVIVES_PER_GAME}번 · 쓸 때마다 1개 소모 · 온라인 대전 전용",
"On your turn, bring a popped (empty) bubble back with 1 critter.\nUp to ${Shop.MAX_REVIVES_PER_GAME} per game · 1 used each time · online only",
zh = "在你的回合，让一个破掉（空）的泡泡带着 1 只复活。\n每局最多 ${Shop.MAX_REVIVES_PER_GAME} 次 · 每次消耗 1 瓶 · 仅限在线对战", hi = "अपनी बारी में फटे (खाली) बबल को 1 जीव के साथ वापस लाएँ।\nहर गेम में अधिकतम ${Shop.MAX_REVIVES_PER_GAME} · हर बार 1 खर्च · सिर्फ़ ऑनलाइन", es = "En tu turno, revive una burbuja reventada (vacía) con 1 bichito.\nHasta ${Shop.MAX_REVIVES_PER_GAME} por partida · gasta 1 cada vez · solo en línea", fr = "À ton tour, ranime une bulle éclatée (vide) avec 1 bestiole.\nJusqu’à ${Shop.MAX_REVIVES_PER_GAME} par partie · 1 utilisée à chaque fois · en ligne uniquement", ar = "في دورك، أعِد فقاعة منفجرة (فارغة) بمخلوق واحد.\nحتى ${Shop.MAX_REVIVES_PER_GAME} في كل مباراة · تُستهلك واحدة كل مرة · عبر الإنترنت فقط", de = "Hol in deinem Zug eine geplatzte (leere) Blase mit 1 Tierchen zurück.\nBis zu ${Shop.MAX_REVIVES_PER_GAME} pro Partie · 1 pro Einsatz · nur online", pt = "Na sua vez, reviva uma bolha estourada (vazia) com 1 bichinho.\nAté ${Shop.MAX_REVIVES_PER_GAME} por partida · gasta 1 por uso · só online", ru = "В свой ход верните лопнувший (пустой) пузырь с 1 зверьком.\nДо ${Shop.MAX_REVIVES_PER_GAME} за партию · 1 за раз · только онлайн", ja = "自分の番に、割れた（空の）バブルを1匹で復活させます。\n1ゲーム最大${Shop.MAX_REVIVES_PER_GAME}回 · 使うたびに1個消費 · オンライン対戦のみ",
),
            price = Shop.POTION_PRICE,
            owned = tr("보유 ${p.potions}개", "Owned: ${p.potions}", zh = "拥有：${p.potions}", hi = "पास में: ${p.potions}", es = "Tienes: ${p.potions}", fr = "Possédées : ${p.potions}", ar = "لديك: ${p.potions}", de = "Im Besitz: ${p.potions}", pt = "Você tem: ${p.potions}", ru = "Есть: ${p.potions}", ja = "所持：${p.potions}個"),
            art = { PotionArt(Modifier.size(84.dp)) },
        ) {
            Button(
                onClick = { confirm = Triple("POTION", tr("부활물약", "Revive Potion", zh = "复活药水", hi = "रिवाइव पोशन", es = "Poción de revivir", fr = "Potion de résurrection", ar = "جرعة الإحياء", de = "Wiederbelebungstrank", pt = "Poção de reviver", ru = "Зелье оживления", ja = "復活薬"), Shop.POTION_PRICE) },
                enabled = !ui.shopBusy && p.score + p.coins >= Shop.POTION_PRICE,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ui.primary),
            ) { Text(tr("${Shop.POTION_PRICE}점에 사기", "Buy for ${Shop.POTION_PRICE} pts", zh = "${Shop.POTION_PRICE} 分购买", hi = "${Shop.POTION_PRICE} अंक में खरीदें", es = "Comprar por ${Shop.POTION_PRICE} pts", fr = "Acheter pour ${Shop.POTION_PRICE} pts", ar = "اشترِ مقابل ${Shop.POTION_PRICE} نقطة", de = "Für ${Shop.POTION_PRICE} Pkt kaufen", pt = "Comprar por ${Shop.POTION_PRICE} pts", ru = "Купить за ${Shop.POTION_PRICE} очк.", ja = "${Shop.POTION_PRICE}点で買う"), fontWeight = FontWeight.Bold) }
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

        Animal.entries.forEach { a ->
            val count = p.animalCount(a)
            val have = count > 0
            val on = p.equipped == a && have
            ShopCard(
                title = a.label,
                desc = tr(
"⏱ 상대 턴 시간 ${Shop.DEFAULT_TURN_SEC}초 → ${a.opponentTurnSec}초",
"⏱ Opponent turn ${Shop.DEFAULT_TURN_SEC}s → ${a.opponentTurnSec}s",
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { confirm = Triple(a.name, a.label, a.price) },
                        enabled = !ui.shopBusy && p.score + p.coins >= a.price,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Ui.primary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) { Text(tr("${a.price}점에 사기", "Buy for ${a.price} pts", zh = "${a.price} 分购买", hi = "${a.price} अंक में खरीदें", es = "Comprar por ${a.price} pts", fr = "Acheter pour ${a.price} pts", ar = "اشترِ مقابل ${a.price} نقطة", de = "Für ${a.price} Pkt kaufen", pt = "Comprar por ${a.price} pts", ru = "Купить за ${a.price} очк.", ja = "${a.price}点で買う"), fontWeight = FontWeight.Bold) }
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

    confirm?.let { (item, label, price) ->
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
            confirmButton = { TextButton(onClick = { vm.buy(item, label); confirm = null }) { Text(tr("살게요", "Buy", zh = "购买", hi = "खरीदें", es = "Comprar", fr = "Acheter", ar = "شراء", de = "Kaufen", pt = "Comprar", ru = "Купить", ja = "買う")) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(tr("취소", "Cancel", zh = "取消", hi = "रद्द करें", es = "Cancelar", fr = "Annuler", ar = "إلغاء", de = "Abbrechen", pt = "Cancelar", ru = "Отмена", ja = "キャンセル")) } },
        )
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
                    Text(title, fontWeight = FontWeight.Black, fontSize = 20.sp, color = Ui.text)
                    Spacer(Modifier.width(6.dp))
if (price != null) Text(tr("${price}점", "${price} pts", zh = "${price} 分", hi = "${price} अंक", es = "${price} pts", fr = "${price} pts", ar = "${price} نقطة", de = "${price} Pkt", pt = "${price} pts", ru = "${price} очк.", ja = "${price}点"), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ui.primaryDark)
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
            Column {
                Rank.entries.reversed().forEach { r ->
                    val isMine = r == mine
                    val range = when (r) {
                        Rank.DIRT -> tr("1000점 이하", "Up to 1000 pts", zh = "1000 分以下", hi = "1000 अंक तक", es = "Hasta 1000 pts", fr = "Jusqu’à 1000 pts", ar = "حتى 1000 نقطة", de = "Bis 1000 Pkt", pt = "Até 1000 pts", ru = "До 1000 очк.", ja = "1000点以下")
                        Rank.DIAMOND -> tr("${r.minScore}점 이상", "${r.minScore}+ pts", zh = "${r.minScore} 分以上", hi = "${r.minScore}+ अंक", es = "${r.minScore}+ pts", fr = "${r.minScore}+ pts", ar = "${r.minScore}+ نقطة", de = "${r.minScore}+ Pkt", pt = "${r.minScore}+ pts", ru = "${r.minScore}+ очк.", ja = "${r.minScore}点以上")
                        else -> tr(
"${r.minScore} ~ ${Rank.entries[r.ordinal + 1].minScore - 1}점",
"${r.minScore} – ${Rank.entries[r.ordinal + 1].minScore - 1} pts",
zh = "${r.minScore} – ${Rank.entries[r.ordinal + 1].minScore - 1} 分", hi = "${r.minScore} – ${Rank.entries[r.ordinal + 1].minScore - 1} अंक", es = "${r.minScore} – ${Rank.entries[r.ordinal + 1].minScore - 1} pts", fr = "${r.minScore} – ${Rank.entries[r.ordinal + 1].minScore - 1} pts", ar = "${r.minScore} – ${Rank.entries[r.ordinal + 1].minScore - 1} نقطة", de = "${r.minScore} – ${Rank.entries[r.ordinal + 1].minScore - 1} Pkt", pt = "${r.minScore} – ${Rank.entries[r.ordinal + 1].minScore - 1} pts", ru = "${r.minScore} – ${Rank.entries[r.ordinal + 1].minScore - 1} очк.", ja = "${r.minScore}〜${Rank.entries[r.ordinal + 1].minScore - 1}点",
)
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(
                                if (isMine) Ui.primary.copy(alpha = 0.15f) else Color.Transparent,
                                RoundedCornerShape(14.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 9.dp),
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
            tr("친구가 내 코드를 입력하면 내가 +30점!", "Get +30 pts when a friend enters your code!",
                zh = "好友输入你的邀请码，你就得 +30 分！", hi = "दोस्त आपका कोड डाले तो आपको +30 अंक!",
                es = "¡Gana +30 pts cuando un amigo use tu código!", fr = "Gagne +30 pts quand un ami entre ton code !",
                ar = "احصل على +30 نقطة عندما يُدخل صديقك رمزك!", de = "+30 Pkt, wenn ein Freund deinen Code eingibt!",
                pt = "Ganhe +30 pts quando um amigo usar seu código!", ru = "+30 очк., когда друг введёт ваш код!",
                ja = "友だちが自分のコードを入力すると +30点！"),
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


/** 상점 맨 위: 포인트 충전 (Google Play 결제). 100 · 500 · 1000 · 2000점 */
@Composable
private fun PointsCard(vm: AppViewModel) {
    val prices by vm.billing.prices.collectAsState()
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
                        }
                    }
                }
            }
        }
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
