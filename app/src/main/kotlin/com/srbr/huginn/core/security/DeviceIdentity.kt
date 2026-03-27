package com.srbr.huginn.core.security

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceIdentity @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val salt = "SRBR_HUGINN_2024"

    private val deviceId: String by lazy {
        val androidId = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        sha256("$salt:$androidId").take(16).uppercase()
    }

    fun getDeviceId(): String = deviceId

    fun getDisplayId(): String {
        val id = deviceId
        return "SRBR-${id.take(4)}-${id.drop(4).take(4)}"
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
