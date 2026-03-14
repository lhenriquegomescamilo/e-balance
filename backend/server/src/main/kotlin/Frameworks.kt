package com.ebalance

import com.ebalance.investments.investmentModule
import com.ebalance.transactions.transactionModule
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.lettuce.core.RedisClient
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
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

    // PostgreSQL connection config: .env > system env > application.yaml
    val dbUrl = dotEnv["DATABASE_URL"]
        ?: System.getenv("DATABASE_URL")
        ?: environment.config.propertyOrNull("database.url")?.getString()
        ?: "jdbc:postgresql://localhost:5432/ebalance"
    val dbUsername = dotEnv["DATABASE_USERNAME"]
        ?: System.getenv("DATABASE_USERNAME")
        ?: environment.config.propertyOrNull("database.username")?.getString()
        ?: "ebalance"
    val dbPassword = dotEnv["DATABASE_PASSWORD"]
        ?: System.getenv("DATABASE_PASSWORD")
        ?: environment.config.propertyOrNull("database.password")?.getString()
        ?: "ebalance"

    environment.log.info("Database URL → $dbUrl")

    // SerpAPI key: .env > system env > application.yaml
    val serpApiKey = dotEnv["SERPAPI_API_KEY"]
        ?: System.getenv("SERPAPI_API_KEY")
        ?: environment.config.propertyOrNull("serpapi.apiKey")?.getString()
        ?: ""
    if (serpApiKey.isBlank()) environment.log.warn("serpapi.apiKey is not configured — portfolio progress chart will be empty")

    // Redis URL: .env > system env > application.yaml > default local
    val redisUrl = dotEnv["REDIS_URL"]
        ?: System.getenv("REDIS_URL")
        ?: environment.config.propertyOrNull("redis.url")?.getString()
        ?: "redis://localhost:6379"
    environment.log.info("Redis URL → $redisUrl")

    // Classifier model path: .env > system env > application.yaml
    val modelPath = dotEnv["CLASSIFIER_MODEL_PATH"]
        ?: System.getenv("CLASSIFIER_MODEL_PATH")
        ?: environment.config.propertyOrNull("classifier.modelPath")?.getString()
        ?: "nn-model.bin"
    environment.log.info("Classifier model path → $modelPath")

    // Create shared HikariCP connection pool
    val dataSource = HikariDataSource(HikariConfig().apply {
        jdbcUrl = dbUrl
        username = dbUsername
        password = dbPassword
        driverClassName = "org.postgresql.Driver"
        maximumPoolSize = 10
        minimumIdle = 2
    })

    // FLYWAY_ENABLED=false skips in-app migration (used in Docker where flyway-migrator
    // container runs migrations before this service starts).
    if (System.getenv("FLYWAY_ENABLED") != "false") {
        val localPath = java.io.File("src/main/resources/db/migration")
        val migrationsLocation = if (localPath.exists()) "filesystem:${localPath.canonicalPath}"
            else "classpath:db/migration"
        environment.log.info("Running Flyway migrations from $migrationsLocation")
        Flyway.configure()
            .dataSource(dataSource)
            .locations(migrationsLocation)
            .load()
            .migrate()
    } else {
        environment.log.info("Flyway in-app migration disabled (FLYWAY_ENABLED=false)")
    }

    val database = Database.connect(dataSource)

    val redisCommands = RedisClient.create(redisUrl)
        .connect()
        .sync()

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
            transactionModule(database, modelPath),
            investmentModule(database, serpApiKey, redisCommands)
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
