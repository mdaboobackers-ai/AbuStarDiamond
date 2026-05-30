package com.goldsmith.billing.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest
import java.util.Locale

object AndroidSigningInfo {
    fun sha1(context: Context): String =
        runCatching {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            }.orEmpty()
            signatures.firstOrNull()?.toByteArray()?.let(::sha1HexWithColons).orEmpty()
        }.getOrDefault("")

    private fun sha1HexWithColons(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(bytes)
        return digest.joinToString(":") { byte ->
            "%02X".format(Locale.US, byte)
        }
    }
}
