package com.ebalance.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class Transaction(
    val operatedAt: LocalDate,
    val description: String,
    val value: BigDecimal,
    val balance: BigDecimal
)