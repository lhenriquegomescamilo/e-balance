package com.ebalance

import com.ebalance.cli.ReadTransactions
import com.github.ajalt.clikt.core.main

fun main(args: Array<String> = emptyArray()) {
    ReadTransactions().main(args)
}