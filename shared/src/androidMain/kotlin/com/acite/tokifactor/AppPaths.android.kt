package com.acite.tokifactor

import android.annotation.SuppressLint
import android.content.Context
import java.io.File

@SuppressLint("StaticFieldLeak")
private var androidAppContext: Context? = null

fun installAppContext(context: Context) {
    androidAppContext = context.applicationContext
}

internal fun requireAppContext(): Context =
    androidAppContext
        ?: Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null) as Context

actual fun appConfigFile(): File = File(requireAppContext().filesDir, "config.toml")

actual fun defaultDisplayName(): String =
    android.os.Build.MODEL.ifBlank { "Android" }
