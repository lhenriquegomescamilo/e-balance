package com.ebalance.transactions.domain

import java.math.BigDecimal
import java.time.LocalDate

/** Domain entity representing a single transaction row. */
data class TransactionRow(
    val id: Long,
    val operatedAt: LocalDate,
    val description: String,
    val value: BigDecimal,       // positive = income, negative = expense
    val balance: BigDecimal,
    val categoryId: Long,
    val categoryName: String
)
