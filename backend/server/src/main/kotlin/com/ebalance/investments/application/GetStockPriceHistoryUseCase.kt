package com.ebalance.investments.application

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ebalance.investments.domain.*
import org.slf4j.LoggerFactory
import java.time.format.DateTimeFormatter
import java.util.Locale

interface GetStockPriceHistoryUseCase {
    fun execute(window: String): Either<InvestmentError, List<StockPriceHistory>>
}

class GetStockPriceHistoryInteractor(
    private val repository: InvestmentRepository,
    private val stockPriceService: StockPriceService
) : GetStockPriceHistoryUseCase {

    private val log        = LoggerFactory.getLogger(GetStockPriceHistoryInteractor::class.java)
    private val displayFmt = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

    override fun execute(window: String): Either<InvestmentError, List<StockPriceHistory>> {
        val apiWindow = when (window.uppercase()) {
            "3M"  -> "3M"
            "1Y"  -> "1Y"
            else  -> "6M"
        }

        val assets = repository.getAssets().fold(
            ifLeft  = { return it.left() },
            ifRight = { it }
        )

        val histories = assets.mapNotNull { asset ->
            if (asset.exchange.isBlank()) {
                log.warn("Skipping ${asset.ticker} — exchange not set")
                return@mapNotNull null
            }
            stockPriceService.getMonthlyPrices(asset.ticker, asset.exchange, apiWindow)
                .fold(
                    ifLeft  = { err ->
                        log.warn("Skipping ${asset.ticker}:${asset.exchange} — ${err.message}")
                        null
                    },
                    ifRight = { (currentPrice, monthlyPrices) ->
                        if (currentPrice <= 0.0 || monthlyPrices.isEmpty()) {
                            log.warn("Skipping ${asset.ticker}:${asset.exchange} — price data unavailable")
                            return@mapNotNull null
                        }
                        val sortedMonths = monthlyPrices.keys.sorted()
                        val prices       = sortedMonths.map { monthlyPrices.getValue(it) }
                        val firstPrice   = prices.first()
                        val changePct    = if (firstPrice > 0.0) ((currentPrice - firstPrice) / firstPrice) * 100.0 else 0.0

                        StockPriceHistory(
                            ticker          = asset.ticker,
                            name            = asset.name,
                            exchange        = asset.exchange,
                            sector          = asset.sector,
                            currentPrice    = currentPrice,
                            changePct       = changePct,
                            investedAmount  = asset.investedAmount,
                            currentValue    = asset.currentValue,
                            pnl             = asset.pnl,
                            roi             = asset.roi,
                            qty             = if (currentPrice > 0.0) asset.currentValue / currentPrice else 0.0,
                            months          = sortedMonths.map { it.format(displayFmt) },
                            prices          = prices
                        )
                    }
                )
        }

        return histories.right()
    }
}
