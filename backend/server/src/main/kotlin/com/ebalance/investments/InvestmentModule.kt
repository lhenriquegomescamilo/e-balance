package com.ebalance.investments

import com.ebalance.investments.application.GetStockPriceHistoryInteractor
import com.ebalance.investments.application.GetStockPriceHistoryUseCase
import com.ebalance.investments.application.*
import com.ebalance.investments.domain.InvestmentRepository
import com.ebalance.investments.domain.StockPriceService
import com.ebalance.investments.infrastructure.persistence.InvestmentRepositoryImpl
import com.ebalance.investments.infrastructure.stock.SerpApiStockPriceService
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module

/**
 * Koin module wiring for the investments feature.
 *
 * Bindings (all singletons):
 *   InvestmentRepository       → InvestmentRepositoryImpl(database)
 *   GetWalletSummaryUseCase    → GetWalletSummaryInteractor
 *   GetWalletHoldingsUseCase   → GetWalletHoldingsInteractor
 *   GetWalletProgressUseCase   → GetWalletProgressInteractor
 */
fun investmentModule(database: Database, serpApiKey: String) = module {
    single<InvestmentRepository>          { InvestmentRepositoryImpl(database) }
    single<StockPriceService>             { SerpApiStockPriceService(serpApiKey) }
    single<GetWalletSummaryUseCase>       { GetWalletSummaryInteractor(get()) }
    single<GetWalletHoldingsUseCase>      { GetWalletHoldingsInteractor(get()) }
    single<GetWalletProgressUseCase>      { GetWalletProgressInteractor(get(), get()) }
    single<UpsertInvestmentAssetUseCase>   { UpsertInvestmentAssetInteractor(get()) }
    single<GetStockPriceHistoryUseCase>    { GetStockPriceHistoryInteractor(get(), get()) }
}
