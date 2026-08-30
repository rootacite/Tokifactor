package com.acite.tokifactor

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform