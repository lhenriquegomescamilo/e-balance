package com.ebalance.investments.infrastructure.web

import com.ebalance.investments.application.GetWalletHoldingsUseCase
import com.ebalance.investments.application.GetWalletProgressUseCase
import com.ebalance.investments.application.GetWalletSummaryUseCase
import com.ebalance.investments.application.UpsertInvestmentAssetUseCase
import com.ebalance.investments.domain.InvestmentAsset
import com.ebalance.investments.domain.InvestmentError
import com.ebalance.investments.infrastructure.web.dto.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Registers all investment-related REST endpoints under the calling [Route].
 *
 * Endpoints:
 *   GET /investments/summary           — total stats + sector breakdown
 *   GET /investments/holdings          — individual positions
 *   GET /investments/progress?period=  — monthly sector value (3m | 6m | 12m)
 */
fun Route.investmentRoutes(
    summaryUseCase: GetWalletSummaryUseCase,
    holdingsUseCase: GetWalletHoldingsUseCase,
    progressUseCase: GetWalletProgressUseCase,
    upsertUseCase: UpsertInvestmentAssetUseCase
) {

    // ── GET /api/v1/investments/summary ───────────────────────────────────────
    get("/investments/summary") {
        summaryUseCase.execute().fold(
            ifLeft  = { call.respondInvestmentError(it) },
            ifRight = { summary ->
                call.respond(
                    HttpStatusCode.OK,
                    WalletSummaryResponse(
                        summary = WalletSummaryStatsDto(
                            totalInvested     = summary.totalInvested,
                            totalCurrentValue = summary.totalCurrentValue,
                            totalPnl          = summary.totalPnl,
                            roi               = summary.roi
                        ),
                        sectors = summary.sectors.map { s ->
                            SectorSummaryDto(
                                name         = s.name,
                                invested     = s.invested,
                                currentValue = s.currentValue,
                                pnl          = s.pnl,
                                roi          = s.roi,
                                percentage   = s.percentage
                            )
                        }
                    )
                )
            }
        )
    }

    // ── GET /api/v1/investments/holdings ──────────────────────────────────────
    get("/investments/holdings") {
        holdingsUseCase.execute().fold(
            ifLeft  = { call.respondInvestmentError(it) },
            ifRight = { assets ->
                call.respond(
                    HttpStatusCode.OK,
                    WalletHoldingsResponse(
                        holdings = assets.map { it.toDto() },
                        total    = assets.size
                    )
                )
            }
        )
    }

    // ── GET /api/v1/investments/progress?period=6m ────────────────────────────
    // period: "3m" | "6m" (default) | "12m"
    get("/investments/progress") {
        val period = call.request.queryParameters["period"]?.lowercase() ?: "6m"
        progressUseCase.execute(period).fold(
            ifLeft  = { call.respondInvestmentError(it) },
            ifRight = { progress ->
                call.respond(
                    HttpStatusCode.OK,
                    WalletProgressResponse(
                        months = progress.months,
                        series = progress.series.map { s ->
                            SectorProgressDto(sector = s.sector, values = s.values)
                        }
                    )
                )
            }
        )
    }

    // ── PUT /api/v1/investments/assets/{ticker} ───────────────────────────────
    put("/investments/assets/{ticker}") {
        val ticker = call.parameters["ticker"]?.uppercase()
            ?: return@put call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "INVALID_PARAMETER", "message" to "Missing ticker")
            )
        val body = call.receive<UpsertAssetRequest>()
        upsertUseCase.execute(ticker, body.name, body.sector, body.investedAmount, body.currentValue).fold(
            ifLeft  = { call.respondInvestmentError(it) },
            ifRight = { call.respond(HttpStatusCode.OK, mapOf("ticker" to ticker, "message" to "Saved")) }
        )
    }
}

// ── Extension: InvestmentAsset → DTO ─────────────────────────────────────────
private fun InvestmentAsset.toDto() = InvestmentAssetDto(
    id           = id,
    ticker       = ticker,
    name         = name,
    sector       = sector,
    invested     = investedAmount,
    currentValue = currentValue,
    pnl          = pnl,
    roi          = roi
)

// ── Extension: respond with the appropriate HTTP error ───────────────────────
private suspend fun ApplicationCall.respondInvestmentError(error: InvestmentError) =
    when (error) {
        is InvestmentError.DatabaseError    -> {
            application.environment.log.error("Investment database error: ${error.message}", error.cause)
            respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "INTERNAL_ERROR", "message" to "An unexpected error occurred")
            )
        }
        is InvestmentError.NotFound         ->
            respond(HttpStatusCode.NotFound,
                mapOf("error" to "NOT_FOUND", "message" to error.message))
        is InvestmentError.InvalidParameter ->
            respond(HttpStatusCode.BadRequest,
                mapOf("error" to "INVALID_PARAMETER", "message" to error.message))
    }
