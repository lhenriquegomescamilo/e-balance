package com.ebalance.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class Transaction(
    private val operatedAt: LocalDate,
    private val description: String,
    private val value: BigDecimal,
    private val balance: BigDecimal
)