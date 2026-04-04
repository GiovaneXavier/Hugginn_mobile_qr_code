package com.srbr.huginn.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.srbr.huginn.core.security.DeviceIdentity
import com.srbr.huginn.core.security.HuginnCard
import com.srbr.huginn.core.security.QRValidator
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
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var qrValidator:    QRValidator
    private lateinit var repository:     CardRepository
    private lateinit var deviceIdentity: DeviceIdentity
    private lateinit var viewModel:      OnboardingViewModel

    private val fakeCard = HuginnCard(
        employeeId = "SRBR-0042", employeeName = "Ana Lima",
        employeeArea = "Pesquisa", employeeRole = null,
        systemId = "SRBR_EXIT", systemName = "Saída SRBR",
        cardColor = "#1428A0", registeredAt = 0L, nonce = "nonce-abc"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        qrValidator    = mockk()
        repository     = mockk(relaxed = true)
        deviceIdentity = mockk(relaxed = true)
        every { deviceIdentity.getDisplayId() } returns "SRBR-ABCD-1234"
        viewModel = OnboardingViewModel(qrValidator, repository, deviceIdentity, SavedStateHandle())
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `initial step is Welcome`() = runTest {
        viewModel.state.test {
            assertTrue(awaitItem().step is OnboardingStep.Welcome)
        }
    }

    @Test fun `onStartScan transitions to Scanning`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.onStartScan()
            assertTrue(awaitItem().step is OnboardingStep.Scanning)
        }
    }

    @Test fun `onCancelScan returns to Welcome`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.onStartScan()
            awaitItem()
            viewModel.onCancelScan()
            assertTrue(awaitItem().step is OnboardingStep.Welcome)
        }
    }

    @Test fun `valid QR transitions to Success with device displayId`() = runTest {
        every { qrValidator.validate(any()) } returns QRValidator.Result.Success(fakeCard)
        every { repository.isNonceUsed(any()) } returns false

        viewModel.state.test {
            awaitItem()
            viewModel.onQRDetected("valid-qr-content")
            awaitItem() // Validating

            testDispatcher.scheduler.advanceUntilIdle()

            val final = expectMostRecentItem()
            assertTrue(final.step is OnboardingStep.Success)
            val success = final.step as OnboardingStep.Success
            assertEquals(fakeCard, success.card)
            assertEquals("SRBR-ABCD-1234", success.displayId)
            verify { repository.saveCard(fakeCard) }
            verify { repository.markNonceUsed("nonce-abc") }
        }
    }

    @Test fun `invalid QR transitions to Error`() = runTest {
        every { qrValidator.validate(any()) } returns
            QRValidator.Result.Failure("QR expirado há 5 minuto(s)")

        viewModel.state.test {
            awaitItem()
            viewModel.onQRDetected("expired-qr")
            awaitItem()
            testDispatcher.scheduler.advanceUntilIdle()
            val final = expectMostRecentItem()
            assertTrue(final.step is OnboardingStep.Error)
            assertEquals("QR Inválido", (final.step as OnboardingStep.Error).title)
        }
    }

    @Test fun `reused nonce transitions to Error`() = runTest {
        every { qrValidator.validate(any()) } returns QRValidator.Result.Success(fakeCard)
        every { repository.isNonceUsed("nonce-abc") } returns true

        viewModel.state.test {
            awaitItem()
            viewModel.onQRDetected("valid-but-reused")
            awaitItem()
            testDispatcher.scheduler.advanceUntilIdle()
            val final = expectMostRecentItem()
            assertTrue(final.step is OnboardingStep.Error)
            assertTrue((final.step as OnboardingStep.Error).title.contains("utilizado"))
        }
    }

    @Test fun `onCameraPermissionDenied transitions to Error`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.onCameraPermissionDenied()
            val state = awaitItem()
            assertTrue(state.step is OnboardingStep.Error)
            assertEquals("Câmera negada", (state.step as OnboardingStep.Error).title)
        }
    }

    @Test fun `onCameraUnavailable transitions to Error`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.onCameraUnavailable()
            val state = awaitItem()
            assertTrue(state.step is OnboardingStep.Error)
            assertEquals("Câmera indisponível", (state.step as OnboardingStep.Error).title)
        }
    }

    @Test fun `onTryAgain resets to Welcome`() = runTest {
        every { qrValidator.validate(any()) } returns QRValidator.Result.Failure("erro")
        viewModel.state.test {
            awaitItem()
            viewModel.onQRDetected("bad")
            awaitItem()
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // Error
            viewModel.onTryAgain()
            assertTrue(awaitItem().step is OnboardingStep.Welcome)
        }
    }
}
