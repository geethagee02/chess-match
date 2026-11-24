package dev.geetha.chessmatch

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import dev.geetha.chessmatch.ui.GameScreen
import dev.geetha.chessmatch.ui.GameViewModel
import dev.geetha.chessmatch.ui.theme.ChessMatchTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.parseColor("#12100E")),
            navigationBarStyle = SystemBarStyle.dark(Color.parseColor("#12100E")),
        )
        setContent {
            ChessMatchTheme {
                val viewModel: GameViewModel = hiltViewModel()
                GameScreen(viewModel)
            }
        }
    }
}
