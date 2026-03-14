package com.ebalance.infrastructure.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

data class DbConfig(
    val url: String,
    val username: String,
    val password: String
)

object DatabaseFactory {

    fun createDataSource(config: DbConfig): HikariDataSource =
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = config.url
            username = config.username
            password = config.password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 5
            minimumIdle = 1
        })

    fun initialize(config: DbConfig): Database {
        val dataSource = createDataSource(config)
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
        return Database.connect(dataSource)
    }
}
