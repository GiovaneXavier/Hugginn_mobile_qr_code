package com.srbr.huginn.core.storage

import com.srbr.huginn.core.security.HuginnCard
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository layer — sits between ViewModels and CardStorage.
 * Supports multiple credentials (one per systemId).
 * This abstraction makes ViewModels fully unit-testable.
 */
@Singleton
class CardRepository @Inject constructor(
    private val storage: CardStorage
) {
    fun getCards(): List<HuginnCard>       = storage.loadCards()
    fun getCard(systemId: String): HuginnCard? = storage.loadCard(systemId)
    fun hasCard(): Boolean                 = storage.hasCards()
    fun saveCard(card: HuginnCard)         = storage.saveCard(card)
    fun deleteCard(systemId: String)       = storage.deleteCard(systemId)
    fun deleteAllCards()                   = storage.deleteAllCards()
    fun isNonceUsed(nonce: String)         = storage.isNonceUsed(nonce)
    fun markNonceUsed(nonce: String)       = storage.markNonceUsed(nonce)
}
