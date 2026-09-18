package com.livrovivo.app

import android.app.Application
import com.livrovivo.app.core.billing.BillingManager
import com.livrovivo.app.core.di.appModule
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class LivroVivoApp : Application() {

    private val billingManager: BillingManager by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@LivroVivoApp)
            modules(appModule)
        }

        // Conecta à loja e restaura a assinatura de quem já pagou (troca de aparelho,
        // reinstalação) antes mesmo de alguém abrir a tela de assinatura.
        billingManager.start()
    }
}
