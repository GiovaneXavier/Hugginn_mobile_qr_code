package com.srbr.huginn.core.security

import android.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Gera tokens assinados com HMAC-SHA256 para exibição via QR Code.
 *
 * O formato do token é idêntico ao utilizado pelo HCE no app NFC:
 *   deviceId|employeeId|systemId|timestamp|nonce.assinatura
 *
 * Isso garante que o backend Heimdall valide tokens NFC e QR
 * com exatamente a mesma lógica, sem alteração no servidor.
 *
 * Injetado via Hilt — tokenHmacKey fornecido pelo AppModule.
 */
@Singleton
class QrTokenGenerator @Inject constructor(
    @Named("tokenHmacKey") private val hmacKey: String
) {

    /**
     * Gera um novo token assinado com timestamp e nonce atual.
     * Cada chamada produz um token diferente (novo timestamp + nonce).
     *
     * @param card     cartão do funcionário registrado
     * @param deviceId ID do dispositivo (SHA-256 do Android ID)
     * @return token no formato "deviceId|empId|sysId|ts|nonce.sig"
     */
    fun generate(card: HuginnCard, deviceId: String): String {
        val ts    = System.currentTimeMillis() / 1000L
        val nonce = Random.nextLong(100_000L, 999_999L)
        val data  = "$deviceId|${card.employeeId}|${card.systemId}|$ts|$nonce"

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacKey.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val sig = Base64.encodeToString(
            mac.doFinal(data.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP or Base64.URL_SAFE
        )
        return "$data.$sig"
    }
}
