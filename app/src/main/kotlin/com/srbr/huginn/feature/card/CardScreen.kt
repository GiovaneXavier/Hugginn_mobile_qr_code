package com.srbr.huginn.feature.card

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srbr.huginn.credential.card.CardScreenScaffold
import com.srbr.huginn.credential.ui.CardUnlockMotion
import com.srbr.huginn.ui.components.QrCode

@Composable
fun CardScreen(
    onRequestBiometric: (onSuccess: () -> Unit) -> Unit,
    onBack:             () -> Unit,
    viewModel:          CardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CardScreenScaffold(
        state                  = state,
        cardMotion             = CardUnlockMotion.RISE_UP,
        statusUnlockedTitle    = "Aponte o QR para o leitor Heimdall",
        statusUnlockedSubtitle = "QR ativo · Biometria confirmada · Renova a cada 10s",
        onRequestBiometric     = onRequestBiometric,
        onBiometricSuccess     = viewModel::onBiometricSuccess,
        onExpire               = viewModel::onExpire,
        onAppBackground        = viewModel::onAppBackground,
        onBack                 = onBack,
        // O QR ocupa o espaço entre card e status; o espaçamento condicional fica no slot.
        cardToStatusSpacing    = 0.dp,
        belowCard = {
            Spacer(modifier = Modifier.height(32.dp))
            QrCode(
                token   = state.qrToken,
                visible = state.isUnlocked
            )
            // Espaçamento condicional: maior quando bloqueado (sem QR).
            Spacer(modifier = Modifier.height(if (state.isUnlocked) 20.dp else 40.dp))
        }
    )
}
