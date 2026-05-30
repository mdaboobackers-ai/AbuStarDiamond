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
import java.net.HttpURLConnection
import java.net.URL
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

data class LatestGoldRates(
    val rate24K: Double,
    val rate22K: Double? = null,
    val rate20K: Double? = null,
    val rate18K: Double? = null,
    val sourceLabel: String
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
        val websiteRate = fetchWebsiteRate()
        if (websiteRate != null) {
            return@withContext MarketRateSnapshot(
                rate24K = websiteRate.rate,
                city = city,
                state = state,
                country = country,
                sourceLabel = websiteRate.source,
                isLocationBased = false
            )
        }
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
        fetchWebsiteRate()?.rate
    }

    suspend fun fetchLatestGoldRates(): LatestGoldRates? = withContext(Dispatchers.IO) {
        fetchWebsiteRate()?.let { rate ->
            LatestGoldRates(
                rate24K = rate.rate,
                rate22K = rate.rate22K,
                rate18K = rate.rate18K,
                sourceLabel = rate.source
            )
        }
    }

    private fun fetchWebsiteRate(): WebsiteRate? {
        val sources = listOf(
            WebsiteSource("T. Nagar LKS Today's Rate", "https://www.tnagarlks.com/"),
            WebsiteSource("T. Nagar LKS Today's Rate", "http://www.tnagarlks.com/"),
            WebsiteSource("SLN Bullion India Gold", "https://bcast.slnbullion.com:443/VOTSBroadcastStreaming/Services/xml/GetLiveRateByTemplateID/sln"),
            WebsiteSource("SLN Bullion MJDMA Gold", "https://bcast.slnbullion.com:443/VOTSBroadcastStreaming/Services/xml/GetLiveRateByTemplateID/slnmjdma"),
            WebsiteSource("SLN Bullion", "https://slnbullion.in/")
        )
        return sources.firstNotNullOfOrNull { source ->
            runCatching {
                val html = download(source.url)
                parseRates(html, source.label)
            }.getOrNull()
        }
    }

    private fun download(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 16000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15) AbuStarDiamonds/4.0")
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }

    private fun parseRates(html: String, sourceLabel: String): WebsiteRate? {
        parseSlnLiveRates(html, sourceLabel)?.let { return it }

        val text = html
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&#8377;", "₹")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")

        parseLksRateTable(text)?.let {
            return WebsiteRate(
                rate = it.rate24K,
                source = sourceLabel,
                rate22K = it.rate22K,
                rate18K = it.rate18K
            )
        }

        return parse24KRateFromCandidates(text)?.let { WebsiteRate(it, sourceLabel) }
    }

    private fun parseSlnLiveRates(raw: String, sourceLabel: String): WebsiteRate? {
        val rows = raw.lines()
            .map { line -> line.split('\t').map(String::trim).filter(String::isNotBlank) }
            .filter { it.size >= 4 }
        rows.firstOrNull { cells -> cells.any { it.equals("India Gold", ignoreCase = true) } }?.let { cells ->
            val goldIndex = cells.indexOfFirst { it.equals("India Gold", ignoreCase = true) }
            val sellRatePerTenGrams = cells.drop(goldIndex + 1).firstNotNullOfOrNull { it.toDoubleOrNull() }
            val rate24K = sellRatePerTenGrams?.div(10.0)
            if (rate24K != null && rate24K in 10_000.0..25_000.0) {
                return WebsiteRate(rate = rate24K, source = sourceLabel)
            }
        }

        rows.firstOrNull { cells -> cells.any { it.equals("GOLD", ignoreCase = true) } }?.let { cells ->
            val goldIndex = cells.indexOfFirst { it.equals("GOLD", ignoreCase = true) }
            val rate22K = cells.drop(goldIndex + 1).firstNotNullOfOrNull { it.toDoubleOrNull() }
            if (rate22K != null && rate22K in 10_000.0..20_000.0) {
                return WebsiteRate(
                    rate = rate22K / 0.916,
                    source = sourceLabel,
                    rate22K = rate22K
                )
            }
        }
        return null
    }

    private fun parseLksRateTable(text: String): LksRates? {
        val rate18 = Regex("""Gold\s+Rate\s*\(\s*18\s*k\s*\)\s*([0-9]{4,6})""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        val rate22 = Regex("""Gold\s+Rate\s*\(\s*22\s*k\s*\)\s*([0-9]{4,6})""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        val rate24 = Regex("""Gold\s+Rate\s*\(\s*24\s*k\s*\)\s*([0-9]{4,6})""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        val normalized24 = rate24?.let(::normalizeRateCandidate)
        return if (normalized24 != null) {
            LksRates(
                rate24K = normalized24,
                rate22K = rate22?.let(::normalizeRateCandidate),
                rate18K = rate18?.let(::normalizeRateCandidate)
            )
        } else {
            null
        }
    }

    private fun parse24KRateFromCandidates(text: String): Double? {
        val numberRegex = Regex("""(?:₹|Rs\.?|INR)?\s*([0-9]{1,3}(?:,[0-9]{2,3})+|[0-9]{4,6})(?:\.\d+)?""", RegexOption.IGNORE_CASE)
        return numberRegex.findAll(text)
            .mapNotNull { match ->
                val value = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return@mapNotNull null
                val normalized = normalizeRateCandidate(value) ?: return@mapNotNull null
                val start = (match.range.first - 90).coerceAtLeast(0)
                val end = (match.range.last + 90).coerceAtMost(text.length - 1)
                val context = text.substring(start, end + 1).lowercase(Locale.US)
                val score = rateScore(context, normalized)
                RateCandidate(normalized, score)
            }
            .filter { it.score > 0 }
            .maxWithOrNull(compareBy<RateCandidate> { it.score }.thenBy { it.rate })
            ?.rate
    }

    private fun normalizeRateCandidate(value: Double): Double? = when {
        value in 10_000.0..25_000.0 -> value
        // Some jewellers publish 8g / sovereign values; convert only when clearly in that range.
        value in 80_000.0..200_000.0 -> value / 8.0
        else -> null
    }

    private fun rateScore(context: String, rate: Double): Int {
        var score = 0
        if ("24" in context || "999" in context || "pure" in context) score += 6
        if ("today" in context || "todays" in context || "rate" in context) score += 4
        if ("gold" in context || "bullion" in context) score += 3
        if ("22" in context || "916" in context) score -= 4
        if ("silver" in context) score -= 6
        if (rate >= 12_000.0) score += 2
        return score
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
    private data class WebsiteSource(val label: String, val url: String)
    private data class WebsiteRate(
        val rate: Double,
        val source: String,
        val rate22K: Double? = null,
        val rate18K: Double? = null
    )
    private data class RateCandidate(val rate: Double, val score: Int)
    private data class LksRates(val rate24K: Double, val rate22K: Double?, val rate18K: Double?)

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

        fun parseLksRatesForTest(html: String): LatestGoldRates? {
            val serviceText = html
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ")
            val rate18 = Regex("""Gold\s+Rate\s*\(\s*18\s*k\s*\)\s*([0-9]{4,6})""", RegexOption.IGNORE_CASE)
                .find(serviceText)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            val rate22 = Regex("""Gold\s+Rate\s*\(\s*22\s*k\s*\)\s*([0-9]{4,6})""", RegexOption.IGNORE_CASE)
                .find(serviceText)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            val rate24 = Regex("""Gold\s+Rate\s*\(\s*24\s*k\s*\)\s*([0-9]{4,6})""", RegexOption.IGNORE_CASE)
                .find(serviceText)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            return rate24?.takeIf { it in 10_000.0..25_000.0 }?.let {
                LatestGoldRates(rate24K = it, rate22K = rate22, rate18K = rate18, sourceLabel = "T. Nagar LKS Today's Rate")
            }
        }

        fun parseSlnRatesForTest(raw: String): LatestGoldRates? {
            val rows = raw.lines()
                .map { line -> line.split('\t').map(String::trim).filter(String::isNotBlank) }
                .filter { it.size >= 4 }
            rows.firstOrNull { cells -> cells.any { it.equals("India Gold", ignoreCase = true) } }?.let { cells ->
                val goldIndex = cells.indexOfFirst { it.equals("India Gold", ignoreCase = true) }
                val ratePerTenGrams = cells.drop(goldIndex + 1).firstNotNullOfOrNull { it.toDoubleOrNull() }
                val rate24K = ratePerTenGrams?.div(10.0)
                if (rate24K != null && rate24K in 10_000.0..25_000.0) {
                    return LatestGoldRates(rate24K = rate24K, sourceLabel = "SLN Bullion India Gold")
                }
            }
            rows.firstOrNull { cells -> cells.any { it.equals("GOLD", ignoreCase = true) } }?.let { cells ->
                val goldIndex = cells.indexOfFirst { it.equals("GOLD", ignoreCase = true) }
                val rate22K = cells.drop(goldIndex + 1).firstNotNullOfOrNull { it.toDoubleOrNull() }
                if (rate22K != null && rate22K in 10_000.0..20_000.0) {
                    return LatestGoldRates(
                        rate24K = rate22K / 0.916,
                        rate22K = rate22K,
                        sourceLabel = "SLN Bullion MJDMA Gold"
                    )
                }
            }
            return null
        }
    }
}
