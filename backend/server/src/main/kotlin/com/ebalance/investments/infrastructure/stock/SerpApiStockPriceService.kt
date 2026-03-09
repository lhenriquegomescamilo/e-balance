package com.ebalance.investments.infrastructure.stock

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ebalance.investments.domain.InvestmentError
import com.ebalance.investments.domain.StockPriceService
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** TTL for cached price entries: 1 hour in seconds. */
private const val CACHE_TTL_SECONDS = 3_600L

/** Wire-format for values stored in Redis. */
@Serializable
private data class CachedPriceData(
    val currentPrice: Double,
    val monthlyPrices: Map<String, Double>   // "YYYY-MM" → price
)

open class SerpApiStockPriceService(
    private val apiKey: String,
    private val redis: RedisCommands<String, String>
) : StockPriceService {

    private val log  = LoggerFactory.getLogger(SerpApiStockPriceService::class.java)
    private val http = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val dateFmtFull  = DateTimeFormatter.ofPattern("MMM dd yyyy", Locale.ENGLISH)
    private val dateFmtShort = DateTimeFormatter.ofPattern("MMM d yyyy",  Locale.ENGLISH)

    override fun getMonthlyPrices(
        ticker: String,
        exchange: String,
        window: String
    ): Either<InvestmentError, Pair<Double, Map<YearMonth, Double>>> {
        val cacheKey = "serpapi:${ticker.uppercase()}:${exchange.uppercase()}:${window.uppercase()}"

        readCache(cacheKey)?.let {
            log.debug("Redis cache hit for $cacheKey")
            return it.right()
        }

        return runCatching { doFetch(ticker, exchange, window) }
            .fold(
                onSuccess = { result ->
                    writeCache(cacheKey, result)
                    result.right()
                },
                onFailure = { e ->
                    log.error("SerpAPI request failed for $ticker:$exchange — ${e.message}", e)
                    InvestmentError.DatabaseError(
                        "Failed to fetch price history for $ticker:$exchange — ${e.message}", e
                    ).left()
                }
            )
    }

    private fun readCache(key: String): Pair<Double, Map<YearMonth, Double>>? = runCatching {
        val raw = redis.get(key) ?: return null
        val cached = Json.decodeFromString(CachedPriceData.serializer(), raw)
        cached.currentPrice to cached.monthlyPrices.mapKeys { (k, _) ->
            val (year, month) = k.split("-")
            YearMonth.of(year.toInt(), month.toInt())
        }
    }.getOrElse { e ->
        log.warn("Redis read failed for $key — falling through to fetch: ${e.message}")
        null
    }

    private fun writeCache(key: String, value: Pair<Double, Map<YearMonth, Double>>) =
        runCatching {
            val payload = CachedPriceData(
                currentPrice  = value.first,
                monthlyPrices = value.second.mapKeys { (ym, _) ->
                    "%04d-%02d".format(ym.year, ym.monthValue)
                }
            )
            redis.setex(key, CACHE_TTL_SECONDS, Json.encodeToString(CachedPriceData.serializer(), payload))
        }.onFailure { e ->
            log.warn("Redis write failed for $key — result will not be cached: ${e.message}")
        }

    /**
     * Performs the actual HTTP call to SerpAPI. Extracted so tests can subclass
     * and override this method without making real network requests.
     */
    internal open fun doFetch(
        ticker: String,
        exchange: String,
        window: String
    ): Pair<Double, Map<YearMonth, Double>> {
        val q   = URLEncoder.encode("$ticker:$exchange", "UTF-8")
        val url = "https://serpapi.com/search.json?engine=google_finance&q=$q&window=$window&api_key=$apiKey"

        log.info("SerpAPI → fetching price history for $ticker:$exchange window=$window")
        val response = http.send(
            HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )

        if (response.statusCode() != 200) {
            log.error("SerpAPI ← HTTP ${response.statusCode()} for $ticker:$exchange, body = ${response.body()}")
            throw RuntimeException("SerpAPI HTTP ${response.statusCode()} for $ticker:$exchange")
        }

        val root = json.parseToJsonElement(response.body()).jsonObject

        val currentPrice = root["summary"]
            ?.jsonObject?.get("extracted_price")
            ?.jsonPrimitive?.doubleOrNull
            ?: run {
                val fallback = root["graph"]?.jsonArray?.lastOrNull()
                    ?.jsonObject?.get("price")?.jsonPrimitive?.doubleOrNull
                if (fallback != null) {
                    log.warn("SerpAPI [$ticker:$exchange] no extracted_price in summary — using last graph price ($fallback) as current price")
                }
                fallback ?: throw RuntimeException(
                    "No current price in SerpAPI response for $ticker:$exchange — check that the exchange is correct"
                )
            }

        val monthlyPrices = mutableMapOf<YearMonth, Double>()
        root["graph"]?.jsonArray?.forEach { el ->
            val obj   = el.jsonObject
            val price = obj["price"]?.jsonPrimitive?.doubleOrNull ?: return@forEach
            val date  = obj["date"]?.jsonPrimitive?.content      ?: return@forEach
            parseYearMonth(date)?.let { ym -> monthlyPrices[ym] = price }
        }

        log.info("SerpAPI ← $ticker:$exchange currentPrice=$currentPrice, ${monthlyPrices.size} monthly data point(s)")
        return Pair(currentPrice, monthlyPrices.toMap())
    }

    /**
     * Parses date strings from SerpAPI graph:
     *   intraday  → "Mar 04 2026, 09:30 AM UTC-05:00"
     *   daily     → "Mar 04 2026"  or  "Mar 4 2026"
     */
    private fun parseYearMonth(dateStr: String): YearMonth? {
        val datePart = dateStr.trim().let {
            if (it.matches(Regex("\\w{3} \\d{1,2} \\d{4},?.*")))
                it.substringBefore(",").trim()
            else
                it.replace(",", " ").trim()
        }

        return listOf(dateFmtFull, dateFmtShort).firstNotNullOfOrNull { fmt ->
            runCatching { YearMonth.from(LocalDate.parse(datePart, fmt)) }.getOrNull()
        }
    }
}
