package com.ebalance

import io.ktor.client.*
import kotlinx.rpc.krpc.ktor.client.installKrpc

fun HttpClientConfig<*>.configureForProject() {
    installKrpc()
}
