package com.ebalance.investments.domain

data class SectorProgress(
    val sector: String,
    val values: List<Double>
)

data class WalletProgress(
    val months: List<String>,
    val series: List<SectorProgress>
)
