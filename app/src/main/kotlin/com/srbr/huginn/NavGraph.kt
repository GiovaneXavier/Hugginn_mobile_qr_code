package com.srbr.huginn

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.srbr.huginn.feature.card.CardScreen
import com.srbr.huginn.feature.onboarding.OnboardingScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val CARD       = "card"
}

@Composable
fun HuginnNavGraph(
    startDestination:    String,
    navController:       NavHostController = rememberNavController(),
    onRequestBiometric:  (onSuccess: () -> Unit) -> Unit,
    onRequestCamera:     (onQRDetected: (String) -> Unit, onPermissionDenied: () -> Unit, onUnavailable: () -> Unit) -> Unit,
    onStopCamera:        () -> Unit
) {
    NavHost(
        navController    = navController,
        startDestination = startDestination
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onRegistered    = {
                    navController.navigate(Routes.CARD) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                onRequestCamera = onRequestCamera,
                onStopCamera    = onStopCamera
            )
        }

        composable(Routes.CARD) {
            CardScreen(
                onRequestBiometric = onRequestBiometric
            )
        }
    }
}
