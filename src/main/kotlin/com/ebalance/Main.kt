package com.ebalance

import com.ebalance.cli.HelloCommand
import com.github.ajalt.clikt.core.main

fun main(args: Array<String> = emptyArray()) {
    HelloCommand().main(args)
}