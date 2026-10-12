package com.maxrave.simpmusic.ui.component

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.maxrave.simpmusic.expect.ui.PlatformBackdrop
import com.maxrave.simpmusic.viewModel.SharedViewModel
import kotlin.reflect.KClass

/** The liquid-glass bar is Android-only (it is built on ConstraintLayout); iOS uses the standard bar. */
@Composable
actual fun LiquidGlassAppBottomNavigationBar(
    startDestination: Any,
    navController: NavController,
    backdrop: PlatformBackdrop,
    viewModel: SharedViewModel,
    isScrolledToTop: Boolean,
    showAnalyticsTab: Boolean,
    showMixForYouTab: Boolean,
    onOpenNowPlaying: () -> Unit,
    reloadDestinationIfNeeded: (KClass<*>) -> Unit,
) {
    AppBottomNavigationBar(
        startDestination = startDestination,
        navController = navController,
        showAnalyticsTab = showAnalyticsTab,
        showMixForYouTab = showMixForYouTab,
        reloadDestinationIfNeeded = reloadDestinationIfNeeded,
    )
}
