package com.srbr.huginn.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.srbr.huginn.core.security.HuginnCard
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val PREFS_FILE  = "huginn_secure_store"
        const val KEY_CARD    = "active_card"
        const val KEY_NONCES  = "used_nonces"
        const val MAX_NONCES  = 50
    }

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveCard(card: HuginnCard) {
        prefs.edit().putString(KEY_CARD, card.toJson()).apply()
    }

    fun loadCard(): HuginnCard? =
        prefs.getString(KEY_CARD, null)?.let { HuginnCard.fromJson(it) }

    fun hasCard(): Boolean = prefs.contains(KEY_CARD) && loadCard() != null

    fun deleteCard() {
        prefs.edit().remove(KEY_CARD).remove(KEY_NONCES).apply()
    }

    fun isNonceUsed(nonce: String): Boolean =
        prefs.getString(KEY_NONCES, "")
            ?.split(",")
            ?.filter { it.isNotEmpty() }
            ?.contains(nonce) == true

    fun markNonceUsed(nonce: String) {
        val current = prefs.getString(KEY_NONCES, "") ?: ""
        val list    = current.split(",").filter { it.isNotEmpty() }.toMutableList()
        list.add(nonce)
        if (list.size > MAX_NONCES) list.removeAt(0)
        prefs.edit().putString(KEY_NONCES, list.joinToString(",")).apply()
    }
}
