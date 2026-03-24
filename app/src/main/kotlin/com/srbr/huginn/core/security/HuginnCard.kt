package com.srbr.huginn.core.security

import org.json.JSONObject

/**
 * Domain model — representa um cartão Huginn recebido via QR de cadastro do Odin.
 * Pure data class sem dependências Android — testável facilmente.
 */
data class HuginnCard(
    val employeeId:   String,
    val employeeName: String,
    val employeeArea: String,
    val employeeRole: String?,
    val systemId:     String,
    val systemName:   String,
    val cardColor:    String,
    val registeredAt: Long,
    val nonce:        String
) {
    fun toJson(): String = JSONObject().apply {
        put("employee_id",   employeeId)
        put("employee_name", employeeName)
        put("employee_area", employeeArea)
        put("employee_role", employeeRole ?: "")
        put("system_id",     systemId)
        put("system_name",   systemName)
        put("card_color",    cardColor)
        put("registered_at", registeredAt)
        put("nonce",         nonce)
    }.toString()

    companion object {
        fun fromJson(json: String): HuginnCard? = runCatching {
            JSONObject(json).let { o ->
                HuginnCard(
                    employeeId   = o.getString("employee_id"),
                    employeeName = o.getString("employee_name"),
                    employeeArea = o.getString("employee_area"),
                    employeeRole = o.optString("employee_role").ifEmpty { null },
                    systemId     = o.getString("system_id"),
                    systemName   = o.getString("system_name"),
                    cardColor    = o.getString("card_color"),
                    registeredAt = o.getLong("registered_at"),
                    nonce        = o.getString("nonce")
                )
            }
        }.getOrNull()
    }
}
