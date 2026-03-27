package com.srbr.huginn.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.srbr.huginn.ui.theme.DarkSurface
import com.srbr.huginn.ui.theme.SubtleText

private val QR_BACKGROUND = DarkSurface
private val QR_FOREGROUND = Color.White

/**
 * Exibe um QR Code dinâmico a partir de um token assinado.
 *
 * O QR se atualiza automaticamente sempre que [token] muda
 * (a cada 10 segundos, controlado pelo CardViewModel).
 * A transição entre QRs usa fadeIn/fadeOut para feedback visual.
 *
 * Ficam invisíveis quando [visible] = false ou [token] está vazio.
 */
@Composable
fun QrCode(
    token:   String,
    visible: Boolean,
    size:    Dp       = 220.dp,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && token.isNotEmpty(),
        enter   = fadeIn(tween(300)),
        exit    = fadeOut(tween(200))
    ) {
        AnimatedContent(
            targetState  = token,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label        = "qrRefresh"
        ) { currentToken ->

            val bitmap = remember(currentToken) {
                generateQrBitmap(currentToken, pixelSize = 512)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = modifier
            ) {
                Box(
                    modifier = Modifier
                        .size(size)
                        .clip(RoundedCornerShape(16.dp))
                        .background(QR_BACKGROUND)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap             = bitmap.asImageBitmap(),
                        contentDescription = "QR Code de autenticação Heimdall",
                        modifier           = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text       = "HEIMDALL · QR",
                    fontSize   = 10.sp,
                    color      = SubtleText,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                    textAlign  = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Renderiza o token em um Bitmap quadrado usando ZXing.
 * Cores: módulos brancos no fundo escuro (DarkSurface = #16213E).
 */
private fun generateQrBitmap(content: String, pixelSize: Int): Bitmap {
    val hints = mapOf(
        EncodeHintType.MARGIN        to 1,
        EncodeHintType.CHARACTER_SET to "UTF-8"
    )
    val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, pixelSize, pixelSize, hints)

    val bgColor = AndroidColor.parseColor("#16213E")  // DarkSurface
    val fgColor = AndroidColor.WHITE

    val pixels = IntArray(pixelSize * pixelSize) { idx ->
        val x = idx % pixelSize
        val y = idx / pixelSize
        if (bitMatrix[x, y]) fgColor else bgColor
    }
    val bitmap = Bitmap.createBitmap(pixelSize, pixelSize, Bitmap.Config.RGB_565)
    bitmap.setPixels(pixels, 0, pixelSize, 0, 0, pixelSize, pixelSize)
    return bitmap
}
