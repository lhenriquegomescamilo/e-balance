package com.ebalance

import com.ebalance.cli.ImportCommand
import com.ebalance.cli.InitCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands

fun main(args: Array<String> = emptyArray()) {
    InitCommand()
        .subcommands(ImportCommand())
        .main(args)
}