package com.srbr.huginn.feature.onboarding

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srbr.huginn.core.security.HuginnCard
import com.srbr.huginn.ui.components.HuginnCard
import com.srbr.huginn.ui.theme.*

@Composable
fun OnboardingScreen(
    onRegistered:    () -> Unit,
    onRequestCamera: (onQRDetected: (String) -> Unit) -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.step) {
        if (state.step is OnboardingStep.Success) {
            kotlinx.coroutines.delay(2000)
            onRegistered()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState   = state.step,
            transitionSpec = {
                slideInHorizontally { it } + fadeIn() togetherWith
                slideOutHorizontally { -it } + fadeOut()
            },
            label = "onboardingStep"
        ) { step ->
            when (step) {
                is OnboardingStep.Welcome    -> WelcomeStep(
                    onStartScan = {
                        viewModel.onStartScan()
                        onRequestCamera { qr -> viewModel.onQRDetected(qr) }
                    }
                )
                is OnboardingStep.Scanning   -> ScanningStep(
                    onCancel = viewModel::onCancelScan
                )
                is OnboardingStep.Validating -> ValidatingStep(
                    status = state.validatingStatus,
                    sub    = state.validatingSub
                )
                is OnboardingStep.Error      -> ErrorStep(
                    title   = step.title,
                    message = step.message,
                    onRetry = viewModel::onTryAgain
                )
                is OnboardingStep.Success    -> SuccessStep(card = step.card)
            }
        }
    }
}

@Composable
private fun WelcomeStep(onStartScan: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(36.dp)
    ) {
        Text("SRBR", fontSize = 12.sp, color = SamsungBlue,
            fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text("🐦", fontSize = 72.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Huginn", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(12.dp))
        Text("Bem-vindo ao seu guardião de credenciais",
            fontSize = 14.sp, color = SubtleText, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(56.dp))
        Button(
            onClick  = onStartScan,
            modifier = Modifier.width(280.dp).height(56.dp),
            shape    = RoundedCornerShape(28.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = SamsungBlue)
        ) {
            Text("📷  Escanear QR de Cadastro",
                fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "Solicite o QR de cadastro ao\nadministrador do sistema na TI",
            fontSize = 12.sp, color = SubtleText, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ScanningStep(onCancel: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(36.dp)
    ) {
        Text("📷", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Aponte para o QR de cadastro",
            fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text("A câmera abrirá automaticamente",
            fontSize = 13.sp, color = SubtleText, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(40.dp))
        TextButton(onClick = onCancel) {
            Text("CANCELAR", color = SubtleText, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun ValidatingStep(status: String, sub: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(36.dp)
    ) {
        CircularProgressIndicator(color = SamsungBlue, modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(status, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text(sub, fontSize = 13.sp, color = SubtleText, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ErrorStep(title: String, message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(36.dp)
    ) {
        Text("🚨", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(24.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(12.dp))
        Text(message, fontSize = 14.sp, color = MutedText, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(40.dp))
        Button(
            onClick  = onRetry,
            modifier = Modifier.width(240.dp).height(52.dp),
            shape    = RoundedCornerShape(26.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = SamsungBlue)
        ) {
            Text("Tentar Novamente", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SuccessStep(card: HuginnCard) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        Text("✅", fontSize = 56.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Cartão ativado!", fontSize = 26.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(8.dp))
        Text("${card.employeeName} · ${card.systemName}",
            fontSize = 14.sp, color = SubtleText)
        Spacer(modifier = Modifier.height(36.dp))
        HuginnCard(
            card        = card,
            displayId   = card.employeeId,
            isUnlocked  = true
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text("Redirecionando...", fontSize = 13.sp, color = SubtleText)
    }
}
