package com.goldsmith.billing.util

import java.util.Locale

object CelebrationWishUtil {
    fun buildWishMessage(
        customerName: String,
        eventType: String,
        senderName: String?
    ): String {
        val name = customerName.trim().ifBlank { "valued customer" }
        val sender = senderName?.trim().orEmpty().ifBlank { "Abu Star Diamonds" }
        val greeting = when (eventType.lowercase(Locale.US)) {
            "birthday" -> "Wishing you a very Happy Birthday"
            "anniversary" -> "Wishing you a very Happy Anniversary"
            else -> "Sending you warm wishes"
        }

        return "$greeting, $name.\nMay your day be filled with happiness, prosperity, and shine.\n\nWarm wishes,\n$sender"
    }

    fun whatsappPhoneNumber(phone: String, defaultCountryCode: String = "91"): String? {
        val digits = phone.filter { it.isDigit() }
        if (digits.length < 10) return null

        return when {
            digits.length == 10 -> defaultCountryCode + digits
            digits.length == 11 && digits.startsWith("0") -> defaultCountryCode + digits.drop(1)
            digits.length in 11..15 -> digits
            else -> null
        }
    }
}
