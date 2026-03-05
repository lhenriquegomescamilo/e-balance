package com.ebalance.investments.application

import arrow.core.left
import arrow.core.right
import com.ebalance.investments.domain.InvestmentError
import com.ebalance.investments.domain.InvestmentRepository
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class UpsertInvestmentAssetInteractorTest : DescribeSpec({

    val repository = mockk<InvestmentRepository>()
    val interactor = UpsertInvestmentAssetInteractor(repository)

    // Clear call records between tests so verify(exactly = 0) checks only the current test
    beforeEach { clearMocks(repository) }

    // ── Happy path ─────────────────────────────────────────────────────────────

    describe("happy path") {

        beforeEach {
            every { repository.upsertAsset(any(), any(), any(), any(), any(), any()) } returns Unit.right()
        }

        it("returns Right(Unit) when all inputs are valid") {
            interactor.execute("AAPL", "Apple Inc.", "Technology", "NASDAQ", 1000.0, 1200.0)
                .shouldBeRight()
        }

        it("delegates to the repository with the exact values provided") {
            interactor.execute("MSFT", "Microsoft Corp.", "Technology", "NASDAQ", 500.0, 600.0)

            verify(exactly = 1) {
                repository.upsertAsset("MSFT", "Microsoft Corp.", "Technology", "NASDAQ", 500.0, 600.0)
            }
        }

        it("accepts zero as a valid investedAmount") {
            interactor.execute("AAPL", "Apple Inc.", "Technology", "NASDAQ", 0.0, 0.0)
                .shouldBeRight()
        }

        it("accepts zero as a valid currentValue") {
            interactor.execute("AAPL", "Apple Inc.", "Technology", "NASDAQ", 100.0, 0.0)
                .shouldBeRight()
        }

        it("propagates a DatabaseError returned by the repository") {
            val error = InvestmentError.DatabaseError("insert failed", RuntimeException("SQL error"))
            every { repository.upsertAsset(any(), any(), any(), any(), any(), any()) } returns error.left()

            interactor.execute("AAPL", "Apple Inc.", "Technology", "NASDAQ", 100.0, 120.0)
                .shouldBeLeft() shouldBe error
        }
    }

    // ── Validation — ticker ────────────────────────────────────────────────────

    describe("validation — ticker") {

        it("returns InvalidParameter when ticker is blank") {
            interactor.execute("   ", "Apple Inc.", "Technology", "NASDAQ", 100.0, 120.0)
                .shouldBeLeft()
                .shouldBeInstanceOf<InvestmentError.InvalidParameter>()
                .message shouldBe "Ticker cannot be empty"
        }

        it("returns InvalidParameter when ticker is an empty string") {
            interactor.execute("", "Apple Inc.", "Technology", "NASDAQ", 100.0, 120.0)
                .shouldBeLeft()
                .shouldBeInstanceOf<InvestmentError.InvalidParameter>()
                .message shouldBe "Ticker cannot be empty"
        }

        it("does not call the repository when ticker is blank") {
            interactor.execute("", "Apple Inc.", "Technology", "NASDAQ", 100.0, 120.0)

            verify(exactly = 0) { repository.upsertAsset(any(), any(), any(), any(), any(), any()) }
        }
    }

    // ── Validation — name ─────────────────────────────────────────────────────

    describe("validation — name") {

        it("returns InvalidParameter when name is blank") {
            interactor.execute("AAPL", "   ", "Technology", "NASDAQ", 100.0, 120.0)
                .shouldBeLeft()
                .shouldBeInstanceOf<InvestmentError.InvalidParameter>()
                .message shouldBe "Name cannot be empty"
        }

        it("returns InvalidParameter when name is an empty string") {
            interactor.execute("AAPL", "", "Technology", "NASDAQ", 100.0, 120.0)
                .shouldBeLeft()
                .shouldBeInstanceOf<InvestmentError.InvalidParameter>()
                .message shouldBe "Name cannot be empty"
        }

        it("does not call the repository when name is blank") {
            interactor.execute("AAPL", "", "Technology", "NASDAQ", 100.0, 120.0)

            verify(exactly = 0) { repository.upsertAsset(any(), any(), any(), any(), any(), any()) }
        }
    }

    // ── Validation — sector ───────────────────────────────────────────────────

    describe("validation — sector") {

        it("returns InvalidParameter when sector is blank") {
            interactor.execute("AAPL", "Apple Inc.", "   ", "NASDAQ", 100.0, 120.0)
                .shouldBeLeft()
                .shouldBeInstanceOf<InvestmentError.InvalidParameter>()
                .message shouldBe "Sector cannot be empty"
        }

        it("returns InvalidParameter when sector is an empty string") {
            interactor.execute("AAPL", "Apple Inc.", "", "NASDAQ", 100.0, 120.0)
                .shouldBeLeft()
                .shouldBeInstanceOf<InvestmentError.InvalidParameter>()
                .message shouldBe "Sector cannot be empty"
        }

        it("does not call the repository when sector is blank") {
            interactor.execute("AAPL", "Apple Inc.", "", "NASDAQ", 100.0, 120.0)

            verify(exactly = 0) { repository.upsertAsset(any(), any(), any(), any(), any(), any()) }
        }
    }

    // ── Validation — investedAmount ───────────────────────────────────────────

    describe("validation — investedAmount") {

        it("returns InvalidParameter when investedAmount is negative") {
            interactor.execute("AAPL", "Apple Inc.", "Technology", "NASDAQ", -0.01, 100.0)
                .shouldBeLeft()
                .shouldBeInstanceOf<InvestmentError.InvalidParameter>()
                .message shouldBe "Invested amount must be ≥ 0"
        }

        it("returns InvalidParameter when investedAmount is a large negative value") {
            interactor.execute("AAPL", "Apple Inc.", "Technology", "NASDAQ", -9999.0, 100.0)
                .shouldBeLeft()
                .shouldBeInstanceOf<InvestmentError.InvalidParameter>()
        }

        it("does not call the repository when investedAmount is negative") {
            interactor.execute("AAPL", "Apple Inc.", "Technology", "NASDAQ", -1.0, 100.0)

            verify(exactly = 0) { repository.upsertAsset(any(), any(), any(), any(), any(), any()) }
        }
    }

    // ── Validation — currentValue ─────────────────────────────────────────────

    describe("validation — currentValue") {

        it("returns InvalidParameter when currentValue is negative") {
            every { repository.upsertAsset(any(), any(), any(), any(), any(), any()) } returns Unit.right()

            interactor.execute("AAPL", "Apple Inc.", "Technology", "NASDAQ", 100.0, -0.01)
                .shouldBeLeft()
                .shouldBeInstanceOf<InvestmentError.InvalidParameter>()
                .message shouldBe "Current value must be ≥ 0"
        }

        it("does not call the repository when currentValue is negative") {
            interactor.execute("AAPL", "Apple Inc.", "Technology", "NASDAQ", 100.0, -50.0)

            verify(exactly = 0) { repository.upsertAsset(any(), any(), any(), any(), any(), any()) }
        }
    }
})
