package com.srbr.huginn

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.srbr.huginn.core.storage.CardRepository
import com.srbr.huginn.ui.theme.DarkBackground
import com.srbr.huginn.ui.theme.HuginnTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var repository: CardRepository

    private var startDestination: String? by mutableStateOf(null)

    private lateinit var cameraExecutor: ExecutorService
    private var onQRResult: ((String) -> Unit)? = null
    private var onCameraPermissionDenied: (() -> Unit)? = null
    private var onCameraUnavailable: (() -> Unit)? = null
    private var isAnalyzing = false
    private var activeCameraProvider: ProcessCameraProvider? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startQRCamera()
        else onCameraPermissionDenied?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition { startDestination == null }

        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        cameraExecutor = Executors.newSingleThreadExecutor()

        lifecycleScope.launch {
            startDestination = withContext(Dispatchers.IO) {
                val cards = repository.getCards()
                when (cards.size) {
                    0    -> Routes.ONBOARDING
                    1    -> Routes.card(cards.first().systemId)
                    else -> Routes.CARD_LIST
                }
            }
        }

        setContent {
            HuginnTheme {
                Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
                    val dest = startDestination
                    if (dest != null) {
                        HuginnNavGraph(
                            startDestination   = dest,
                            onRequestBiometric = ::requestBiometric,
                            onRequestCamera    = { onQR, onDenied, onUnavailable ->
                                onQRResult = onQR
                                onCameraPermissionDenied = onDenied
                                onCameraUnavailable = onUnavailable
                                requestCamera()
                            },
                            onStopCamera       = ::stopQRCamera
                        )
                    }
                }
            }
        }
    }

    private fun requestBiometric(onSuccess: () -> Unit) {
        var failCount = 0
        val executor = ContextCompat.getMainExecutor(this)
        lateinit var prompt: BiometricPrompt
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
            override fun onAuthenticationFailed() {
                failCount++
                if (failCount >= 3) prompt.cancelAuthentication()
            }
        }
        prompt = BiometricPrompt(this, executor, callback)
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Huginn")
                .setSubtitle("Confirme sua identidade para exibir o QR Code")
                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                .build()
        )
    }

    private fun requestCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startQRCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun stopQRCamera() {
        isAnalyzing = false
        activeCameraProvider?.unbindAll()
        activeCameraProvider = null
    }

    @OptIn(ExperimentalGetImage::class)
    private fun startQRCamera() {
        isAnalyzing = true
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            activeCameraProvider = provider
            val scanner  = BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
            )
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { ia ->
                    ia.setAnalyzer(cameraExecutor) { proxy ->
                        val media = proxy.image
                        if (media != null && isAnalyzing) {
                            val image = InputImage.fromMediaImage(
                                media, proxy.imageInfo.rotationDegrees)
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    barcodes.firstOrNull()?.rawValue?.let { qr ->
                                        if (isAnalyzing) {
                                            isAnalyzing = false
                                            provider.unbindAll()
                                            runOnUiThread { onQRResult?.invoke(qr) }
                                        }
                                    }
                                }
                                .addOnCompleteListener { proxy.close() }
                        } else { proxy.close() }
                    }
                }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, analysis)
            } catch (e: Exception) {
                Log.e("MainActivity", "Camera bind failed: ${e.message}")
                activeCameraProvider = null
                runOnUiThread { onCameraUnavailable?.invoke() }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
