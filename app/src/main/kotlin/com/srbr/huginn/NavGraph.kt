package com.srbr.huginn

import android.net.Uri
import androidx.camera.core.Preview
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.srbr.huginn.feature.card.CardScreen
import com.srbr.huginn.feature.card_list.CardListScreen
import com.srbr.huginn.credential.onboarding.OnboardingScreen

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
    onRequestBiometric:  (onSuccess: () -> Unit) -> Unit,
    onRequestCamera:     (surfaceProvider: Preview.SurfaceProvider, onQRDetected: (String) -> Unit, onPermissionDenied: () -> Unit, onUnavailable: () -> Unit) -> Unit,
    onStopCamera:        () -> Unit
) {
    NavHost(
        navController    = navController,
        startDestination = startDestination
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onRegistered = { systemId, totalCards ->
                    val destination = if (totalCards > 1) Routes.CARD_LIST else Routes.card(systemId)
                    navController.navigate(destination) {
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
            CardScreen(
                onRequestBiometric = onRequestBiometric,
                onBack             = { navController.popBackStack() }
            )
        }
    }
}
