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

/**
 * Reads a .env file from the given directory (or ancestors) and returns its KEY=VALUE pairs.
 * Lines starting with '#' and blank lines are ignored. Inline comments are stripped.
 * Quoted values (single or double) are unquoted.
 */
private fun loadDotEnv(): Pair<java.io.File?, Map<String, String>> {
    val candidates = listOf(
        java.io.File(".env"),           // working dir (backend/server/ when run via Gradle)
        java.io.File("../../.env"),     // project root
        java.io.File("../.env"),        // backend/
    )
    val envFile = candidates.firstOrNull { it.exists() } ?: return null to emptyMap()
    val map = envFile.readLines()
        .filter { it.isNotBlank() && !it.trimStart().startsWith('#') }
        .mapNotNull { line ->
            val eqIdx = line.indexOf('=')
            if (eqIdx < 1) return@mapNotNull null
            val key = line.substring(0, eqIdx).trim()
            var value = line.substring(eqIdx + 1).trim()
            // Strip inline comment
            value = value.substringBefore(" #").substringBefore("\t#").trim()
            // Strip surrounding quotes
            if ((value.startsWith('"') && value.endsWith('"')) ||
                (value.startsWith('\'') && value.endsWith('\''))) {
                value = value.substring(1, value.length - 1)
            }
            key to value
        }
        .toMap()
    return envFile to map
}

fun Application.configureFrameworks() {
    val (envFile, dotEnv) = loadDotEnv()
    if (envFile != null) {
        environment.log.info("Loaded ${dotEnv.size} key(s) from ${envFile.canonicalPath}")
    } else {
        environment.log.info("No .env file found — relying on system environment and application.yaml")
    }

    // Read the DB path from application.yaml; fall back to relative path for local dev.
    // Gradle :server:run sets workingDir = backend/server/, so the default is ../../e-balance.db
    val dbPath = environment.config.propertyOrNull("database.path")?.getString()
        ?: "../../e-balance.db"

    val resolvedPath = java.io.File(dbPath).canonicalPath
    environment.log.info("Database path → $resolvedPath (exists: ${java.io.File(resolvedPath).exists()})")

    // SerpAPI key: .env > system env > application.yaml
    val serpApiKey = dotEnv["SERPAPI_API_KEY"]
        ?: System.getenv("SERPAPI_API_KEY")
        ?: environment.config.propertyOrNull("serpapi.apiKey")?.getString()
        ?: ""
    if (serpApiKey.isBlank()) environment.log.warn("serpapi.apiKey is not configured — portfolio progress chart will be empty")

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
            investmentModule(dbPath, serpApiKey)
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
