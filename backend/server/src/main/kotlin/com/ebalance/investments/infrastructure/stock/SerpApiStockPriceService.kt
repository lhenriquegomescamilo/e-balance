package com.ebalance.investments.infrastructure.stock

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ebalance.investments.domain.InvestmentError
import com.ebalance.investments.domain.StockPriceService
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

class SerpApiStockPriceService(private val apiKey: String) : StockPriceService {

    private val log  = LoggerFactory.getLogger(SerpApiStockPriceService::class.java)
    private val http = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val dateFmtFull  = DateTimeFormatter.ofPattern("MMM dd yyyy", Locale.ENGLISH)
    private val dateFmtShort = DateTimeFormatter.ofPattern("MMM d yyyy",  Locale.ENGLISH)

    override fun getMonthlyPrices(
        ticker: String,
        exchange: String,
        window: String
    ): Either<InvestmentError, Pair<Double, Map<YearMonth, Double>>> =
        runCatching {
            val q   = URLEncoder.encode("$ticker:$exchange", "UTF-8")
            val url = "https://serpapi.com/search.json?engine=google_finance&q=$q&window=$window&api_key=$apiKey"

            log.info("SerpAPI → fetching price history for $ticker:$exchange window=$window")
            val response = http.send(
                HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            )

            if (response.statusCode() != 200) {
                log.error("SerpAPI ← HTTP ${response.statusCode()} for $ticker:$exchange")
                throw RuntimeException("SerpAPI HTTP ${response.statusCode()} for $ticker:$exchange")
            }

            val root = json.parseToJsonElement(response.body()).jsonObject

            val currentPrice = root["summary"]
                ?.jsonObject?.get("extracted_price")
                ?.jsonPrimitive?.doubleOrNull
                ?: run {
                    // Fallback: use the last price point from the graph (e.g. wrong exchange but graph still returned)
                    val fallback = root["graph"]?.jsonArray?.lastOrNull()
                        ?.jsonObject?.get("price")?.jsonPrimitive?.doubleOrNull
                    if (fallback != null) {
                        log.warn("SerpAPI [$ticker:$exchange] no extracted_price in summary — using last graph price ($fallback) as current price")
                    }
                    fallback ?: throw RuntimeException("No current price in SerpAPI response for $ticker:$exchange — check that the exchange is correct")
                }

            // Group graph data by YearMonth — last price in the month wins
            val monthlyPrices = mutableMapOf<YearMonth, Double>()
            root["graph"]?.jsonArray?.forEach { el ->
                val obj   = el.jsonObject
                val price = obj["price"]?.jsonPrimitive?.doubleOrNull ?: return@forEach
                val date  = obj["date"]?.jsonPrimitive?.content      ?: return@forEach
                parseYearMonth(date)?.let { ym -> monthlyPrices[ym] = price }
            }

            log.info("SerpAPI ← $ticker:$exchange currentPrice=$currentPrice, ${monthlyPrices.size} monthly data point(s)")
            Pair(currentPrice, monthlyPrices.toMap())
        }.fold(
            onSuccess = { it.right() },
            onFailure = { e ->
                log.error("SerpAPI request failed for $ticker:$exchange — ${e.message}", e)
                InvestmentError.DatabaseError(
                    "Failed to fetch price history for $ticker:$exchange — ${e.message}", e
                ).left()
            }
        )

    /**
     * Parses date strings from SerpAPI graph:
     *   intraday  → "Mar 04 2026, 09:30 AM UTC-05:00"
     *   daily     → "Mar 04 2026"  or  "Mar 4 2026"
     */
    private fun parseYearMonth(dateStr: String): YearMonth? {
        // Strip everything after the first comma that is part of the time component
        val datePart = dateStr.trim().let {
            // If format is "MMM d yyyy, ..." take the part before the comma
            // If format is "MMM d, yyyy" (comma after day) keep as-is and remove comma
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
