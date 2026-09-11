package com.logiclabs.app

import android.app.Application
import com.logiclabs.app.di.AppContainer

/**
 * Process-wide owner of the repositories.
 *
 * The app deliberately uses no DI framework: four stateless-over-a-Context repositories
 * do not justify one, and a hand-rolled container keeps construction order obvious.
 * Nothing here does work at process start beyond allocating four small objects — every
 * DataStore is created lazily on first read, so the splash screen is never held up by
 * disk I/O.
 */
class LogicLabsApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
