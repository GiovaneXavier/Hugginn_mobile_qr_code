package com.srbr.huginn.core.storage

import androidx.test.core.app.ApplicationProvider
import com.srbr.huginn.core.security.HuginnCard
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CardStorageTest {

    private lateinit var storage: CardStorage

    private val fakeCard = HuginnCard(
        employeeId   = "EMP001",
        employeeName = "Test User",
        employeeArea = "Engineering",
        employeeRole = null,
        systemId     = "SYS001",
        systemName   = "Sistema Teste",
        cardColor    = "#1428A0",
        registeredAt = 1700000000L,
        nonce        = "onboarding-nonce"
    )

    @Before
    fun setup() {
        storage = CardStorage(ApplicationProvider.getApplicationContext())
        storage.deleteAllCards()
    }

    @Test fun `hasCards returns false when no card saved`() {
        assertFalse(storage.hasCards())
    }

    @Test fun `saveCard and loadCard roundtrip`() {
        storage.saveCard(fakeCard)
        val loaded = storage.loadCard(fakeCard.systemId)
        assertNotNull(loaded)
        assertEquals(fakeCard.employeeId,   loaded!!.employeeId)
        assertEquals(fakeCard.employeeName, loaded.employeeName)
        assertEquals(fakeCard.systemId,     loaded.systemId)
    }

    @Test fun `hasCards returns true after saveCard`() {
        storage.saveCard(fakeCard)
        assertTrue(storage.hasCards())
    }

    @Test fun `deleteAllCards removes all cards and nonces`() {
        storage.saveCard(fakeCard)
        storage.markNonceUsed("nonce-1")
        storage.deleteAllCards()
        assertFalse(storage.hasCards())
        assertFalse(storage.isNonceUsed("nonce-1"))
    }

    @Test fun `deleteCard removes only the targeted card`() {
        storage.saveCard(fakeCard)
        storage.deleteCard(fakeCard.systemId)
        assertFalse(storage.hasCards())
        assertNull(storage.loadCard(fakeCard.systemId))
    }

    @Test fun `loadCards returns all saved cards`() {
        val card2 = fakeCard.copy(systemId = "SYS002", systemName = "Sistema 2")
        storage.saveCard(fakeCard)
        storage.saveCard(card2)
        val cards = storage.loadCards()
        assertEquals(2, cards.size)
        assertTrue(cards.any { it.systemId == "SYS001" })
        assertTrue(cards.any { it.systemId == "SYS002" })
    }

    @Test fun `isNonceUsed returns false for unknown nonce`() {
        assertFalse(storage.isNonceUsed("never-used"))
    }

    @Test fun `markNonceUsed marks nonce as used`() {
        storage.markNonceUsed("nonce-abc")
        assertTrue(storage.isNonceUsed("nonce-abc"))
    }

    @Test fun `different nonces are tracked independently`() {
        storage.markNonceUsed("nonce-1")
        assertTrue(storage.isNonceUsed("nonce-1"))
        assertFalse(storage.isNonceUsed("nonce-2"))
    }

    @Test fun `nonce FIFO eviction at MAX_NONCES (50)`() {
        for (i in 0 until 50) storage.markNonceUsed("nonce-$i")
        assertTrue(storage.isNonceUsed("nonce-0"))
        assertTrue(storage.isNonceUsed("nonce-49"))

        storage.markNonceUsed("nonce-50")
        assertFalse("nonce-0 should be evicted after 51st nonce", storage.isNonceUsed("nonce-0"))
        assertTrue(storage.isNonceUsed("nonce-50"))
        assertTrue(storage.isNonceUsed("nonce-49"))
    }

    @Test fun `marking same nonce twice does not crash`() {
        storage.markNonceUsed("duplicate")
        storage.markNonceUsed("duplicate")
        assertTrue(storage.isNonceUsed("duplicate"))
    }
}
