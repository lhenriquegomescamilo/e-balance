package com.ebalance.transactions

import com.ebalance.transactions.application.*
import com.ebalance.transactions.domain.TransactionRepository
import com.ebalance.transactions.infrastructure.persistence.TransactionRepositoryImpl
import org.koin.dsl.module

/**
 * Koin module wiring for the transactions feature.
 *
 * Bindings (all singletons — repository opens a new JDBC connection per call):
 *   TransactionRepository          → TransactionRepositoryImpl(dbPath)
 *   GetTransactionSummaryUseCase   → GetTransactionSummaryInteractor
 *   GetTransactionsUseCase         → GetTransactionsInteractor
 *   GetCategoriesUseCase           → GetCategoriesInteractor
 */
fun transactionModule(dbPath: String) = module {
    single<TransactionRepository>        { TransactionRepositoryImpl(dbPath) }
    single<GetTransactionSummaryUseCase> { GetTransactionSummaryInteractor(get()) }
    single<GetTransactionsUseCase>       { GetTransactionsInteractor(get()) }
    single<GetCategoriesUseCase>         { GetCategoriesInteractor(get()) }
}
