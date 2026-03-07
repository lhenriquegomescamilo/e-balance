package com.ebalance.investments.infrastructure.persistence

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ebalance.investments.domain.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class InvestmentRepositoryImpl(private val database: Database) : InvestmentRepository {

    // ── getAssets ─────────────────────────────────────────────────────────────
    override fun getAssets(): Either<InvestmentError.DatabaseError, List<InvestmentAsset>> =
        runCatching {
            transaction(database) {
                InvestmentAssetTable.selectAll()
                    .orderBy(InvestmentAssetTable.sector to SortOrder.ASC, InvestmentAssetTable.ticker to SortOrder.ASC)
                    .map { row ->
                        InvestmentAsset(
                            id             = row[InvestmentAssetTable.id],
                            ticker         = row[InvestmentAssetTable.ticker],
                            name           = row[InvestmentAssetTable.name],
                            sector         = row[InvestmentAssetTable.sector],
                            exchange       = row[InvestmentAssetTable.exchange],
                            investedAmount = row[InvestmentAssetTable.investedAmount],
                            currentValue   = row[InvestmentAssetTable.currentValue]
                        )
                    }
            }
        }.fold(
            onSuccess = { it.right() },
            onFailure = { e -> InvestmentError.DatabaseError("Failed to load investment assets", e).left() }
        )

    // ── upsertAsset ───────────────────────────────────────────────────────────
    override fun upsertAsset(
        ticker: String, name: String, sector: String, exchange: String,
        investedAmount: Double, currentValue: Double
    ): Either<InvestmentError.DatabaseError, Unit> =
        runCatching {
            transaction(database) {
                val now = LocalDate.now().toString()
                InvestmentAssetTable.upsert(
                    InvestmentAssetTable.ticker,
                    onUpdateExclude = listOf(
                        InvestmentAssetTable.id,
                        InvestmentAssetTable.createdAt,
                        InvestmentAssetTable.updatedAt,
                        InvestmentAssetTable.notes
                    )
                ) { stmt ->
                    stmt[InvestmentAssetTable.ticker] = ticker.uppercase()
                    stmt[InvestmentAssetTable.name] = name
                    stmt[InvestmentAssetTable.sector] = sector
                    stmt[InvestmentAssetTable.exchange] = exchange.uppercase()
                    stmt[InvestmentAssetTable.investedAmount] = investedAmount
                    stmt[InvestmentAssetTable.currentValue] = currentValue
                    stmt[InvestmentAssetTable.notes] = null
                    stmt[InvestmentAssetTable.createdAt] = now
                    stmt[InvestmentAssetTable.updatedAt] = now
                }
                Unit
            }
        }.fold(
            onSuccess = { Unit.right() },
            onFailure = { e -> InvestmentError.DatabaseError("Failed to upsert asset '$ticker'", e).left() }
        )

    // ── getSectorSnapshots ────────────────────────────────────────────────────
    override fun getSectorSnapshots(limitMonths: Int): Either<InvestmentError.DatabaseError, WalletProgress> =
        runCatching {
            val displayFmt = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

            transaction(database) {
                val subQuery = SectorSnapshotTable
                    .select(SectorSnapshotTable.monthYear)
                    .groupBy(SectorSnapshotTable.monthYear)
                    .orderBy(SectorSnapshotTable.monthYear to SortOrder.DESC)
                    .limit(limitMonths)

                data class Row(val sector: String, val monthYear: String, val value: Double)

                val rows = SectorSnapshotTable
                    .select(SectorSnapshotTable.sectorName, SectorSnapshotTable.monthYear, SectorSnapshotTable.totalValue)
                    .where { SectorSnapshotTable.monthYear inSubQuery subQuery }
                    .orderBy(SectorSnapshotTable.monthYear to SortOrder.ASC, SectorSnapshotTable.sectorName to SortOrder.ASC)
                    .map { row ->
                        Row(
                            sector    = row[SectorSnapshotTable.sectorName],
                            monthYear = row[SectorSnapshotTable.monthYear],
                            value     = row[SectorSnapshotTable.totalValue]
                        )
                    }

                val months  = rows.map { it.monthYear }.distinct().sorted()
                val sectors = rows.map { it.sector }.distinct().sorted()
                val lookup  = rows.associateBy { "${it.sector}|${it.monthYear}" }

                val monthLabels = months.map { m -> YearMonth.parse(m).format(displayFmt) }

                val series = sectors.map { sector ->
                    SectorProgress(
                        sector = sector,
                        values = months.map { m -> lookup["$sector|$m"]?.value ?: 0.0 }
                    )
                }

                WalletProgress(months = monthLabels, series = series)
            }
        }.fold(
            onSuccess = { it.right() },
            onFailure = { e -> InvestmentError.DatabaseError("Failed to load sector snapshots", e).left() }
        )
}
