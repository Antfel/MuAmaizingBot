package com.example.muamaizingbot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.muamaizingbot.capture.ScreenCapturePermission
import com.example.muamaizingbot.ui.navigation.AppNavigation
import com.example.muamaizingbot.ui.theme.MUAmaizingBotTheme

class MainActivity : ComponentActivity() {

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        ScreenCapturePermission.handleResult(
            context = this,
            resultCode = result.resultCode,
            data = result.data
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MUAmaizingBotTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavigation(
                        onRequestCapture = {
                            captureLauncher.launch(
                                ScreenCapturePermission.createRequestIntent(this)
                            )
                        },
                    )
                }
            }
        }
    }
}
