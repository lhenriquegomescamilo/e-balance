package com.ebalance.investments.application

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ebalance.investments.domain.*
import org.slf4j.LoggerFactory
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

interface GetWalletProgressUseCase {
    fun execute(period: String): Either<InvestmentError, WalletProgress>
}

class GetWalletProgressInteractor(
    private val repository: InvestmentRepository,
    private val stockPriceService: StockPriceService
) : GetWalletProgressUseCase {

    private val log        = LoggerFactory.getLogger(GetWalletProgressInteractor::class.java)
    private val displayFmt = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

    override fun execute(period: String): Either<InvestmentError, WalletProgress> {
        val window = when (period.lowercase()) {
            "3m"  -> "3M"
            "12m" -> "1Y"
            else  -> "6M"
        }

        val assets = repository.getAssets().fold(
            ifLeft  = { return it.left() },
            ifRight = { it }
        )

        if (assets.isEmpty()) return WalletProgress(emptyList(), emptyList()).right()

        // For each asset with a known exchange, fetch monthly prices (best-effort)
        data class AssetPrices(
            val asset: InvestmentAsset,
            val currentPrice: Double,
            val byMonth: Map<YearMonth, Double>   // YearMonth → last price that month
        )

        val priceData = assets.mapNotNull { asset ->
            if (asset.exchange.isBlank()) {
                log.warn("Skipping ${asset.ticker} — exchange is not set")
                return@mapNotNull null
            }
            stockPriceService.getMonthlyPrices(asset.ticker, asset.exchange, window)
                .fold(
                    ifLeft  = { err ->
                        log.warn("Skipping ${asset.ticker}:${asset.exchange} — ${err.message}")
                        null
                    },
                    ifRight = { (cur, monthly) ->
                        if (cur <= 0.0) {
                            log.warn("Skipping ${asset.ticker}:${asset.exchange} — current price is zero or negative")
                            null
                        } else {
                            log.info("${asset.ticker}:${asset.exchange} — currentPrice=$cur, ${monthly.size} monthly data point(s)")
                            AssetPrices(asset, cur, monthly)
                        }
                    }
                )
        }

        if (priceData.isEmpty()) {
            log.warn("No price data available for any asset (${assets.size} asset(s) skipped) — portfolio progress chart will be empty")
            return WalletProgress(emptyList(), emptyList()).right()
        }
        log.info("Building portfolio progress: ${priceData.size}/${assets.size} asset(s) with price data")

        // Union of all months across all stocks, sorted chronologically
        val allMonths = priceData.flatMap { it.byMonth.keys }.toSortedSet().toList()
        if (allMonths.isEmpty()) return WalletProgress(emptyList(), emptyList()).right()

        // Group assets by sector; for each sector compute value at each month
        val series = priceData
            .groupBy { it.asset.sector }
            .map { (sector, items) ->
                val values = allMonths.map { ym ->
                    items.sumOf { ap ->
                        // Forward-fill: use the latest known price on or before this month
                        val price = ap.byMonth[ym]
                            ?: ap.byMonth.entries
                                .filter { it.key <= ym }
                                .maxByOrNull { it.key }
                                ?.value
                            ?: 0.0
                        // Portfolio value = shares × price, where shares = current_value / current_price
                        price * (ap.asset.currentValue / ap.currentPrice)
                    }
                }
                SectorProgress(sector = sector, values = values.map { v -> (v * 100).toLong() / 100.0 })
            }
            .sortedBy { it.sector }

        return WalletProgress(
            months = allMonths.map { it.format(displayFmt) },
            series = series
        ).right()
    }
}
