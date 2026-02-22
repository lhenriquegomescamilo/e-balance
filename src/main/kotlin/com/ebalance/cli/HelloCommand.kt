package com.ebalance.cli

import com.github.ajalt.clikt.core.CliktCommand

class HelloCommand : CliktCommand() {
    override fun run() {
        echo("Hello World!")
    }
}