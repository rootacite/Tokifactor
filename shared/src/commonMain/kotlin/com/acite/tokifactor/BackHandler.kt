package com.acite.tokifactor

import androidx.compose.runtime.Composable

@Composable
expect fun OnSystemBack(enabled: Boolean, onBack: () -> Unit)
