package com.goldsmith.billing.data.remote

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class MarketRateSnapshot(
    val rate24K: Double,
    val city: String = "",
    val state: String = "Tamil Nadu",
    val country: String = "India",
    val sourceLabel: String = "Saved manual rate",
    val isLocationBased: Boolean = false
)

@Singleton
class GoldRateService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun fetchCurrentMarketSnapshot(savedRate24K: Double): MarketRateSnapshot = withContext(Dispatchers.IO) {
        val locationName = resolveLastKnownLocationName()
        val city = locationName?.city.orEmpty()
        val state = locationName?.state?.ifEmpty { "Tamil Nadu" } ?: "Tamil Nadu"
        val country = locationName?.country?.ifEmpty { "India" } ?: "India"
        val estimatedRate = estimateTamilNaduRate(savedRate24K, city)

        MarketRateSnapshot(
            rate24K = estimatedRate,
            city = city,
            state = state,
            country = country,
            sourceLabel = if (city.isNotEmpty()) "Location estimate" else "Saved manual rate",
            isLocationBased = city.isNotEmpty()
        )
    }

    /**
     * Backward-compatible API for callers that only need a number.
     * Never returns random demo values, so app startup cannot silently reset rates.
     */
    suspend fun fetchLatestGoldRate(): Double? = withContext(Dispatchers.IO) {
        null
    }

    private fun resolveLastKnownLocationName(): LocationName? {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null

        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location = locationManager.getProviders(true)
                .asSequence()
                .mapNotNull { provider -> locationManager.getLastKnownLocation(provider) }
                .maxByOrNull { it.time }
                ?: return null

            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val address = geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
            LocationName(
                city = address?.locality ?: address?.subAdminArea ?: "",
                state = address?.adminArea ?: "",
                country = address?.countryName ?: ""
            )
        } catch (_: Exception) {
            null
        }
    }

    private data class LocationName(val city: String, val state: String, val country: String)

    companion object {
        private val tamilNaduCityAdjustments = mapOf(
            "chennai" to 8.0,
            "coimbatore" to 5.0,
            "madurai" to 4.0,
            "tiruchirappalli" to 3.0,
            "trichy" to 3.0,
            "salem" to 2.0,
            "tirunelveli" to 2.0,
            "thoothukudi" to 1.0,
            "tuticorin" to 1.0,
            "erode" to 2.0,
            "vellore" to 2.0,
            "tiruppur" to 3.0,
            "thanjavur" to 1.0,
            "dindigul" to 1.0,
            "nagercoil" to 1.0
        )

        fun estimateTamilNaduRate(savedRate24K: Double, city: String?): Double {
            val normalized = city?.trim()?.lowercase(Locale.US).orEmpty()
            val adjustment = tamilNaduCityAdjustments[normalized] ?: 0.0
            return "%.2f".format(Locale.US, savedRate24K + adjustment).toDouble()
        }
    }
}
