package com.srbr.huginn.feature.card

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.srbr.huginn.core.security.QrTokenGenerator
import com.srbr.huginn.credential.card.BaseCardViewModel
import com.srbr.huginn.credential.security.DeviceIdentity
import com.srbr.huginn.credential.security.HuginnCard
import com.srbr.huginn.credential.storage.CardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Variante QR: o desbloqueio gera um token assinado via [QrTokenGenerator], exibido como
 * QR Code e renovado a cada 10s enquanto a janela de 30s estiver ativa.
 * Toda a máquina de estado (loadCard, countdown, auto-lock, background) vem de
 * [BaseCardViewModel]; aqui só ficam os ganchos específicos do canal QR.
 */
@HiltViewModel
class CardViewModel(
    repository:                 CardRepository,
    deviceIdentity:             DeviceIdentity,
    private val qrTokenGenerator: QrTokenGenerator,
    savedStateHandle:           SavedStateHandle,
    private val computeDispatcher: CoroutineDispatcher
) : BaseCardViewModel(repository, deviceIdentity, savedStateHandle) {

    // Construtor usado pelo Hilt; injeta o dispatcher real de produção.
    // O construtor primário recebe um dispatcher controlável para testes determinísticos.
    @Inject constructor(
        repository:       CardRepository,
        deviceIdentity:   DeviceIdentity,
        qrTokenGenerator: QrTokenGenerator,
        savedStateHandle: SavedStateHandle
    ) : this(repository, deviceIdentity, qrTokenGenerator, savedStateHandle, Dispatchers.Default)

    private var qrRefreshJob: Job? = null
    private val qrRefreshSecs = 10  // renova o QR a cada 10s

    init {
        // EncryptedSharedPreferences não deve bloquear a main thread.
        viewModelScope.launch(Dispatchers.IO) { loadCard() }
    }

    /** Gera o primeiro token, desbloqueia o card e inicia o loop de renovação. */
    override suspend fun onUnlocked(card: HuginnCard) {
        val token = withContext(computeDispatcher) {
            qrTokenGenerator.generate(card, deviceIdentity.getDeviceId())
        }
        setUnlocked { copy(qrToken = token) }
        startQrRefresh()
    }

    /** Cancela o loop de renovação ao bloquear/expirar. */
    override fun onLockedCleanup() {
        qrRefreshJob?.cancel()
    }

    /** Renova o QR a cada [qrRefreshSecs] segundos enquanto desbloqueado. */
    private fun startQrRefresh() {
        qrRefreshJob?.cancel()
        qrRefreshJob = viewModelScope.launch {
            while (state.value.isUnlocked) {
                delay(qrRefreshSecs * 1000L)
                if (state.value.isUnlocked) refreshQrToken()
            }
        }
    }

    /** Gera um novo token assinado e atualiza o estado (novo QR exibido). */
    private suspend fun refreshQrToken() {
        val card = state.value.card ?: return
        val token = withContext(computeDispatcher) {
            qrTokenGenerator.generate(card, deviceIdentity.getDeviceId())
        }
        _state.update { it.copy(qrToken = token) }
    }
}
