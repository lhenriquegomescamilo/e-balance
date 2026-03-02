package com.ebalance.transactions.domain

import java.time.LocalDate

/**
 * Value object representing filter criteria for transaction queries.
 * [categoryIds] empty = all categories.
 * [page] and [pageSize] are only used by getTransactions(); other queries ignore them.
 */
data class TransactionFilter(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val categoryIds: List<Long> = emptyList(),
    val type: TransactionType = TransactionType.ALL,
    val page: Int = 1,
    val pageSize: Int = 20
)

enum class TransactionType { ALL, INCOME, EXPENSE }
