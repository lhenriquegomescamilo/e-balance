package com.ebalance

import com.ebalance.investments.application.GetStockPriceHistoryUseCase
import com.ebalance.investments.application.GetWalletHoldingsUseCase
import com.ebalance.investments.application.GetWalletProgressUseCase
import com.ebalance.investments.application.GetWalletSummaryUseCase
import com.ebalance.investments.application.UpsertInvestmentAssetUseCase
import com.ebalance.investments.infrastructure.web.investmentRoutes
import com.ebalance.transactions.application.GetCategoriesUseCase
import com.ebalance.transactions.application.GetMonthlySummaryUseCase
import com.ebalance.transactions.application.GetTransactionSummaryUseCase
import com.ebalance.transactions.application.GetTransactionsUseCase
import com.ebalance.transactions.application.UpdateTransactionCategoryUseCase
import com.ebalance.transactions.infrastructure.web.transactionRoutes
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    // Transactions use cases
    val summaryUseCase: GetTransactionSummaryUseCase by inject()
    val transactionsUseCase: GetTransactionsUseCase by inject()
    val categoriesUseCase: GetCategoriesUseCase by inject()
    val monthlySummaryUseCase: GetMonthlySummaryUseCase by inject()
    val updateCategoryUseCase: UpdateTransactionCategoryUseCase by inject()

    // Investments use cases
    val walletSummaryUseCase: GetWalletSummaryUseCase by inject()
    val walletHoldingsUseCase: GetWalletHoldingsUseCase by inject()
    val walletProgressUseCase: GetWalletProgressUseCase by inject()
    val upsertAssetUseCase: UpsertInvestmentAssetUseCase by inject()
    val stockPriceHistoryUseCase: GetStockPriceHistoryUseCase by inject()

    routing {
        // Redirect root to the dashboard
        get("/") { call.respondRedirect("/static/index.html") }

        // Serve the static dashboard at /static/*
        staticResources("/static", "static")

        // REST API — all endpoints live under /api/v1
        route("/api/v1") {
            transactionRoutes(summaryUseCase, transactionsUseCase, categoriesUseCase, monthlySummaryUseCase, updateCategoryUseCase)
            investmentRoutes(walletSummaryUseCase, walletHoldingsUseCase, walletProgressUseCase, upsertAssetUseCase, stockPriceHistoryUseCase)
        }
    }
}
