package com.ebalance.investments.domain

sealed interface InvestmentError {
    val message: String

    data class DatabaseError(
        override val message: String,
        val cause: Throwable? = null
    ) : InvestmentError

    data class NotFound(override val message: String) : InvestmentError

    data class InvalidParameter(override val message: String) : InvestmentError
}
