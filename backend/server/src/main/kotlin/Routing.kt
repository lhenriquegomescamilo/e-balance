package com.ebalance

import com.ebalance.transactions.application.GetCategoriesUseCase
import com.ebalance.transactions.application.GetMonthlySummaryUseCase
import com.ebalance.transactions.application.GetTransactionSummaryUseCase
import com.ebalance.transactions.application.GetTransactionsUseCase
import com.ebalance.transactions.infrastructure.web.transactionRoutes
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    // Resolve use-case singletons from Koin (configured in Frameworks.kt)
    val summaryUseCase: GetTransactionSummaryUseCase by inject()
    val transactionsUseCase: GetTransactionsUseCase by inject()
    val categoriesUseCase: GetCategoriesUseCase by inject()
    val monthlySummaryUseCase: GetMonthlySummaryUseCase by inject()

    routing {
        // Redirect root to the dashboard
        get("/") { call.respondRedirect("/static/index.html") }

        // Serve the static dashboard at /static/*
        staticResources("/static", "static")

        // REST API — all endpoints live under /api/v1
        route("/api/v1") {
            transactionRoutes(summaryUseCase, transactionsUseCase, categoriesUseCase, monthlySummaryUseCase)
        }
    }
}
