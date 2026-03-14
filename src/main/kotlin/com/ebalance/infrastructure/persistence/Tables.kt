package com.ebalance.infrastructure.persistence

import org.jetbrains.exposed.sql.Table

object TransactionsTable : Table("transactions") {
    val id = integer("id").autoIncrement()
    val operatedAt = text("operated_at")
    val description = text("description")
    val value = double("value")
    val balance = double("balance")
    val categoryId = integer("category_id").default(0)
    override val primaryKey = PrimaryKey(id)
}
