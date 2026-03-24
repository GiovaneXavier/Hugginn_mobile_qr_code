package com.srbr.huginn.core.security

import android.util.Base64
import org.json.JSONObject
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Named

/**
 * Valida payloads QR do Odin (QR de cadastro).
 *
 * Verificações (em ordem):
 *  1. JSON parseável
 *  2. version == 1
 *  3. mode == "REG"
 *  4. expires_at > now
 *  5. assinatura HMAC-SHA256 válida
 *
 * Injetado via Hilt — hmacKey fornecido como @Named("qrHmacKey").
 */
class QRValidator @Inject constructor(
    @Named("qrHmacKey") private val hmacKey: String
) {

    sealed class Result {
        data class Success(val card: HuginnCard) : Result()
        data class Failure(val reason: String)   : Result()
    }

    fun validate(qrContent: String): Result {
        val json = runCatching { JSONObject(qrContent) }.getOrNull()
            ?: return Result.Failure("QR malformado. Tente novamente.")

        if (json.optInt("version", 0) != 1)
            return Result.Failure("Versão do QR incompatível. Atualize o app.")

        if (json.optString("mode") != "REG")
            return Result.Failure("QR inválido para cadastro.")

        val expiresAt = runCatching { json.getLong("expires_at") }.getOrNull()
            ?: return Result.Failure("QR sem data de validade.")

        val now = System.currentTimeMillis() / 1000
        if (now > expiresAt) {
            val mins = ((now - expiresAt) / 60).toInt().coerceAtLeast(1)
            return Result.Failure("QR expirado há $mins minuto(s). Solicite um novo.")
        }

        val receivedSig = json.optString("signature", "")
        if (receivedSig.isEmpty())
            return Result.Failure("QR sem assinatura. Contate a TI.")

        val canonical = buildCanonical(json)
        if (!verifyHmac(canonical, receivedSig))
            return Result.Failure("Assinatura inválida. QR pode ter sido adulterado.")

        return runCatching {
            val card = json.getJSONObject("card")
            val emp  = json.getJSONObject("employee")
            Result.Success(
                HuginnCard(
                    employeeId   = emp.getString("id"),
                    employeeName = emp.getString("name"),
                    employeeArea = emp.getString("area"),
                    employeeRole = emp.optString("role").ifEmpty { null },
                    systemId     = card.getString("system"),
                    systemName   = card.getString("system_name"),
                    cardColor    = card.getString("card_color"),
                    registeredAt = System.currentTimeMillis() / 1000,
                    nonce        = json.getString("nonce")
                )
            )
        }.getOrElse { Result.Failure("Campos obrigatórios ausentes no QR.") }
    }

    internal fun buildCanonical(json: JSONObject): String =
        "${json.optInt("version")}|" +
        "${json.optString("mode")}|" +
        "${json.optLong("issued_at")}|" +
        "${json.optLong("expires_at")}|" +
        "${json.optString("nonce")}|" +
        "${json.optJSONObject("employee")?.optString("id") ?: ""}|" +
        "${json.optJSONObject("employee")?.optString("name") ?: ""}|" +
        "${json.optJSONObject("card")?.optString("system") ?: ""}"

    internal fun verifyHmac(data: String, received: String): Boolean {
        val expected = computeHmac(data)
        if (expected.length != received.length) return false
        var diff = 0
        for (i in expected.indices) diff = diff or (expected[i].code xor received[i].code)
        return diff == 0
    }

    internal fun computeHmac(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacKey.toByteArray(), "HmacSHA256"))
        return Base64.encodeToString(
            mac.doFinal(data.toByteArray()),
            Base64.NO_WRAP or Base64.URL_SAFE
        )
    }
}
