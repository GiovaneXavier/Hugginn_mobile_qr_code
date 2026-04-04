package com.srbr.huginn.core.security

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
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

    // ── Q41: Formato de saída do computeHmac (NO_PADDING, URL_SAFE) ──────────

    @Test fun `computeHmac output has no Base64 padding`() {
        val sig = validator.computeHmac("any canonical string")
        assertFalse("Signature must not contain '=' padding", sig.contains('='))
    }

    @Test fun `computeHmac output uses URL-safe chars`() {
        repeat(50) {
            val sig = validator.computeHmac("canonical-$it")
            assertFalse("Must not contain '+'", sig.contains('+'))
            assertFalse("Must not contain '/'", sig.contains('/'))
        }
    }

    @Test fun `computeHmac output length is 43 for SHA-256`() {
        val sig = validator.computeHmac("test data")
        assertEquals(43, sig.length)
    }

    /**
     * Q41 — Vetor de teste cross-platform.
     *
     * O valor esperado foi computado no Node.js com:
     *   node -e "const c=require('crypto'); \
     *     console.log(c.createHmac('sha256','TEST_SECRET_KEY') \
     *     .update('1|REG|1700000000|1700003600|fixed-nonce|EMP001|Test User|SYS001') \
     *     .digest('base64') \
     *     .replace(/\\+/g,'-').replace(/\\//g,'_').replace(/=+\$/,''))"
     *
     * Substitua REPLACE_WITH_NODE_OUTPUT pelo output do comando acima antes de commitar.
     */
    @Test fun `computeHmac matches cross-platform reference vector`() {
        val canonical = "1|REG|1700000000|1700003600|fixed-nonce|EMP001|Test User|SYS001"
        val expected  = "eSJdyv9cvTX3kst9JbzhJJoiD_P60Svb2UhaXPgVbBE"
        assertEquals(expected, QRValidator("TEST_SECRET_KEY").computeHmac(canonical))
    }
}
