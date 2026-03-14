package com.ebalance.investments.domain

data class StockPriceHistory(
    val ticker: String,
    val name: String,
    val exchange: String,
    val sector: String,
    val currentPrice: Double,
    val changePct: Double,       // % change from first to current price in the selected window
    val investedAmount: Double,  // cost basis for this position
    val currentValue: Double,    // mark-to-market value
    val pnl: Double,             // unrealized gain/loss (currentValue - investedAmount)
    val roi: Double,             // return on investment (%)
    val qty: Double,             // estimated shares held: currentValue / currentPrice (0 if currentPrice ≤ 0)
    val months: List<String>,    // "Jan 2025", "Feb 2025", …
    val prices: List<Double>     // monthly closing prices aligned with months
)
