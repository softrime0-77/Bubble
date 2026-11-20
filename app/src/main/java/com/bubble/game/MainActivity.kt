package com.bubble.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import com.bubble.game.ads.AdManager
import com.bubble.game.i18n.Lang
import com.bubble.game.ui.AppViewModel
import com.bubble.game.ui.BubbleApp
import com.bubble.game.ui.Ui

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Lang.init(this)
        AdManager.start(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Ui.primary, secondary = Ui.accent)) {
                BubbleApp(vm)
            }
        }
    }
}
