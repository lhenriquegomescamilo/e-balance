package com.ebalance.investments.domain

data class InvestmentAsset(
    val id: Int,
    val ticker: String,
    val name: String,
    val sector: String,
    val exchange: String,
    val investedAmount: Double,
    val currentValue: Double,
    /** ISO-8601 date string (YYYY-MM-DD) of when the position was opened. Null if not recorded. */
    val purchasedAt: String?
) {
    val pnl: Double get() = currentValue - investedAmount
    val roi: Double get() = if (investedAmount > 0.0) (pnl / investedAmount) * 100.0 else 0.0
}
