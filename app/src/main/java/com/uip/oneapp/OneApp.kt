package com.uip.oneapp

import android.app.Application
import com.uip.oneapp.di.appModule
import com.uip.oneapp.maps.OfflineMapRenderer
import com.uip.oneapp.ui.localization.LocalizationManager
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class OneApp : Application() {
    override fun onCreate() {
        super.onCreate()

        LocalizationManager.init(this)

        // MapsForge graphics factory — must run exactly once per process before
        // any .map file is read or rendered. Idempotent inside ensureInitialised().
        OfflineMapRenderer.ensureInitialised(this)

        startKoin {
            androidLogger(Level.DEBUG)
            androidContext(this@OneApp)
            modules(appModule)
        }
    }
}
