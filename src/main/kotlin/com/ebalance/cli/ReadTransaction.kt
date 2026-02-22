package com.ebalance.cli

import com.github.ajalt.clikt.core.CliktCommand

class ReadTransactions : CliktCommand() {
    override fun run() {
        echo("Reading transaction")
    }
}
