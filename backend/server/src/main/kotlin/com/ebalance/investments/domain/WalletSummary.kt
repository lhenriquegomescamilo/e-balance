package com.ebalance.investments.domain

data class SectorSummary(
    val name: String,
    val invested: Double,
    val currentValue: Double,
    val pnl: Double,
    val roi: Double,
    val percentage: Double
)

data class WalletSummary(
    val totalInvested: Double,
    val totalCurrentValue: Double,
    val totalPnl: Double,
    val roi: Double,
    val sectors: List<SectorSummary>
)
