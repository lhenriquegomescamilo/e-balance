package com.ebalance.transactions.infrastructure.persistence

import org.jetbrains.exposed.sql.*

object TransactionsTable : Table("transactions") {
    val id = long("id").autoIncrement()
    val operatedAt = text("operated_at")
    val description = text("description")
    val value = double("value")
    val balance = double("balance")
    val categoryId = long("category_id").default(0L)
    override val primaryKey = PrimaryKey(id)
}

object CategoryTable : Table("category") {
    val id = long("id")
    val name = text("name")
    val enumName = text("enum_name")
    override val primaryKey = PrimaryKey(id)
}

class ToCharDate(
    val column: Expression<String>,
    val format: String
) : ExpressionWithColumnType<String>() {
    override val columnType: IColumnType<String> = TextColumnType()
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append("TO_CHAR(")
        queryBuilder.append(column)
        queryBuilder.append("::date, '")
        queryBuilder.append(format)
        queryBuilder.append("')")
    }
}
