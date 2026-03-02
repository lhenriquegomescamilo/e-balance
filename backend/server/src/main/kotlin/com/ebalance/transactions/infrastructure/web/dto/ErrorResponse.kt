package com.ebalance.transactions.infrastructure.web.dto

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val error: String,    // machine-readable code, e.g. "INVALID_DATE"
    val message: String   // human-readable description
)
