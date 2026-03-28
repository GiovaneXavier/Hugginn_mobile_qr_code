package com.srbr.huginn.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.srbr.huginn.core.security.HuginnCard
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val PREFS_FILE = "huginn_secure_store"
        const val KEY_CARDS  = "cards_v2"    // JSON object: systemId → cardJson
        const val KEY_CARD   = "active_card" // legacy single-card key (migration)
        const val KEY_NONCES = "used_nonces"
        const val MAX_NONCES = 50
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

    // ── Multi-card read ───────────────────────────────────────────────────────

    fun loadCards(): List<HuginnCard> {
        migrateLegacyCardIfNeeded()
        return loadCardMap().values.mapNotNull { HuginnCard.fromJson(it) }
    }

    fun loadCard(systemId: String): HuginnCard? =
        loadCardMap()[systemId]?.let { HuginnCard.fromJson(it) }

    fun hasCards(): Boolean = loadCards().isNotEmpty()

    // ── Write / delete ────────────────────────────────────────────────────────

    fun saveCard(card: HuginnCard) {
        val map = loadCardMap().toMutableMap()
        map[card.systemId] = card.toJson()
        saveCardMap(map)
    }

    fun deleteCard(systemId: String) {
        val map = loadCardMap().toMutableMap()
        map.remove(systemId)
        saveCardMap(map)
    }

    fun deleteAllCards() {
        prefs.edit().remove(KEY_CARDS).remove(KEY_CARD).remove(KEY_NONCES).apply()
    }

    // ── Nonce tracking (global, not per-card) ────────────────────────────────

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

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun loadCardMap(): Map<String, String> {
        val json = prefs.getString(KEY_CARDS, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { obj.getString(it) }
        }.getOrElse { emptyMap() }
    }

    private fun saveCardMap(map: Map<String, String>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_CARDS, obj.toString()).apply()
    }

    /** One-time migration: moves the legacy single-card key into the new map. */
    private fun migrateLegacyCardIfNeeded() {
        val legacyJson = prefs.getString(KEY_CARD, null) ?: return
        HuginnCard.fromJson(legacyJson)?.let { card ->
            val map = loadCardMap().toMutableMap()
            if (!map.containsKey(card.systemId)) {
                map[card.systemId] = legacyJson
                saveCardMap(map)
            }
        }
        prefs.edit().remove(KEY_CARD).apply()
    }
}
