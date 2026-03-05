package com.ebalance.investments.infrastructure.persistence

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ebalance.investments.domain.*
import java.sql.DriverManager
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * SQLite adapter implementing [InvestmentRepository].
 *
 * Schema:
 *   investment_asset(id, ticker, name, sector, invested_amount, current_value, ...)
 *   investment_sector_snapshot(id, sector_name, month_year TEXT 'YYYY-MM', total_value)
 *
 * A new connection is opened per call (same pattern as TransactionRepositoryImpl).
 */
class InvestmentRepositoryImpl(private val dbPath: String) : InvestmentRepository {

    private fun connection() = DriverManager.getConnection("jdbc:sqlite:$dbPath")

    // ── getAssets ─────────────────────────────────────────────────────────────
    override fun getAssets(): Either<InvestmentError.DatabaseError, List<InvestmentAsset>> =
        runCatching {
            connection().use { conn ->
                conn.prepareStatement(
                    """
                    SELECT id, ticker, name, sector, exchange, invested_amount, current_value
                    FROM   investment_asset
                    ORDER  BY sector, ticker
                    """.trimIndent()
                ).use { stmt ->
                    val list = mutableListOf<InvestmentAsset>()
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            list += InvestmentAsset(
                                id             = rs.getInt("id"),
                                ticker         = rs.getString("ticker"),
                                name           = rs.getString("name"),
                                sector         = rs.getString("sector"),
                                exchange       = rs.getString("exchange") ?: "NASDAQ",
                                investedAmount = rs.getDouble("invested_amount"),
                                currentValue   = rs.getDouble("current_value")
                            )
                        }
                    }
                    list
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
            connection().use { conn ->
                conn.prepareStatement(
                    """
                    INSERT INTO investment_asset (ticker, name, sector, exchange, invested_amount, current_value)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT(ticker) DO UPDATE SET
                        name            = excluded.name,
                        sector          = excluded.sector,
                        exchange        = excluded.exchange,
                        invested_amount = excluded.invested_amount,
                        current_value   = excluded.current_value
                    """.trimIndent()
                ).use { stmt ->
                    stmt.setString(1, ticker.uppercase())
                    stmt.setString(2, name)
                    stmt.setString(3, sector)
                    stmt.setString(4, exchange.uppercase())
                    stmt.setDouble(5, investedAmount)
                    stmt.setDouble(6, currentValue)
                    stmt.executeUpdate()
                }
            }
        }.fold(
            onSuccess = { Unit.right() },
            onFailure = { e -> InvestmentError.DatabaseError("Failed to upsert asset '$ticker'", e).left() }
        )

    // ── getSectorSnapshots ────────────────────────────────────────────────────
    override fun getSectorSnapshots(limitMonths: Int): Either<InvestmentError.DatabaseError, WalletProgress> =
        runCatching {
            val displayFmt = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

            connection().use { conn ->
                // Fetch the N most recent months, then order chronologically
                val sql = """
                    SELECT sector_name, month_year, total_value
                    FROM   investment_sector_snapshot
                    WHERE  month_year IN (
                        SELECT DISTINCT month_year
                        FROM   investment_sector_snapshot
                        ORDER  BY month_year DESC
                        LIMIT  ?
                    )
                    ORDER  BY month_year ASC, sector_name ASC
                """.trimIndent()

                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, limitMonths)

                    data class Row(val sector: String, val monthYear: String, val value: Double)

                    val rows = mutableListOf<Row>()
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            rows += Row(
                                sector    = rs.getString("sector_name"),
                                monthYear = rs.getString("month_year"),
                                value     = rs.getDouble("total_value")
                            )
                        }
                    }

                    val months  = rows.map { it.monthYear }.distinct().sorted()
                    val sectors = rows.map { it.sector }.distinct().sorted()
                    val lookup  = rows.associateBy { "${it.sector}|${it.monthYear}" }

                    val monthLabels = months.map { m ->
                        YearMonth.parse(m).format(displayFmt)   // "Oct 2025"
                    }

                    val series = sectors.map { sector ->
                        SectorProgress(
                            sector = sector,
                            values = months.map { m -> lookup["$sector|$m"]?.value ?: 0.0 }
                        )
                    }

                    WalletProgress(months = monthLabels, series = series)
                }
            }
        }.fold(
            onSuccess = { it.right() },
            onFailure = { e -> InvestmentError.DatabaseError("Failed to load sector snapshots", e).left() }
        )
}
