package com.srbr.huginn.feature.card_list

import androidx.lifecycle.ViewModel
import com.srbr.huginn.credential.security.HuginnCard
import com.srbr.huginn.credential.storage.CardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class CardListViewModel @Inject constructor(
    private val repository: CardRepository
) : ViewModel() {
    private val _cards = MutableStateFlow(repository.getCards())
    val cards: StateFlow<List<HuginnCard>> = _cards.asStateFlow()

    fun refresh() { _cards.value = repository.getCards() }
}
