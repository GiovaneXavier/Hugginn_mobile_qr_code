package com.srbr.huginn.core.storage

import com.srbr.huginn.core.security.HuginnCard
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Camada de repositório — fica entre os ViewModels e o CardStorage.
 * Essa abstração torna os ViewModels completamente testáveis via mock.
 */
@Singleton
class CardRepository @Inject constructor(
    private val storage: CardStorage
) {
    fun getCard(): HuginnCard?           = storage.loadCard()
    fun hasCard(): Boolean               = storage.hasCard()
    fun saveCard(card: HuginnCard)       = storage.saveCard(card)
    fun deleteCard()                     = storage.deleteCard()
    fun isNonceUsed(nonce: String)       = storage.isNonceUsed(nonce)
    fun markNonceUsed(nonce: String)     = storage.markNonceUsed(nonce)
}
