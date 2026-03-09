package com.ebalance.investments.application

import arrow.core.left
import arrow.core.right
import com.ebalance.investments.domain.InvestmentError
import com.ebalance.investments.domain.StockPriceService
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.YearMonth

class ValidateStockInteractorTest : DescribeSpec({

    val stockPriceService = mockk<StockPriceService>()
    val interactor        = ValidateStockInteractor(stockPriceService)

    val samplePrices = Pair(
        195.0,
        mapOf(YearMonth.of(2026, 1) to 195.0)
    )

    beforeEach { clearMocks(stockPriceService) }

    // ── Happy path ─────────────────────────────────────────────────────────────

    describe("happy path") {

        it("returns Right(true) when SerpAPI returns data for the stock") {
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "1M") } returns samplePrices.right()

            interactor.execute("AAPL", "NASDAQ").shouldBeRight() shouldBe true
        }

        it("returns Right(false) when SerpAPI returns an error (stock not found)") {
            every { stockPriceService.getMonthlyPrices("INVALID", "NASDAQ", "1M") } returns
                InvestmentError.DatabaseError("Not found", RuntimeException("404")).left()

            interactor.execute("INVALID", "NASDAQ").shouldBeRight() shouldBe false
        }

        it("forwards ticker as-is (trimmed) to the service without changing case") {
            every { stockPriceService.getMonthlyPrices("aapl", "NASDAQ", "1M") } returns samplePrices.right()

            interactor.execute("aapl", "NASDAQ").shouldBeRight()

            verify(exactly = 1) { stockPriceService.getMonthlyPrices("aapl", "NASDAQ", "1M") }
        }

        it("forwards exchange as-is (trimmed) to the service without changing case") {
            every { stockPriceService.getMonthlyPrices("AAPL", "nasdaq", "1M") } returns samplePrices.right()

            interactor.execute("AAPL", "nasdaq").shouldBeRight()

            verify(exactly = 1) { stockPriceService.getMonthlyPrices("AAPL", "nasdaq", "1M") }
        }

        it("always uses the '1M' window regardless of the stock") {
            every { stockPriceService.getMonthlyPrices(any(), any(), "1M") } returns samplePrices.right()

            interactor.execute("MSFT", "NASDAQ")

            verify(exactly = 1) { stockPriceService.getMonthlyPrices("MSFT", "NASDAQ", "1M") }
        }
    }

    // ── Validation ─────────────────────────────────────────────────────────────

    describe("validation — ticker") {

        it("returns InvalidParameter when ticker is blank") {
            interactor.execute("   ", "NASDAQ")
                .shouldBeLeft()
                .shouldBeInstanceOf<InvestmentError.InvalidParameter>()
                .message shouldBe "Ticker cannot be empty"
        }

        it("returns InvalidParameter when ticker is an empty string") {
            interactor.execute("", "NASDAQ")
                .shouldBeLeft()
                .shouldBeInstanceOf<InvestmentError.InvalidParameter>()
                .message shouldBe "Ticker cannot be empty"
        }

        it("does not call the service when ticker is blank") {
            interactor.execute("", "NASDAQ")

            verify(exactly = 0) { stockPriceService.getMonthlyPrices(any(), any(), any()) }
        }
    }

    describe("validation — exchange") {

        it("returns InvalidParameter when exchange is blank") {
            interactor.execute("AAPL", "   ")
                .shouldBeLeft()
                .shouldBeInstanceOf<InvestmentError.InvalidParameter>()
                .message shouldBe "Exchange cannot be empty"
        }

        it("returns InvalidParameter when exchange is an empty string") {
            interactor.execute("AAPL", "")
                .shouldBeLeft()
                .shouldBeInstanceOf<InvestmentError.InvalidParameter>()
                .message shouldBe "Exchange cannot be empty"
        }

        it("does not call the service when exchange is blank") {
            interactor.execute("AAPL", "")

            verify(exactly = 0) { stockPriceService.getMonthlyPrices(any(), any(), any()) }
        }
    }

    // ── Edge cases ─────────────────────────────────────────────────────────────

    describe("edge cases") {

        it("trims whitespace around ticker before forwarding") {
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "1M") } returns samplePrices.right()

            interactor.execute("  AAPL  ", "NASDAQ").shouldBeRight() shouldBe true

            verify(exactly = 1) { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "1M") }
        }

        it("trims whitespace around exchange before forwarding") {
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "1M") } returns samplePrices.right()

            interactor.execute("AAPL", "  NASDAQ  ").shouldBeRight() shouldBe true

            verify(exactly = 1) { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "1M") }
        }
    }
})
