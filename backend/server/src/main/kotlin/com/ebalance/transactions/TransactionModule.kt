package com.ebalance.transactions

import arrow.fx.stm.TVar
import com.ebalance.transactions.application.*
import com.ebalance.transactions.domain.TransactionRepository
import com.ebalance.transactions.infrastructure.persistence.TransactionRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module

/**
 * Koin module wiring for the transactions feature.
 *
 * Bindings (all singletons — repository uses Exposed DSL inside transaction { } blocks):
 *   TransactionRepository                → TransactionRepositoryImpl(database)
 *   GetTransactionSummaryUseCase         → GetTransactionSummaryInteractor
 *   GetTransactionsUseCase               → GetTransactionsInteractor
 *   GetCategoriesUseCase                 → GetCategoriesInteractor
 *   GetMonthlySummaryUseCase             → GetMonthlySummaryInteractor
 *   UpdateTransactionCategoryUseCase     → UpdateTransactionCategoryInteractor
 */
fun transactionModule(database: Database) = module {
    single<TransactionRepository>                { TransactionRepositoryImpl(database) }
    single<GetTransactionSummaryUseCase>         { GetTransactionSummaryInteractor(get()) }
    single<GetTransactionsUseCase>               { GetTransactionsInteractor(get()) }
    single<GetCategoriesUseCase>                 { GetCategoriesInteractor(get()) }
    single<GetMonthlySummaryUseCase>             { GetMonthlySummaryInteractor(get()) }
    single<UpdateTransactionCategoryUseCase>     {
        // TVar.new is suspend; runBlocking is safe here because this factory runs
        // once at server startup outside of any coroutine dispatcher.
        val inFlight  = runBlocking { TVar.new(emptySet<Long>()) }
        val committed = runBlocking { TVar.new(emptyMap<Long, Long>()) }
        UpdateTransactionCategoryInteractor(get(), inFlight, committed)
    }
}
