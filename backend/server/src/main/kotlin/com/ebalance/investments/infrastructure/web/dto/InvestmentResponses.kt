package com.ebalance.investments.infrastructure.web.dto

import kotlinx.serialization.Serializable

// ── /investments/summary ─────────────────────────────────────────────────────

@Serializable
data class WalletSummaryStatsDto(
    val totalInvested: Double,
    val totalCurrentValue: Double,
    val totalPnl: Double,
    val roi: Double
)

@Serializable
data class SectorSummaryDto(
    val name: String,
    val invested: Double,
    val currentValue: Double,
    val pnl: Double,
    val roi: Double,
    val percentage: Double
)

@Serializable
data class WalletSummaryResponse(
    val summary: WalletSummaryStatsDto,
    val sectors: List<SectorSummaryDto>
)

// ── /investments/holdings ────────────────────────────────────────────────────

@Serializable
data class InvestmentAssetDto(
    val id: Int,
    val ticker: String,
    val name: String,
    val sector: String,
    val exchange: String,
    val invested: Double,
    val currentValue: Double,
    val pnl: Double,
    val roi: Double
)

@Serializable
data class WalletHoldingsResponse(
    val holdings: List<InvestmentAssetDto>,
    val total: Int
)

// ── /investments/assets/{ticker} (PUT) ───────────────────────────────────────

@Serializable
data class UpsertAssetRequest(
    val name: String,
    val sector: String,
    val exchange: String,
    val investedAmount: Double,
    val currentValue: Double
)

// ── /investments/progress ────────────────────────────────────────────────────

@Serializable
data class SectorProgressDto(
    val sector: String,
    val values: List<Double>
)

@Serializable
data class WalletProgressResponse(
    val months: List<String>,
    val series: List<SectorProgressDto>
)
