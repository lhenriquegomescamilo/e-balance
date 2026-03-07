package com.ebalance.investments.infrastructure.persistence

import org.jetbrains.exposed.sql.Table

object InvestmentAssetTable : Table("investment_asset") {
    val id = integer("id").autoIncrement()
    val ticker = text("ticker").uniqueIndex()
    val name = text("name")
    val sector = text("sector")
    val exchange = text("exchange").default("NASDAQ")
    val investedAmount = double("invested_amount").default(0.0)
    val currentValue = double("current_value").default(0.0)
    val notes = text("notes").nullable()
    val createdAt = text("created_at")
    val updatedAt = text("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object SectorSnapshotTable : Table("investment_sector_snapshot") {
    val id = integer("id").autoIncrement()
    val sectorName = text("sector_name")
    val monthYear = text("month_year")
    val totalValue = double("total_value").default(0.0)
    override val primaryKey = PrimaryKey(id)
}
