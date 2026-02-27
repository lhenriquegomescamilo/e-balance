package com.ebalance.transactions.infrastructure.web.dto

import kotlinx.serialization.Serializable

// ── Summary endpoint (/api/v1/transactions/summary) ──────────────────────────

@Serializable
data class SummaryStatsDto(
    val totalIncome: Double,
    val totalExpenses: Double,
    val netBalance: Double,
    val transactionCount: Int
)

@Serializable
data class CategorySummaryDto(
    val categoryId: Long,
    val categoryName: String,
    val totalIncome: Double,
    val totalExpenses: Double,
    val transactionCount: Int,
    val percentageOfExpenses: Double   // pre-calculated server-side for chart labels
)

@Serializable
data class AppliedFiltersDto(
    val startDate: String,
    val endDate: String,
    val categories: List<Long>
)

@Serializable
data class TransactionSummaryResponse(
    val summary: SummaryStatsDto,
    val categories: List<CategorySummaryDto>,
    val filters: AppliedFiltersDto
)

// ── Transaction list endpoint (/api/v1/transactions) ─────────────────────────

@Serializable
data class TransactionDto(
    val id: Long,
    val operatedAt: String,       // ISO-8601 date string
    val description: String,
    val value: Double,            // signed: positive = income, negative = expense
    val balance: Double,
    val categoryId: Long,
    val categoryName: String,
    val type: String              // "INCOME" | "EXPENSE"
)

@Serializable
data class TransactionListResponse(
    val transactions: List<TransactionDto>,
    val total: Int
)

// ── Monthly-by-category endpoint (/api/v1/transactions/monthly-by-category) ──

@Serializable
data class MonthlyCategoryDataDto(
    val monthYear: String,          // "YYYY-MM"
    val totalIncome: Double,
    val totalExpenses: Double,
    val transactionCount: Int
)

@Serializable
data class MonthlyCategorySeriesDto(
    val categoryId: Long,
    val categoryName: String,
    val monthlyData: List<MonthlyCategoryDataDto>   // zero-filled, one entry per month
)

@Serializable
data class MonthlySummaryResponse(
    val months: List<String>,                       // sorted "YYYY-MM" list
    val series: List<MonthlyCategorySeriesDto>,     // sorted by total expenses desc
    val filters: AppliedFiltersDto
)

// ── Categories endpoint (/api/v1/categories) ─────────────────────────────────

@Serializable
data class CategoryDto(
    val id: Long,
    val name: String,
    val enumName: String
)

@Serializable
data class CategoryListResponse(
    val categories: List<CategoryDto>
)
