package com.ebalance.classification

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.toImmutableMap

class Tokenizer {

    private var workToIndex: ImmutableMap<String, Int> = persistentHashMapOf()

    private class Fit(private val texts: List<String>) {
        fun execute(): ImmutableMap<String, Int> = texts
            .flatMap { it.lowercase().split(" ") }
            .distinct()
            .foldIndexed(persistentHashMapOf()) { index, acc, current -> acc.put(current, index) }

    }

    private class Transform(private val indexedWords: ImmutableMap<String, Int>, private val text: String) {
        fun execute(): DoubleArray {
            val vector = DoubleArray(indexedWords.size)
            for (word in text.lowercase().split(" ")) {
                indexedWords[word]?.let { index -> vector[index] += 1.0 }
            }

            return vector
        }
    }


    fun fit(texts: List<String>): Tokenizer {
        workToIndex = Fit(texts).execute()
        return this
    }

    fun getWordIndexes(): ImmutableMap<String, Int> {
        return workToIndex.toImmutableMap()
    }

    fun transform(text: String): DoubleArray = Transform(workToIndex, text).execute()
}