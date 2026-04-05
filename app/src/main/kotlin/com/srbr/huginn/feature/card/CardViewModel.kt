package com.srbr.huginn.feature.card

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srbr.huginn.core.security.DeviceIdentity
import com.srbr.huginn.core.security.HuginnCard
import com.srbr.huginn.core.security.QrTokenGenerator
import com.srbr.huginn.core.storage.CardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CardUiState(
    val card:         HuginnCard? = null,
    val displayId:    String      = "",
    val isUnlocked:   Boolean     = false,
    val countdown:    Int         = 0,      // segundos restantes
    val countdownPct: Float       = 0f,     // 0..1 para barra de progresso
    val hasCard:      Boolean     = false,
    val hasLoaded:    Boolean     = false,  // true após loadCard() completar
    val qrToken:      String      = ""      // token atual exibido no QR Code
)

@HiltViewModel
class CardViewModel @Inject constructor(
    private val repository:       CardRepository,
    private val deviceIdentity:   DeviceIdentity,
    private val qrTokenGenerator: QrTokenGenerator,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(CardUiState())
    val state: StateFlow<CardUiState> = _state.asStateFlow()

    private var countdownJob: Job? = null
    private var qrRefreshJob: Job? = null

    private val AUTH_WINDOW_SECS = 30
    private val QR_REFRESH_SECS  = 10  // renova o QR a cada 10s

    init {
        // EncryptedSharedPreferences não deve bloquear a main thread —
        // carrega no IO e atualiza o state quando pronto.
        viewModelScope.launch { loadCard() }
    }

    private suspend fun loadCard() = withContext(Dispatchers.IO) {
        val systemId = savedStateHandle.get<String>("systemId")
        // Tenta pelo systemId da rota; se não encontrar (ex: diferença de encoding
        // entre startDestination e navigate()), usa o primeiro cartão disponível.
        val card = systemId?.let { repository.getCard(it) }
            ?: repository.getCards().firstOrNull()
        _state.update {
            it.copy(
                card      = card,
                displayId = deviceIdentity.getDisplayId(),
                hasCard   = card != null,
                hasLoaded = true
            )
        }
    }

    /**
     * Chamado pelo MainActivity após BiometricPrompt ser bem-sucedido.
     * Gera o primeiro QR e inicia os loops de countdown e refresh.
     */
    fun onBiometricSuccess() {
        viewModelScope.launch {
            // Garante que o cartão está carregado antes de gerar o token
            // (necessário em processo fresco onde loadCard() é assíncrono)
            if (_state.value.card == null) loadCard()
            val card = _state.value.card ?: return@launch
            val token = withContext(Dispatchers.Default) {
                qrTokenGenerator.generate(card, deviceIdentity.getDeviceId())
            }
            _state.update {
                it.copy(
                    isUnlocked   = true,
                    countdown    = AUTH_WINDOW_SECS,
                    countdownPct = 1f,
                    qrToken      = token
                )
            }
            startCountdown()
            startQrRefresh()
        }
    }

    /** Gera um novo token assinado e atualiza o estado (novo QR exibido). */
    private suspend fun refreshQrToken() {
        val card = _state.value.card ?: return
        val token = withContext(Dispatchers.Default) {
            qrTokenGenerator.generate(card, deviceIdentity.getDeviceId())
        }
        _state.update { it.copy(qrToken = token) }
    }

    /** Renova o QR a cada QR_REFRESH_SECS segundos enquanto desbloqueado. */
    private fun startQrRefresh() {
        qrRefreshJob?.cancel()
        qrRefreshJob = viewModelScope.launch {
            while (_state.value.isUnlocked) {
                delay(QR_REFRESH_SECS * 1000L)
                if (_state.value.isUnlocked) refreshQrToken()
            }
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (remaining in AUTH_WINDOW_SECS downTo 1) {
                _state.update {
                    it.copy(
                        countdown    = remaining,
                        countdownPct = remaining.toFloat() / AUTH_WINDOW_SECS
                    )
                }
                delay(1000)
            }
            qrRefreshJob?.cancel()
            _state.update { it.copy(isUnlocked = false, countdown = 0, countdownPct = 0f, qrToken = "") }
        }
    }

    fun onExpire() {
        countdownJob?.cancel()
        qrRefreshJob?.cancel()
        _state.update { it.copy(isUnlocked = false, countdown = 0, countdownPct = 0f, qrToken = "") }
    }

    fun onAppBackground() {
        if (_state.value.isUnlocked) onExpire()
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
        qrRefreshJob?.cancel()
    }
}
