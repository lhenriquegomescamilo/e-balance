package com.ebalance.investments.application

import arrow.core.Either
import com.ebalance.investments.domain.*

interface GetWalletHoldingsUseCase {
    fun execute(): Either<InvestmentError, List<InvestmentAsset>>
}

class GetWalletHoldingsInteractor(
    private val repository: InvestmentRepository
) : GetWalletHoldingsUseCase {
    override fun execute(): Either<InvestmentError, List<InvestmentAsset>> =
        repository.getAssets()
}
