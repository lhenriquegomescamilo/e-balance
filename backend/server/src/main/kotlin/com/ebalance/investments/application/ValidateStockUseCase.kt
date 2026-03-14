package com.ebalance.investments.application

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.ebalance.investments.domain.InvestmentError
import com.ebalance.investments.domain.StockPriceService

interface ValidateStockUseCase {
    fun execute(ticker: String, exchange: String): Either<InvestmentError, Boolean>
}

class ValidateStockInteractor(
    private val stockPriceService: StockPriceService
) : ValidateStockUseCase {

    override fun execute(ticker: String, exchange: String): Either<InvestmentError, Boolean> = either {
        ensure(ticker.isNotBlank()) { InvestmentError.InvalidParameter("Ticker cannot be empty") }
        ensure(exchange.isNotBlank()) { InvestmentError.InvalidParameter("Exchange cannot be empty") }

        // Use a small window — we only care whether the stock exists, not the full history.
        // The result is automatically cached in Redis by SerpApiStockPriceService.
        stockPriceService.getMonthlyPrices(ticker.trim(), exchange.trim(), "1M").fold(
            ifLeft  = { false },
            ifRight = { true }
        )
    }
}
