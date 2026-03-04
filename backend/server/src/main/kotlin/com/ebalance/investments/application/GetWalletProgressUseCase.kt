package com.ebalance.investments.application

import arrow.core.Either
import com.ebalance.investments.domain.*

interface GetWalletProgressUseCase {
    fun execute(period: String): Either<InvestmentError, WalletProgress>
}

class GetWalletProgressInteractor(
    private val repository: InvestmentRepository
) : GetWalletProgressUseCase {

    override fun execute(period: String): Either<InvestmentError, WalletProgress> {
        val limitMonths = when (period) {
            "3m"  -> 3
            "12m" -> 12
            else  -> 6   // default: "6m"
        }
        return repository.getSectorSnapshots(limitMonths)
    }
}
