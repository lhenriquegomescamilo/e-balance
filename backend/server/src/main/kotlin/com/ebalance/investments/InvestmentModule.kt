package com.ebalance.investments

import com.ebalance.investments.application.*
import com.ebalance.investments.domain.InvestmentRepository
import com.ebalance.investments.infrastructure.persistence.InvestmentRepositoryImpl
import org.koin.dsl.module

/**
 * Koin module wiring for the investments feature.
 *
 * Bindings (all singletons):
 *   InvestmentRepository       → InvestmentRepositoryImpl(dbPath)
 *   GetWalletSummaryUseCase    → GetWalletSummaryInteractor
 *   GetWalletHoldingsUseCase   → GetWalletHoldingsInteractor
 *   GetWalletProgressUseCase   → GetWalletProgressInteractor
 */
fun investmentModule(dbPath: String) = module {
    single<InvestmentRepository>          { InvestmentRepositoryImpl(dbPath) }
    single<GetWalletSummaryUseCase>       { GetWalletSummaryInteractor(get()) }
    single<GetWalletHoldingsUseCase>      { GetWalletHoldingsInteractor(get()) }
    single<GetWalletProgressUseCase>      { GetWalletProgressInteractor(get()) }
    single<UpsertInvestmentAssetUseCase>  { UpsertInvestmentAssetInteractor(get()) }
}
