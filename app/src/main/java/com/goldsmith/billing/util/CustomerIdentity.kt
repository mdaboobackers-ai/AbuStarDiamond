package com.goldsmith.billing.util

import com.goldsmith.billing.data.model.Customer
import java.util.Locale
import java.util.UUID

object CustomerIdentity {
    const val PREFIX = "ASD"

    fun newExternalId(): String =
        "$PREFIX-${UUID.randomUUID().toString().replace("-", "").take(12).uppercase(Locale.US)}"

    fun normalizeExternalId(value: String?): String {
        val compact = value.orEmpty().trim().uppercase(Locale.US)
        return compact.ifBlank { "" }
    }

    fun normalizedName(value: String): String =
        value.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .filter { it.isLetterOrDigit() || it == ' ' }

    fun namesMatch(importedName: String, existing: Customer): Boolean {
        val imported = normalizedName(importedName)
        if (imported.isBlank()) return false
        return imported == normalizedName(existing.name)
    }

    fun Customer.withStableExternalId(): Customer {
        val current: String? = externalId
        val normalized = normalizeExternalId(current)
        return if (normalized.isBlank()) copy(externalId = newExternalId()) else copy(externalId = normalized)
    }
}
