package com.srbr.huginn.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srbr.huginn.core.security.HuginnCard
import com.srbr.huginn.ui.theme.*

/**
 * Cartão animado que faz flip 3D entre os estados bloqueado e desbloqueado.
 * Pure Compose — sem Canvas, sem XML.
 */
@Composable
fun HuginnCard(
    card:       HuginnCard?,
    displayId:  String,
    isUnlocked: Boolean,
    modifier:   Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue   = if (isUnlocked) 180f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label         = "cardFlip"
    )

    Box(
        modifier = modifier
            .width(320.dp)
            .height(200.dp)
            .graphicsLayer {
                rotationY      = rotation
                cameraDistance = 12f * density
            }
    ) {
        if (rotation <= 90f) {
            LockedCardFace(displayId = displayId)
        } else {
            UnlockedCardFace(
                card      = card,
                displayId = displayId,
                modifier  = Modifier.graphicsLayer { rotationY = 180f }
            )
        }
    }
}

@Composable
private fun LockedCardFace(displayId: String) {
    CardSurface(
        gradient = Brush.linearGradient(listOf(Color(0xFF1A1A2E), Color(0xFF0F3460)))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            CardHeader(systemName = "SAMSUNG RESEARCH BRASIL")
        }
        Column(
            modifier              = Modifier.fillMaxSize(),
            verticalArrangement   = Arrangement.Center,
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {
            Text("🔒", fontSize = 40.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Autentique para desbloquear",
                fontSize   = 12.sp,
                color      = SubtleText,
                fontWeight = FontWeight.Medium
            )
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
            Text(
                text       = "SRBR-••••-••••",
                modifier   = Modifier.padding(20.dp),
                fontFamily = FontFamily.Monospace,
                fontSize   = 14.sp,
                color      = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun UnlockedCardFace(card: HuginnCard?, displayId: String, modifier: Modifier = Modifier) {
    val color = runCatching {
        Color(android.graphics.Color.parseColor(card?.cardColor ?: "#1428A0"))
    }.getOrElse { SamsungBlue }

    CardSurface(
        gradient = Brush.linearGradient(listOf(color, SamsungBlueLight)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            CardHeader(systemName = card?.systemName ?: "SRBR")
            Spacer(modifier = Modifier.height(16.dp))
            // Chip EMV decorativo
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.linearGradient(listOf(Color(0xFFD4AF37), Color(0xFFB8960C)))
                    )
            )
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            Text(
                text       = card?.employeeName ?: "",
                modifier   = Modifier.padding(horizontal = 20.dp),
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White
            )
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text       = displayId,
                    fontFamily = FontFamily.Monospace,
                    fontSize   = 12.sp,
                    color      = Color.White.copy(alpha = 0.7f)
                )
                ActiveBadge()
            }
        }
    }
}

@Composable
private fun CardHeader(systemName: String) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Column {
            Text("SRBR", fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("Huginn", fontSize = 22.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Text("📱", fontSize = 22.sp)
    }
}

@Composable
private fun ActiveBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(50))
                .background(SuccessGreen)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("ATIVO", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp)
    }
}

@Composable
private fun CardSurface(
    gradient: Brush,
    modifier: Modifier = Modifier,
    content:  @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(gradient),
        content = content
    )
}
