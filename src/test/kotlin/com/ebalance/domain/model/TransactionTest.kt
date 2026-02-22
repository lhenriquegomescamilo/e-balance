package com.ebalance.domain.model

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.LocalDate

class TransactionTest : DescribeSpec({
    
    describe("Transaction data class") {
        
        it("should create a transaction with all fields") {
            val transaction = Transaction(
                operatedAt = LocalDate.of(2026, 2, 22),
                description = "Test Transaction",
                value = BigDecimal("-50.00"),
                balance = BigDecimal("1000.00")
            )
            
            transaction.operatedAt shouldBe LocalDate.of(2026, 2, 22)
            transaction.description shouldBe "Test Transaction"
            transaction.value shouldBe BigDecimal("-50.00")
            transaction.balance shouldBe BigDecimal("1000.00")
        }
        
        it("should support negative values for expenses") {
            val expense = Transaction(
                operatedAt = LocalDate.now(),
                description = "Coffee",
                value = BigDecimal("-5.50"),
                balance = BigDecimal("100.00")
            )
            
            expense.value shouldBe BigDecimal("-5.50")
        }
        
        it("should support positive values for income") {
            val income = Transaction(
                operatedAt = LocalDate.now(),
                description = "Salary",
                value = BigDecimal("3000.00"),
                balance = BigDecimal("3500.00")
            )
            
            income.value shouldBe BigDecimal("3000.00")
        }
        
        it("should be a data class with equals and copy") {
            val original = Transaction(
                operatedAt = LocalDate.of(2026, 2, 22),
                description = "Test",
                value = BigDecimal("100.00"),
                balance = BigDecimal("500.00")
            )
            
            val copied = original.copy(description = "Modified")
            
            copied.description shouldBe "Modified"
            copied.operatedAt shouldBe original.operatedAt
            copied.value shouldBe original.value
            copied.balance shouldBe original.balance
        }
    }
})