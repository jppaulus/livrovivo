package com.livrovivo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.livrovivo.app.core.billing.BillingManager
import com.livrovivo.app.core.theme.LivroVivoTheme
import com.livrovivo.app.presentation.navigation.LivroVivoNavGraph
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val billingManager: BillingManager by inject()

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

    /**
     * O usuário pode assinar, cancelar ou ter a cobrança recusada fora do app (na Play Store,
     * no site do Google). Voltar para o app é a hora certa de reconferir com a loja.
     */
    override fun onResume() {
        super.onResume()
        billingManager.start()
    }
}
