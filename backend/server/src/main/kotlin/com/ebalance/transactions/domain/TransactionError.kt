package com.ebalance.transactions.domain

sealed interface TransactionError {
    val message: String
    data class InvalidDate(val input: String, override val message: String) : TransactionError
    data class InvalidParameter(override val message: String) : TransactionError
    data class NotFound(override val message: String) : TransactionError
    data class DatabaseError(override val message: String, val cause: Throwable) : TransactionError
}
