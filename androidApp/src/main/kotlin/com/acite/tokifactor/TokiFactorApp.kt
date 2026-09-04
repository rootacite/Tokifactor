package com.acite.tokifactor

import android.app.Application
import dev.zacsweers.metro.createGraph

class TokiFactorApp : Application() {
    val graph: AppGraph by lazy { createGraph<AppGraph>() }

    @Volatile
    var chatUiVisible: Boolean = false

    override fun onCreate() {
        super.onCreate()
        installAppContext(this)
    }
}
