package com.ebalance.investments

import com.ebalance.investments.application.GetStockPriceHistoryInteractor
import com.ebalance.investments.application.GetStockPriceHistoryUseCase
import com.ebalance.investments.application.*
import com.ebalance.investments.domain.InvestmentRepository
import com.ebalance.investments.domain.StockPriceService
import com.ebalance.investments.infrastructure.persistence.InvestmentRepositoryImpl
import com.ebalance.investments.infrastructure.stock.SerpApiStockPriceService
import org.koin.dsl.module
import javax.sql.DataSource

/**
 * Koin module wiring for the investments feature.
 *
 * Bindings (all singletons):
 *   InvestmentRepository       → InvestmentRepositoryImpl(dataSource)
 *   GetWalletSummaryUseCase    → GetWalletSummaryInteractor
 *   GetWalletHoldingsUseCase   → GetWalletHoldingsInteractor
 *   GetWalletProgressUseCase   → GetWalletProgressInteractor
 */
fun investmentModule(dataSource: DataSource, serpApiKey: String) = module {
    single<InvestmentRepository>          { InvestmentRepositoryImpl(dataSource) }
    single<StockPriceService>             { SerpApiStockPriceService(serpApiKey) }
    single<GetWalletSummaryUseCase>       { GetWalletSummaryInteractor(get()) }
    single<GetWalletHoldingsUseCase>      { GetWalletHoldingsInteractor(get()) }
    single<GetWalletProgressUseCase>      { GetWalletProgressInteractor(get(), get()) }
    single<UpsertInvestmentAssetUseCase>   { UpsertInvestmentAssetInteractor(get()) }
    single<GetStockPriceHistoryUseCase>    { GetStockPriceHistoryInteractor(get(), get()) }
}
