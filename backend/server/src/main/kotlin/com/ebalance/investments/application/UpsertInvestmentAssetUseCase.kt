package com.ebalance.investments.application

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.ebalance.investments.domain.InvestmentError
import com.ebalance.investments.domain.InvestmentRepository

interface UpsertInvestmentAssetUseCase {
    fun execute(
        ticker: String,
        name: String,
        sector: String,
        exchange: String,
        investedAmount: Double,
        currentValue: Double,
        purchasedAt: String?
    ): Either<InvestmentError, Unit>
}

class UpsertInvestmentAssetInteractor(
    private val repository: InvestmentRepository
) : UpsertInvestmentAssetUseCase {

    override fun execute(
        ticker: String,
        name: String,
        sector: String,
        exchange: String,
        investedAmount: Double,
        currentValue: Double,
        purchasedAt: String?
    ): Either<InvestmentError, Unit> = either {
        ensure(ticker.isNotBlank()) { InvestmentError.InvalidParameter("Ticker cannot be empty") }
        ensure(name.isNotBlank()) { InvestmentError.InvalidParameter("Name cannot be empty") }
        ensure(sector.isNotBlank()) { InvestmentError.InvalidParameter("Sector cannot be empty") }
        ensure(investedAmount >= 0) { InvestmentError.InvalidParameter("Invested amount must be ≥ 0") }
        ensure(currentValue >= 0) { InvestmentError.InvalidParameter("Current value must be ≥ 0") }

        repository.upsertAsset(ticker, name, sector, exchange, investedAmount, currentValue, purchasedAt).bind()
    }
}
