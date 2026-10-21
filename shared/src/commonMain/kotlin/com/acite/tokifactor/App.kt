package com.acite.tokifactor

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.acite.tokifactor.pages.MainPage
import com.acite.tokifactor.pages.SettingPage
import com.acite.tokifactor.services.SettingsStore
import com.acite.tokifactor.ui.theme.Appearance
import com.acite.tokifactor.ui.theme.TokiBackdrop
import com.acite.tokifactor.ui.theme.TokiFactorTheme
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory

private const val ROUTE_CHAT = "chat"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun App(
    metroVmf: MetroViewModelFactory,
    settingsStore: SettingsStore,
) {
    var fontScale by remember { mutableFloatStateOf(settingsStore.fontScale) }
    var appearanceId by remember { mutableStateOf(settingsStore.appearanceId) }
    TokiFactorTheme(
        appearance = Appearance.byId(appearanceId),
        fontScale = fontScale,
    ) {
        TokiBackdrop {
            CompositionLocalProvider(LocalMetroViewModelFactory provides metroVmf) {
                val navController = rememberNavController()
                NavHost(
                    modifier = Modifier.fillMaxSize(),
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
                            },
                        )
                    }
                    composable(ROUTE_SETTINGS) {
                        SettingPage(
                            onBack = {
                                fontScale = settingsStore.fontScale
                                appearanceId = settingsStore.appearanceId
                                navController.popBackStack()
                            },
                            onFontScaleChange = { fontScale = it },
                            onAppearanceChange = { appearanceId = it },
                        )
                    }
                }
            }
        }
    }
}
