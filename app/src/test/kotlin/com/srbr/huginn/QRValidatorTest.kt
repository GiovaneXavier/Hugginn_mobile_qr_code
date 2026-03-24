package com.srbr.huginn

import com.srbr.huginn.core.security.QRValidator
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class QRValidatorTest {

    private lateinit var validator: QRValidator
    private val hmacKey = "TEST_SECRET_KEY"

    @Before fun setup() { validator = QRValidator(hmacKey) }

    private fun now() = System.currentTimeMillis() / 1000

    private fun buildPayload(
        version: Int = 1, mode: String = "REG",
        expiresAt: Long = now() + 300, nonce: String = "test-nonce-123",
        empId: String = "SRBR-0042", empName: String = "Ana Lima",
        sign: Boolean = true
    ): String {
        val json = JSONObject().apply {
            put("version", version); put("mode", mode)
            put("issued_at", now()); put("expires_at", expiresAt); put("nonce", nonce)
            put("employee", JSONObject().apply { put("id", empId); put("name", empName); put("area", "Pesquisa"); put("role", "") })
            put("card", JSONObject().apply { put("system", "SRBR_EXIT"); put("system_name", "Saída SRBR"); put("card_color", "#1428A0") })
        }
        if (sign) json.put("signature", validator.computeHmac(validator.buildCanonical(json)))
        return json.toString()
    }

    @Test fun `valid payload returns Success`() {
        val r = validator.validate(buildPayload())
        assertTrue(r is QRValidator.Result.Success)
        val c = (r as QRValidator.Result.Success).card
        assertEquals("SRBR-0042", c.employeeId)
        assertEquals("Ana Lima",  c.employeeName)
        assertEquals("SRBR_EXIT", c.systemId)
    }

    @Test fun `malformed JSON returns Failure`() {
        val r = validator.validate("{{{not json")
        assertTrue(r is QRValidator.Result.Failure)
    }

    @Test fun `wrong version returns Failure`() {
        val r = validator.validate(buildPayload(version = 2))
        assertTrue(r is QRValidator.Result.Failure)
        assertTrue((r as QRValidator.Result.Failure).reason.contains("incompatível"))
    }

    @Test fun `wrong mode returns Failure`() {
        val r = validator.validate(buildPayload(mode = "AUTH"))
        assertTrue(r is QRValidator.Result.Failure)
    }

    @Test fun `expired QR returns Failure`() {
        val r = validator.validate(buildPayload(expiresAt = now() - 600))
        assertTrue(r is QRValidator.Result.Failure)
        assertTrue((r as QRValidator.Result.Failure).reason.contains("expirado"))
    }

    @Test fun `missing signature returns Failure`() {
        val r = validator.validate(buildPayload(sign = false))
        assertTrue(r is QRValidator.Result.Failure)
    }

    @Test fun `tampered payload returns Failure`() {
        val json = JSONObject(buildPayload())
        json.getJSONObject("employee").put("name", "Hacker")
        val r = validator.validate(json.toString())
        assertTrue(r is QRValidator.Result.Failure)
    }

    @Test fun `wrong HMAC key returns Failure`() {
        val r = QRValidator("WRONG_KEY").validate(buildPayload())
        assertTrue(r is QRValidator.Result.Failure)
    }

    @Test fun `verifyHmac matches correctly`() {
        val data = "test|data|123"
        assertTrue(validator.verifyHmac(data, validator.computeHmac(data)))
    }

    @Test fun `verifyHmac rejects mismatch`() {
        assertFalse(validator.verifyHmac("data1", validator.computeHmac("data2")))
    }
}
