package com.ebalance

import com.ebalance.investments.investmentModule
import com.ebalance.transactions.transactionModule
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.*
import org.flywaydb.core.Flyway
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureFrameworks() {
    // Read the DB path from application.yaml; fall back to relative path for local dev.
    // Gradle :server:run sets workingDir = backend/server/, so the default is ../../e-balance.db
    val dbPath = environment.config.propertyOrNull("database.path")?.getString()
        ?: "../../e-balance.db"

    val resolvedPath = java.io.File(dbPath).canonicalPath
    environment.log.info("Database path → $resolvedPath (exists: ${java.io.File(resolvedPath).exists()})")

    // Run Flyway migrations before any connection pool or use-case is wired up.
    // Migrations live in the root project's src/main/resources/db/migration directory.
    val migrationsPath = java.io.File(resolvedPath).parentFile
        .resolve("src/main/resources/db/migration")
        .canonicalPath
    environment.log.info("Running Flyway migrations from: $migrationsPath")
    Flyway.configure()
        .dataSource("jdbc:sqlite:$resolvedPath", null, null)
        .locations("filesystem:$migrationsPath")
        .load()
        .migrate()

    install(Koin) {
        slf4jLogger()
        modules(
            module {
                single<HelloService> {
                    HelloService {
                        println(environment.log.info("Hello, World!"))
                    }
                }
            },
            transactionModule(dbPath),
            investmentModule(dbPath)
        )
    }

    install(Krpc)
    routing {
        rpc("/api") {
            rpcConfig {
                serialization { json() }
            }
            registerService<SampleService> { SampleServiceImpl() }
        }
    }
}
