package com.ebalance.transactions.domain

/** Paginated result returned by [TransactionRepository.getTransactions]. */
data class TransactionPage(
    val rows: List<TransactionRow>,
    val total: Int,       // total rows matching the filter (before pagination)
    val page: Int,
    val pageSize: Int,
    val totalPages: Int
)
