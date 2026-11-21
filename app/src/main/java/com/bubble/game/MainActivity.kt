package com.bubble.game

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import com.bubble.game.ads.AdManager
import com.bubble.game.data.Invite
import com.bubble.game.i18n.Lang
import com.bubble.game.ui.AppViewModel
import com.bubble.game.ui.BubbleApp
import com.bubble.game.ui.Ui

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Invite.fromUri(intent.data)?.let(vm::receiveInvite)
        Invite.battleFromUri(intent.data)?.let(vm::receiveBattle)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Lang.init(this)
        AdManager.start(this)
        // 친구 초대: 링크로 열렸거나, 초대 링크로 Play 스토어에서 설치된 경우
        Invite.fromUri(intent?.data)?.let(vm::receiveInvite)
        // 친구 대전 링크로 열린 경우 (로그인되면 바로 대전방에 들어감)
        Invite.battleFromUri(intent?.data)?.let(vm::receiveBattle)
        Invite.checkInstallReferrer(this) { code -> runOnUiThread { vm.receiveInvite(code) } }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Ui.primary, secondary = Ui.accent)) {
                BubbleApp(vm)
            }
        }
    }
}
