package com.srbr.huginn.feature.card

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.srbr.huginn.core.security.DeviceIdentity
import com.srbr.huginn.core.security.HuginnCard
import com.srbr.huginn.core.security.QrTokenGenerator
import com.srbr.huginn.core.storage.CardRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CardViewModelTest {

    private val testDispatcher   = StandardTestDispatcher()
    private val savedStateHandle = SavedStateHandle(mapOf("systemId" to "SRBR_EXIT"))
    private lateinit var repository:       CardRepository
    private lateinit var deviceIdentity:   DeviceIdentity
    private lateinit var qrTokenGenerator: QrTokenGenerator
    private lateinit var viewModel:        CardViewModel

    private val fakeCard = HuginnCard(
        employeeId   = "SRBR-0042",
        employeeName = "Ana Lima",
        employeeArea = "Pesquisa",
        employeeRole = null,
        systemId     = "SRBR_EXIT",
        systemName   = "Saída SRBR",
        cardColor    = "#1428A0",
        registeredAt = 1710950400,
        nonce        = "test-nonce"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository       = mockk(relaxed = true)
        deviceIdentity   = mockk(relaxed = true)
        qrTokenGenerator = mockk()

        every { repository.getCard(any()) }     returns fakeCard
        every { repository.hasCard() }          returns true
        every { deviceIdentity.getDeviceId() }  returns "ABCD1234EFGH5678"
        every { deviceIdentity.getDisplayId() } returns "SRBR-ABCD-1234"
        every { qrTokenGenerator.generate(any(), any()) } returns "fake-token-abc123"

        viewModel = CardViewModel(repository, deviceIdentity, qrTokenGenerator, savedStateHandle)
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `initial state loads card from repository`() = runTest {
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(fakeCard,         state.card)
            assertEquals("SRBR-ABCD-1234", state.displayId)
            assertTrue(state.hasCard)
            assertFalse(state.isUnlocked)
            assertTrue(state.qrToken.isEmpty())
        }
    }

    @Test
    fun `onBiometricSuccess sets isUnlocked true and generates QR token`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.onBiometricSuccess()
            val unlocked = awaitItem()
            assertTrue(unlocked.isUnlocked)
            assertEquals(30, unlocked.countdown)
            assertEquals(1f, unlocked.countdownPct)
            assertEquals("fake-token-abc123", unlocked.qrToken)
        }
    }

    @Test
    fun `onBiometricSuccess calls QrTokenGenerator`() = runTest {
        viewModel.onBiometricSuccess()
        testDispatcher.scheduler.advanceUntilIdle()
        verify { qrTokenGenerator.generate(fakeCard, "ABCD1234EFGH5678") }
    }

    @Test
    fun `onExpire clears QR token and locks card`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.onBiometricSuccess()
            awaitItem()
            viewModel.onExpire()
            val locked = awaitItem()
            assertFalse(locked.isUnlocked)
            assertTrue(locked.qrToken.isEmpty())
        }
    }

    @Test
    fun `onAppBackground locks if unlocked`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.onBiometricSuccess()
            awaitItem()
            viewModel.onAppBackground()
            val state = awaitItem()
            assertFalse(state.isUnlocked)
            assertTrue(state.qrToken.isEmpty())
        }
    }

    @Test
    fun `countdown decrements over time`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.onBiometricSuccess()
            val start = awaitItem()
            assertEquals(30, start.countdown)

            testDispatcher.scheduler.advanceTimeBy(3000)
            val after3s = expectMostRecentItem()
            assertTrue(after3s.countdown <= 27)
        }
    }

    @Test
    fun `countdown expires and locks automatically`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.onBiometricSuccess()
            awaitItem()

            testDispatcher.scheduler.advanceTimeBy(31_000)
            val expired = expectMostRecentItem()
            assertFalse(expired.isUnlocked)
            assertTrue(expired.qrToken.isEmpty())
        }
    }

    @Test
    fun `QR token refreshes every 10 seconds`() = runTest {
        var callCount = 0
        every { qrTokenGenerator.generate(any(), any()) } answers {
            "token-${++callCount}"
        }

        viewModel.state.test {
            awaitItem()
            viewModel.onBiometricSuccess()
            val first = awaitItem()
            assertEquals("token-1", first.qrToken)

            testDispatcher.scheduler.advanceTimeBy(10_000)
            val refreshed = expectMostRecentItem()
            assertEquals("token-2", refreshed.qrToken)
        }
    }
}
