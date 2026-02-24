package com.ebalance.classification

import io.kotest.core.spec.style.DescribeSpec

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe

class TokenizerTest : DescribeSpec({

    describe("Tokenizer") {
        describe("fit function") {

            it("fit should correctly build word to index mapping") {

                val tokenizer = Tokenizer()

                val texts = listOf(
                    "Hello world",
                    "World is great",
                    "Great hello",
                )

                val wordIndexed = tokenizer.fit(texts).getWordIndexes()

                wordIndexed shouldHaveSize 4


                wordIndexed shouldContainExactly mapOf(
                    "hello" to 0,
                    "world" to 1,
                    "is" to 2,
                    "great" to 3
                )
            }

            it("fit should handle empty text list") {
                val tokenizer = Tokenizer()
                val wordIndexes = tokenizer.fit(emptyList()).getWordIndexes()
                wordIndexes.shouldBeEmpty()
            }
            it("fit should handle duplicate words in input texts") {
                val tokenizer = Tokenizer()
                val texts = listOf("apple apple", "banana apple")
                val wordToIndex = tokenizer.fit(texts).getWordIndexes()
                wordToIndex shouldHaveSize 2 // apple, banana
                wordToIndex shouldContainExactly mapOf("apple" to 0, "banana" to 1)
            }
        }

        describe("transform function") {
            it("should create correct vector after fitting") {
                val tokenizer = Tokenizer()
                val textsToFit = listOf("apple banna orange", "grape apple")
                val wordIndexed = tokenizer.fit(textsToFit).getWordIndexes()

                val testText = " banna apple apple grape"
                val vector = tokenizer.transform(testText)

                vector shouldHaveSize wordIndexed.size

                val expectedVector = DoubleArray(wordIndexed.size)

                wordIndexed.getValue("banna").let { expectedVector[it] += 1.0 }
                wordIndexed.getValue("apple").let { expectedVector[it] += 2.0 }
                wordIndexed.getValue("grape").let { expectedVector[it] += 1.0 }

                vector shouldBe expectedVector
            }

            it("should return vector of correct size even if words not in vocabulary") {
                val tokenizer = Tokenizer()
                val textsToFit = listOf("hello world")
                val testText = "unknown word"

                val wordIndexes = tokenizer.fit(textsToFit).getWordIndexes()
                val vector = tokenizer.transform(testText)

                vector shouldHaveSize wordIndexes.size
            }

            it("should return empty vector if tokenizer was not fitted") {
                val tokenizer = Tokenizer()
                val testText = "any test"

                val vector = tokenizer.transform(testText)
                vector shouldHaveSize 0
                vector.isEmpty() shouldBe true
            }

            it("should handle case insensitivity based on fit") {
                val tokenizer = Tokenizer()
                val textsToFit = listOf("apple BANNA orange", "grape apple")
                val wordIndexed = tokenizer.fit(textsToFit).getWordIndexes()

                val testText = " banna apple APPLE grape"
                val vector = tokenizer.transform(testText)

                vector shouldHaveSize wordIndexed.size

                val expectedVector = DoubleArray(wordIndexed.size)

                wordIndexed.getValue("banna").let { expectedVector[it] += 1.0 }
                wordIndexed.getValue("apple").let { expectedVector[it] += 2.0 }
                wordIndexed.getValue("grape").let { expectedVector[it] += 1.0 }

                vector shouldBe expectedVector
            }
        }
    }


})