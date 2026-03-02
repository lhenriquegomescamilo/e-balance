package com.ebalance.transactions

import arrow.fx.stm.TVar
import com.ebalance.transactions.application.*
import com.ebalance.transactions.domain.TransactionRepository
import com.ebalance.transactions.infrastructure.persistence.TransactionRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.koin.dsl.module

/**
 * Koin module wiring for the transactions feature.
 *
 * Bindings (all singletons — repository opens a new JDBC connection per call):
 *   TransactionRepository                → TransactionRepositoryImpl(dbPath)
 *   GetTransactionSummaryUseCase         → GetTransactionSummaryInteractor
 *   GetTransactionsUseCase               → GetTransactionsInteractor
 *   GetCategoriesUseCase                 → GetCategoriesInteractor
 *   GetMonthlySummaryUseCase             → GetMonthlySummaryInteractor
 *   UpdateTransactionCategoryUseCase     → UpdateTransactionCategoryInteractor
 */
fun transactionModule(dbPath: String) = module {
    single<TransactionRepository>                { TransactionRepositoryImpl(dbPath) }
    single<GetTransactionSummaryUseCase>         { GetTransactionSummaryInteractor(get()) }
    single<GetTransactionsUseCase>               { GetTransactionsInteractor(get()) }
    single<GetCategoriesUseCase>                 { GetCategoriesInteractor(get()) }
    single<GetMonthlySummaryUseCase>             { GetMonthlySummaryInteractor(get()) }
    single<UpdateTransactionCategoryUseCase>     {
        // TVar.new is suspend; runBlocking is safe here because this factory runs
        // once at server startup outside of any coroutine dispatcher.
        val inFlight = runBlocking { TVar.new(emptySet<Long>()) }
        UpdateTransactionCategoryInteractor(get(), inFlight)
    }
}
