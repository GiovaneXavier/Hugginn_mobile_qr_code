package com.srbr.huginn.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srbr.huginn.core.security.DeviceIdentity
import com.srbr.huginn.core.security.HuginnCard
import com.srbr.huginn.core.security.QRValidator
import com.srbr.huginn.core.storage.CardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class OnboardingStep {
    object Welcome    : OnboardingStep()
    object Scanning   : OnboardingStep()
    object Validating : OnboardingStep()
    data class Error(val title: String, val message: String) : OnboardingStep()
    data class Success(val card: HuginnCard, val displayId: String) : OnboardingStep()
}

data class OnboardingUiState(
    val step:             OnboardingStep = OnboardingStep.Welcome,
    val validatingStatus: String         = "Verificando assinatura...",
    val validatingSub:    String         = "Validando autenticidade do QR"
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val qrValidator:    QRValidator,
    private val repository:     CardRepository,
    private val deviceIdentity: DeviceIdentity
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun onStartScan() {
        _state.update { it.copy(step = OnboardingStep.Scanning) }
    }

    fun onCancelScan() {
        _state.update { it.copy(step = OnboardingStep.Welcome) }
    }

    fun onQRDetected(content: String) {
        _state.update { it.copy(step = OnboardingStep.Validating) }

        viewModelScope.launch {
            delay(600) // feedback visual breve

            val result = withContext(Dispatchers.Default) {
                qrValidator.validate(content)
            }

            when (result) {
                is QRValidator.Result.Failure -> {
                    _state.update {
                        it.copy(step = OnboardingStep.Error(
                            title   = "QR Inválido",
                            message = result.reason
                        ))
                    }
                }
                is QRValidator.Result.Success -> {
                    val card = result.card

                    if (repository.isNonceUsed(card.nonce)) {
                        _state.update {
                            it.copy(step = OnboardingStep.Error(
                                title   = "QR já utilizado",
                                message = "Este QR já foi usado. Solicite um novo ao administrador."
                            ))
                        }
                        return@launch
                    }

                    _state.update {
                        it.copy(
                            validatingStatus = "Gerando credenciais...",
                            validatingSub    = "Criando chaves de segurança"
                        )
                    }
                    delay(800)

                    repository.markNonceUsed(card.nonce)
                    repository.saveCard(card)

                    _state.update { it.copy(step = OnboardingStep.Success(card, deviceIdentity.getDisplayId())) }
                }
            }
        }
    }

    fun onCameraUnavailable() {
        _state.update {
            it.copy(step = OnboardingStep.Error(
                title   = "Câmera indisponível",
                message = "Não foi possível acessar a câmera. Verifique se outro app está usando-a e tente novamente."
            ))
        }
    }

    fun onCameraPermissionDenied() {
        _state.update {
            it.copy(step = OnboardingStep.Error(
                title   = "Câmera negada",
                message = "Permissão de câmera necessária para escanear o QR de cadastro. Habilite nas configurações do sistema."
            ))
        }
    }

    fun onTryAgain() {
        _state.update { it.copy(step = OnboardingStep.Welcome) }
    }
}
