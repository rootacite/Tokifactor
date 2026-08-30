package com.acite.tokifactor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.acite.tokifactor.pages.MainPage
import com.acite.tokifactor.pages.SettingPage
import com.acite.tokifactor.ui.theme.TokiFactorTheme
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import org.jetbrains.compose.resources.painterResource

import tokifactor.shared.generated.resources.Res
import tokifactor.shared.generated.resources.compose_multiplatform

private const val ROUTE_CHAT = "chat"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun App(
    metroVmf: MetroViewModelFactory
) {
    TokiFactorTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            CompositionLocalProvider(LocalMetroViewModelFactory provides metroVmf) {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = ROUTE_CHAT,
                    enterTransition = {
                        slideInHorizontally(
                            animationSpec = tween(durationMillis = 280),
                            initialOffsetX = { fullWidth -> fullWidth },
                        )
                    },
                    exitTransition = {
                        slideOutHorizontally(
                            animationSpec = tween(durationMillis = 280),
                            targetOffsetX = { fullWidth -> -fullWidth / 4 },
                        )
                    },
                    popEnterTransition = {
                        slideInHorizontally(
                            animationSpec = tween(durationMillis = 280),
                            initialOffsetX = { fullWidth -> -fullWidth / 4 },
                        )
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            animationSpec = tween(durationMillis = 280),
                            targetOffsetX = { fullWidth -> fullWidth },
                        )
                    },
                ) {
                    composable(ROUTE_CHAT) {
                        MainPage(
                            onOpenSettings = {
                                navController.navigate(ROUTE_SETTINGS) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                    composable(ROUTE_SETTINGS) {
                        SettingPage(
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}