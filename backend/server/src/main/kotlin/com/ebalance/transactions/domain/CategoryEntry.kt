package com.ebalance.transactions.domain

/** Domain entity representing a single category record from the database. */
data class CategoryEntry(
    val id: Long,
    val name: String,
    val enumName: String
)
