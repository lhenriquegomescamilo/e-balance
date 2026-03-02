package com.ebalance.transactions.application

import arrow.core.right
import com.ebalance.transactions.domain.CategoryEntry
import com.ebalance.transactions.domain.TransactionRepository
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class GetCategoriesInteractorTest : DescribeSpec({

    val repository = mockk<TransactionRepository>()
    val interactor = GetCategoriesInteractor(repository)

    val allCategories = listOf(
        CategoryEntry(1L, "Food", "FOOD"),
        CategoryEntry(2L, "Transport", "TRANSPORT"),
        CategoryEntry(3L, "Entertainment", "ENTERTAINMENT")
    )

    beforeEach {
        every { repository.getCategories() } returns allCategories.right()
    }

    describe("execute") {

        it("returns all categories when ids list is empty") {
            val result = interactor.execute(emptyList()).shouldBeRight()

            result shouldHaveSize 3
            result shouldBe allCategories
        }

        it("returns only the matching categories when ids are provided") {
            val result = interactor.execute(listOf(1L, 3L)).shouldBeRight()

            result shouldHaveSize 2
            result.map { it.id } shouldBe listOf(1L, 3L)
        }

        it("returns a single category when one id is provided") {
            val result = interactor.execute(listOf(2L)).shouldBeRight()

            result shouldHaveSize 1
            result.first().name shouldBe "Transport"
        }

        it("returns an empty list when no categories match the given ids") {
            val result = interactor.execute(listOf(99L, 100L)).shouldBeRight()

            result.shouldBeEmpty()
        }

        it("returns an empty list when the repository has no categories") {
            every { repository.getCategories() } returns emptyList<CategoryEntry>().right()

            val result = interactor.execute(emptyList()).shouldBeRight()

            result.shouldBeEmpty()
        }
    }
})
