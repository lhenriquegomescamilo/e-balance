package com.ebalance.transactions

import arrow.fx.stm.TVar
import com.ebalance.transactions.application.*
import com.ebalance.transactions.domain.TransactionRepository
import com.ebalance.transactions.infrastructure.persistence.TransactionRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module

fun transactionModule(database: Database, modelPath: String) = module {
    single<TransactionRepository>                { TransactionRepositoryImpl(database) }
    single<GetTransactionSummaryUseCase>         { GetTransactionSummaryInteractor(get()) }
    single<GetTransactionsUseCase>               { GetTransactionsInteractor(get()) }
    single<GetCategoriesUseCase>                 { GetCategoriesInteractor(get()) }
    single<GetMonthlySummaryUseCase>             { GetMonthlySummaryInteractor(get()) }
    single<UpdateTransactionCategoryUseCase>     {
        val inFlight  = runBlocking { TVar.new(emptySet<Long>()) }
        val committed = runBlocking { TVar.new(emptyMap<Long, Long>()) }
        UpdateTransactionCategoryInteractor(get(), inFlight, committed)
    }
    single { ImportTransactionsUseCase(database, modelPath) }
}
