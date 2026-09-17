package com.livrovivo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.livrovivo.app.core.theme.LivroVivoTheme
import com.livrovivo.app.presentation.navigation.LivroVivoNavGraph

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LivroVivoTheme {
                val navController = rememberNavController()
                LivroVivoNavGraph(navController = navController)
            }
        }
    }
}
