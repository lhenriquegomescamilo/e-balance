package com.ebalance

import com.ebalance.cli.InitCommand
import com.github.ajalt.clikt.core.main

fun main(args: Array<String> = emptyArray()) {
    InitCommand().main(args)
}