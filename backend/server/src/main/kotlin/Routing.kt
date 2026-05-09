package com.ebalance

import com.ebalance.investments.application.GetStockPriceHistoryUseCase
import com.ebalance.investments.application.GetWalletHoldingsUseCase
import com.ebalance.investments.application.GetWalletProgressUseCase
import com.ebalance.investments.application.GetWalletSummaryUseCase
import com.ebalance.investments.application.UpsertInvestmentAssetUseCase
import com.ebalance.investments.application.ValidateStockUseCase
import com.ebalance.investments.infrastructure.web.investmentRoutes
import com.ebalance.transactions.application.GetCategoriesUseCase
import com.ebalance.transactions.application.GetMonthlySummaryUseCase
import com.ebalance.transactions.application.GetTransactionSummaryUseCase
import com.ebalance.transactions.application.GetTransactionsUseCase
import com.ebalance.transactions.application.ImportTransactionsUseCase
import com.ebalance.transactions.application.UpdateTransactionCategoryUseCase
import com.ebalance.transactions.infrastructure.web.transactionRoutes
import com.expediagroup.graphql.server.ktor.graphQLGetRoute
import com.expediagroup.graphql.server.ktor.graphQLPostRoute
import com.expediagroup.graphql.server.ktor.graphQLSDLRoute
import com.expediagroup.graphql.server.ktor.graphiQLRoute
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
    val importUseCase: ImportTransactionsUseCase by inject()

    // Investments use cases
    val walletSummaryUseCase: GetWalletSummaryUseCase by inject()
    val walletHoldingsUseCase: GetWalletHoldingsUseCase by inject()
    val walletProgressUseCase: GetWalletProgressUseCase by inject()
    val upsertAssetUseCase: UpsertInvestmentAssetUseCase by inject()
    val stockPriceHistoryUseCase: GetStockPriceHistoryUseCase by inject()
    val validateStockUseCase: ValidateStockUseCase by inject()

    routing {
        // Redirect root to the dashboard
        get("/") { call.respondRedirect("/static/index.html") }

        // Serve the static dashboard at /static/*
        staticResources("/static", "static")

        // GraphQL — preferred API surface going forward.
        graphQLGetRoute()
        graphQLPostRoute()
        graphQLSDLRoute()    // GET /sdl  → schema definition language
        graphiQLRoute()      // GET /graphiql → in-browser explorer

        // REST API — DEPRECATED, retained for existing clients during migration.
        // Every response under /api/v1 advertises the deprecation via
        // RFC 8594 Deprecation + Link("successor-version") headers so clients
        // can detect it without calling each endpoint individually.
        route("/api/v1") {
            install(createRouteScopedPlugin("DeprecatedApiHeaders") {
                onCallRespond { call ->
                    call.response.headers.append("Deprecation", "true", safeOnly = false)
                    call.response.headers.append(
                        "Link",
                        "</graphql>; rel=\"successor-version\"",
                        safeOnly = false
                    )
                }
            })
            transactionRoutes(summaryUseCase, transactionsUseCase, categoriesUseCase, monthlySummaryUseCase, updateCategoryUseCase, importUseCase)
            investmentRoutes(walletSummaryUseCase, walletHoldingsUseCase, walletProgressUseCase, upsertAssetUseCase, stockPriceHistoryUseCase, validateStockUseCase)
        }
    }
}
