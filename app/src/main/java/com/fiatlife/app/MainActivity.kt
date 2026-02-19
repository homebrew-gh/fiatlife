package com.fiatlife.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fiatlife.app.ui.navigation.FiatLifeNavGraph
import com.fiatlife.app.ui.theme.FiatLifeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FiatLifeTheme {
                FiatLifeNavGraph()
            }
        }
    }
}
