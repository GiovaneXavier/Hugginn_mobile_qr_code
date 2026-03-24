package com.srbr.huginn.feature.card

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srbr.huginn.ui.components.HuginnCard
import com.srbr.huginn.ui.components.QrCode
import com.srbr.huginn.ui.theme.*

@Composable
fun CardScreen(
    onRequestBiometric: (onSuccess: () -> Unit) -> Unit,
    viewModel: CardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(Modifier.systemBarsPadding()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {

            // Label superior
            Text(
                text          = "SRBR · HEIMDALL",
                fontSize      = 11.sp,
                color         = SubtleText,
                letterSpacing = 2.sp,
                fontWeight    = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(32.dp))

            // Cartão com animação de flip 3D
            HuginnCard(
                card       = state.card,
                displayId  = state.displayId,
                isUnlocked = state.isUnlocked
            )

            Spacer(modifier = Modifier.height(32.dp))

            // QR Code dinâmico (visível apenas quando desbloqueado)
            QrCode(
                token   = state.qrToken,
                visible = state.isUnlocked
            )

            // Espaçamento condicional: maior quando bloqueado (sem QR)
            Spacer(modifier = Modifier.height(if (state.isUnlocked) 20.dp else 40.dp))

            // Status textual
            AnimatedContent(
                targetState = state.isUnlocked,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "status"
            ) { unlocked ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text       = if (unlocked) "Aponte o QR para o leitor Heimdall"
                                     else "Toque para autenticar",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = if (unlocked) SuccessGreen else MutedText,
                        textAlign  = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text      = if (unlocked) "QR ativo · Biometria confirmada · Renova a cada 10s"
                                    else "Sua biometria confirma que você é o proprietário",
                        fontSize  = 12.sp,
                        color     = SubtleText,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Botão de autenticação ou barra de countdown
            AnimatedVisibility(visible = !state.isUnlocked) {
                Button(
                    onClick = {
                        onRequestBiometric { viewModel.onBiometricSuccess() }
                    },
                    modifier = Modifier
                        .width(280.dp)
                        .height(52.dp),
                    shape  = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue)
                ) {
                    Text(
                        "🔒  Desbloquear com Biometria",
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            AnimatedVisibility(visible = state.isUnlocked) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(280.dp)
                ) {
                    LinearProgressIndicator(
                        progress   = { state.countdownPct },
                        modifier   = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color      = SuccessGreen,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "${state.countdown}s",
                        fontSize = 13.sp,
                        color    = SubtleText
                    )
                }
            }
        }
    }
}
