package com.ebalance.classification

import kotlin.times


fun main() {
    val classifier = TextClassifier()
    println("Training...")
    val trainingData = listOf(
        "Moneyflow, Lda" to "ACCOUNTANT",
        "Moneyflow, Lda" to "ACCOUNTANT",
        "Moneyflow" to "ACCOUNTANT",
        "McDonals" to "RESTAURANT",
        "Burger" to "RESTAURANT",
        "Transportes Aereos Portugueses S A" to "FLIGHTS",
        "Corrida D Ingredientes - Lda" to "RESTAURANT",
        "Uber Eats Portugal Unipessoal Lda" to "RESTAURANT",
        "Uber Portugal Unipessoal Lda" to "TAXI_UBER"
    )
    classifier.train(trainingData)

    val testInputs = listOf(
        "Uber Eats Portugal Unipessoal Lda",
        "TAP Air",
        "Moneyflow Lda",
        "McDonald D Ingredientes",
        "Something Random",
        "Burger King",
        "Uber"
    )

    println("\n--- RESULTS WITH CONFIDENCE ---")
    testInputs.forEach { input ->
        val (label, score) = classifier.predictWithScore(input)
        val percentage = "%.2f%%".format(score * 100)
        println("Input: '$input' -> $label ($percentage confidence)")
    }
}