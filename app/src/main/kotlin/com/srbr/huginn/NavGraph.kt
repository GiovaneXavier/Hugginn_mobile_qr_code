package com.srbr.huginn

import android.app.Activity
import android.net.Uri
import androidx.camera.core.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.srbr.huginn.feature.card.CardScreen
import com.srbr.huginn.feature.card_list.CardListScreen
import com.srbr.huginn.feature.onboarding.OnboardingScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val CARD_LIST  = "card_list"
    const val CARD       = "card/{systemId}"

    /** Builds the filled-in route for navigating to a specific card. */
    fun card(systemId: String) = "card/${Uri.encode(systemId)}"
}

@Composable
fun HuginnNavGraph(
    startDestination:    String,
    navController:       NavHostController = rememberNavController(),
    onRequestBiometric:  (onSuccess: () -> Unit, onDismiss: () -> Unit) -> Unit,
    onRequestCamera:     (surfaceProvider: Preview.SurfaceProvider, onQRDetected: (String) -> Unit, onPermissionDenied: () -> Unit, onUnavailable: () -> Unit) -> Unit,
    onStopCamera:        () -> Unit
) {
    NavHost(
        navController    = navController,
        startDestination = startDestination
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onRegistered = { systemId ->
                    navController.navigate(Routes.card(systemId)) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                onRequestCamera = onRequestCamera,
                onStopCamera    = onStopCamera
            )
        }

        composable(Routes.CARD_LIST) {
            CardListScreen(
                onSelectCard = { systemId -> navController.navigate(Routes.card(systemId)) },
                onAddCard    = { navController.navigate(Routes.ONBOARDING) }
            )
        }

        composable(
            route     = Routes.CARD,
            arguments = listOf(navArgument("systemId") { type = NavType.StringType })
        ) {
            val activity = LocalContext.current as Activity
            CardScreen(
                onNavigateBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        activity.moveTaskToBack(true)
                    }
                },
                onRequestBiometric = onRequestBiometric
            )
        }
    }
}
