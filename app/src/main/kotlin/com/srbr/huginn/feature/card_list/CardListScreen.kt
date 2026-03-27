package com.srbr.huginn.feature.card_list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srbr.huginn.ui.theme.*

@Composable
fun CardListScreen(
    onSelectCard: (systemId: String) -> Unit,
    onAddCard:    () -> Unit,
    viewModel:    CardListViewModel = hiltViewModel()
) {
    val cards by viewModel.cards.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text          = "SRBR",
            fontSize      = 12.sp,
            color         = SamsungBlue,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text       = "Huginn",
            fontSize   = 36.sp,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text     = "Selecione uma credencial",
            fontSize = 14.sp,
            color    = SubtleText
        )

        Spacer(modifier = Modifier.height(28.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(cards, key = { it.systemId }) { card ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkSurface)
                        .clickable { onSelectCard(card.systemId) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = card.employeeName,
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text     = card.systemName,
                            fontSize = 13.sp,
                            color    = MutedText
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text       = card.systemId,
                            fontSize   = 11.sp,
                            color      = SubtleText,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick  = onAddCard,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape  = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue)
        ) {
            Text(
                text       = "📷  Adicionar Credencial",
                fontSize   = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
