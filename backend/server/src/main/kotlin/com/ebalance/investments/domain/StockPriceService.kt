package com.ebalance.investments.domain

import arrow.core.Either
import java.time.YearMonth

/**
 * Returns the current price and a map of YearMonth → closing price
 * for the requested [window] ("3M", "6M", "1Y").
 */
interface StockPriceService {
    fun getMonthlyPrices(
        ticker: String,
        exchange: String,
        window: String
    ): Either<InvestmentError, Pair<Double, Map<YearMonth, Double>>>
}
