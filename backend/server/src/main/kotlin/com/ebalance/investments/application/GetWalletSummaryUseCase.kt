package com.ebalance.investments.application

import arrow.core.Either
import com.ebalance.investments.domain.*

interface GetWalletSummaryUseCase {
    fun execute(): Either<InvestmentError, WalletSummary>
}

class GetWalletSummaryInteractor(
    private val repository: InvestmentRepository
) : GetWalletSummaryUseCase {

    override fun execute(): Either<InvestmentError, WalletSummary> =
        repository.getAssets().map { assets ->
            val totalInvested     = assets.sumOf { it.investedAmount }
            val totalCurrentValue = assets.sumOf { it.currentValue }
            val totalPnl          = totalCurrentValue - totalInvested
            val roi               = if (totalInvested > 0.0) (totalPnl / totalInvested) * 100.0 else 0.0

            val sectors = assets
                .groupBy { it.sector }
                .map { (sectorName, sectorAssets) ->
                    val sInvested = sectorAssets.sumOf { it.investedAmount }
                    val sCurrent  = sectorAssets.sumOf { it.currentValue }
                    val sPnl      = sCurrent - sInvested
                    val sRoi      = if (sInvested > 0.0) (sPnl / sInvested) * 100.0 else 0.0
                    val pct       = if (totalInvested > 0.0) (sInvested / totalInvested) * 100.0 else 0.0
                    SectorSummary(
                        name         = sectorName,
                        invested     = sInvested,
                        currentValue = sCurrent,
                        pnl          = sPnl,
                        roi          = sRoi,
                        percentage   = pct
                    )
                }
                .sortedByDescending { it.invested }

            WalletSummary(
                totalInvested     = totalInvested,
                totalCurrentValue = totalCurrentValue,
                totalPnl          = totalPnl,
                roi               = roi,
                sectors           = sectors
            )
        }
}
