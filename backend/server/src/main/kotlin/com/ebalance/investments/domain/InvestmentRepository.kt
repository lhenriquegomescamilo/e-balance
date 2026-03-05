package com.ebalance.investments.domain

import arrow.core.Either

interface InvestmentRepository {
    fun getAssets(): Either<InvestmentError.DatabaseError, List<InvestmentAsset>>
    fun getSectorSnapshots(limitMonths: Int): Either<InvestmentError.DatabaseError, WalletProgress>
    fun upsertAsset(ticker: String, name: String, sector: String, exchange: String, investedAmount: Double, currentValue: Double): Either<InvestmentError.DatabaseError, Unit>
}
