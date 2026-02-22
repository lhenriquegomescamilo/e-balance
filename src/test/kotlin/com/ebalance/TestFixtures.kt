package com.ebalance

import com.ebalance.domain.model.Transaction
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Test fixtures for creating test data.
 */
object TestFixtures {
    
    fun aTransaction(
        operatedAt: LocalDate = LocalDate.of(2026, 2, 22),
        description: String = "Test Transaction",
        value: BigDecimal = BigDecimal("-50.00"),
        balance: BigDecimal = BigDecimal("1000.00")
    ): Transaction = Transaction(
        operatedAt = operatedAt,
        description = description,
        value = value,
        balance = balance
    )
    
    fun aListOfTransactions(count: Int = 5): List<Transaction> = (1..count).map { index ->
        aTransaction(
            operatedAt = LocalDate.of(2026, 2, 22).minusDays(index.toLong()),
            description = "Transaction $index",
            value = BigDecimal("-${index * 10}"),
            balance = BigDecimal("1000.00") - BigDecimal(index * 10)
        )
    }
    
    val sampleTransactions = listOf(
        Transaction(
            operatedAt = LocalDate.of(2026, 2, 23),
            description = "Repsol E0394",
            value = BigDecimal("-75.01"),
            balance = BigDecimal("578.96")
        ),
        Transaction(
            operatedAt = LocalDate.of(2026, 2, 23),
            description = "Farmacia Dias",
            value = BigDecimal("-39.09"),
            balance = BigDecimal("653.97")
        ),
        Transaction(
            operatedAt = LocalDate.of(2026, 2, 19),
            description = "Transferência de Softdraft",
            value = BigDecimal("700.00"),
            balance = BigDecimal("703.86")
        )
    )
}