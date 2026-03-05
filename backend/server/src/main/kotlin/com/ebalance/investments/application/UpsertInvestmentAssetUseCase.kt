package com.ebalance.investments.application

import arrow.core.Either
import arrow.core.left
import com.ebalance.investments.domain.InvestmentError
import com.ebalance.investments.domain.InvestmentRepository

interface UpsertInvestmentAssetUseCase {
    fun execute(
        ticker: String,
        name: String,
        sector: String,
        exchange: String,
        investedAmount: Double,
        currentValue: Double
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
        currentValue: Double
    ): Either<InvestmentError, Unit> {
        if (ticker.isBlank())       return InvestmentError.InvalidParameter("Ticker cannot be empty").left()
        if (name.isBlank())         return InvestmentError.InvalidParameter("Name cannot be empty").left()
        if (sector.isBlank())       return InvestmentError.InvalidParameter("Sector cannot be empty").left()
        if (investedAmount < 0)     return InvestmentError.InvalidParameter("Invested amount must be ≥ 0").left()
        if (currentValue < 0)       return InvestmentError.InvalidParameter("Current value must be ≥ 0").left()

        return repository.upsertAsset(ticker, name, sector, exchange, investedAmount, currentValue)
    }
}
