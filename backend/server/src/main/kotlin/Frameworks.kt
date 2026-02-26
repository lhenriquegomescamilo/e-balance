package com.ebalance

import com.ebalance.transactions.transactionModule
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.*
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
            transactionModule(dbPath)
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
