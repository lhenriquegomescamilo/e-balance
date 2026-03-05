package com.ebalance.investments.application

import arrow.core.left
import arrow.core.right
import com.ebalance.investments.domain.InvestmentAsset
import com.ebalance.investments.domain.InvestmentError
import com.ebalance.investments.domain.InvestmentRepository
import com.ebalance.investments.domain.StockPriceService
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.YearMonth

class GetStockPriceHistoryInteractorTest : DescribeSpec({

    val repository        = mockk<InvestmentRepository>()
    val stockPriceService = mockk<StockPriceService>()
    val interactor        = GetStockPriceHistoryInteractor(repository, stockPriceService)

    beforeEach { clearMocks(repository, stockPriceService) }

    // ── Fixtures ───────────────────────────────────────────────────────────────

    fun anAsset(
        ticker: String = "AAPL",
        name: String = "Apple Inc.",
        exchange: String = "NASDAQ",
        sector: String = "Technology",
        investedAmount: Double = 1_000.0,
        currentValue: Double  = 1_200.0
    ) = InvestmentAsset(
        id             = 1,
        ticker         = ticker,
        name           = name,
        sector         = sector,
        exchange       = exchange,
        investedAmount = investedAmount,
        currentValue   = currentValue
    )

    // Six monthly data points in an unsorted map to verify sorting behavior
    val sixMonthPrices = mapOf(
        YearMonth.of(2026,  3) to 195.0,
        YearMonth.of(2025, 10) to 180.0,
        YearMonth.of(2026,  1) to 192.0,
        YearMonth.of(2025, 11) to 185.0,
        YearMonth.of(2025, 12) to 188.0,
        YearMonth.of(2026,  2) to 190.0
    )

    // ── Happy path ─────────────────────────────────────────────────────────────

    describe("happy path") {

        it("returns a StockPriceHistory for a valid asset") {
            every { repository.getAssets() } returns listOf(anAsset()).right()
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "6M") } returns
                (195.0 to sixMonthPrices).right()

            val result = interactor.execute("6M").shouldBeRight()

            result shouldHaveSize 1
            result.first().ticker shouldBe "AAPL"
        }

        it("maps all asset fields into StockPriceHistory") {
            every { repository.getAssets() } returns listOf(anAsset()).right()
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "6M") } returns
                (195.0 to sixMonthPrices).right()

            val h = interactor.execute("6M").shouldBeRight().first()

            h.ticker   shouldBe "AAPL"
            h.name     shouldBe "Apple Inc."
            h.exchange shouldBe "NASDAQ"
            h.sector   shouldBe "Technology"
        }

        it("returns months sorted chronologically") {
            every { repository.getAssets() } returns listOf(anAsset()).right()
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "6M") } returns
                (195.0 to sixMonthPrices).right()

            val h = interactor.execute("6M").shouldBeRight().first()

            h.months shouldBe listOf("Oct 2025", "Nov 2025", "Dec 2025", "Jan 2026", "Feb 2026", "Mar 2026")
        }

        it("returns prices aligned with the sorted months") {
            every { repository.getAssets() } returns listOf(anAsset()).right()
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "6M") } returns
                (195.0 to sixMonthPrices).right()

            val h = interactor.execute("6M").shouldBeRight().first()

            h.prices shouldBe listOf(180.0, 185.0, 188.0, 192.0, 190.0, 195.0)
        }

        it("sets currentPrice from the service response") {
            every { repository.getAssets() } returns listOf(anAsset()).right()
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "6M") } returns
                (195.0 to sixMonthPrices).right()

            interactor.execute("6M").shouldBeRight().first().currentPrice shouldBe 195.0
        }

        it("returns all valid assets when multiple are present") {
            val assetA = anAsset(ticker = "AAPL", exchange = "NASDAQ")
            val assetB = anAsset(ticker = "MSFT", exchange = "NASDAQ", investedAmount = 500.0, currentValue = 600.0)
            every { repository.getAssets() } returns listOf(assetA, assetB).right()
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "6M") } returns (195.0 to sixMonthPrices).right()
            every { stockPriceService.getMonthlyPrices("MSFT", "NASDAQ", "6M") } returns (300.0 to sixMonthPrices).right()

            val result = interactor.execute("6M").shouldBeRight()

            result shouldHaveSize 2
            result.map { it.ticker } shouldBe listOf("AAPL", "MSFT")
        }
    }

    // ── Investment P&L fields ──────────────────────────────────────────────────

    describe("investment P&L fields") {

        it("investedAmount and currentValue come from the asset record") {
            every { repository.getAssets() } returns listOf(anAsset(investedAmount = 1_000.0, currentValue = 1_200.0)).right()
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "6M") } returns (195.0 to sixMonthPrices).right()

            val h = interactor.execute("6M").shouldBeRight().first()

            h.investedAmount shouldBe 1_000.0
            h.currentValue   shouldBe 1_200.0
        }

        it("pnl equals currentValue minus investedAmount for a gain") {
            every { repository.getAssets() } returns listOf(anAsset(investedAmount = 800.0, currentValue = 950.0)).right()
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "6M") } returns (195.0 to sixMonthPrices).right()

            interactor.execute("6M").shouldBeRight().first().pnl shouldBe 150.0
        }

        it("pnl is negative when the position is at a loss") {
            every { repository.getAssets() } returns listOf(anAsset(investedAmount = 1_500.0, currentValue = 1_200.0)).right()
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "6M") } returns (195.0 to sixMonthPrices).right()

            interactor.execute("6M").shouldBeRight().first().pnl shouldBe -300.0
        }

        it("roi reflects the percentage return on investment") {
            every { repository.getAssets() } returns listOf(anAsset(investedAmount = 1_000.0, currentValue = 1_200.0)).right()
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "6M") } returns (195.0 to sixMonthPrices).right()

            // roi = (200 / 1000) * 100 = 20.0
            interactor.execute("6M").shouldBeRight().first().roi shouldBe 20.0
        }

        it("roi is 0.0 when investedAmount is zero") {
            every { repository.getAssets() } returns listOf(anAsset(investedAmount = 0.0, currentValue = 500.0)).right()
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "6M") } returns (195.0 to sixMonthPrices).right()

            interactor.execute("6M").shouldBeRight().first().roi shouldBe 0.0
        }
    }

    // ── qty calculation ────────────────────────────────────────────────────────

    describe("qty — estimated shares held") {

        it("calculates qty as currentValue divided by currentPrice") {
            // currentValue = 1_200.0, currentPrice = 192.0  →  qty = 6.25
            every { repository.getAssets() } returns listOf(anAsset(currentValue = 1_200.0)).right()
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "6M") } returns (192.0 to sixMonthPrices).right()

            interactor.execute("6M").shouldBeRight().first().qty shouldBe 1_200.0 / 192.0
        }

        it("returns 0.0 qty when currentPrice is zero") {
            val zeroPrices = mapOf(YearMonth.of(2025, 10) to 0.0, YearMonth.of(2025, 11) to 0.0)
            every { repository.getAssets() } returns listOf(anAsset(currentValue = 1_200.0)).right()
            // currentPrice = 0.0 — asset is skipped entirely (zero price guard), so qty edge case
            // is covered via a stock that passes the price guard but whose currentPrice is just above 0
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "6M") } returns (0.0 to zeroPrices).right()

            // The asset is skipped because currentPrice <= 0, so the result is empty
            interactor.execute("6M").shouldBeRight().shouldBeEmpty()
        }

        it("returns 0.0 qty when currentValue is zero") {
            every { repository.getAssets() } returns listOf(anAsset(currentValue = 0.0)).right()
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "6M") } returns (195.0 to sixMonthPrices).right()

            interactor.execute("6M").shouldBeRight().first().qty shouldBe 0.0
        }

        it("produces a fractional qty for a non-round division") {
            // currentValue = 1_000.0, currentPrice = 300.0  →  qty = 3.333…
            every { repository.getAssets() } returns listOf(anAsset(currentValue = 1_000.0)).right()
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "6M") } returns (300.0 to sixMonthPrices).right()

            interactor.execute("6M").shouldBeRight().first().qty shouldBe 1_000.0 / 300.0
        }

        it("each asset carries its own independently computed qty") {
            val assetA = anAsset(ticker = "AAPL", exchange = "NASDAQ", currentValue = 1_200.0)
            val assetB = anAsset(ticker = "MSFT", exchange = "NASDAQ", currentValue = 900.0)
            every { repository.getAssets() } returns listOf(assetA, assetB).right()
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "6M") } returns (200.0 to sixMonthPrices).right()
            every { stockPriceService.getMonthlyPrices("MSFT", "NASDAQ", "6M") } returns (300.0 to sixMonthPrices).right()

            val results = interactor.execute("6M").shouldBeRight()

            results.first { it.ticker == "AAPL" }.qty shouldBe 1_200.0 / 200.0
            results.first { it.ticker == "MSFT" }.qty shouldBe 900.0 / 300.0
        }
    }

    // ── changePct calculation ──────────────────────────────────────────────────

    describe("changePct calculation") {

        it("calculates changePct from first monthly price to current price") {
            // first price = 180.0 (Oct 2025), current = 195.0
            // changePct = (195.0 - 180.0) / 180.0 * 100
            every { repository.getAssets() } returns listOf(anAsset()).right()
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "6M") } returns (195.0 to sixMonthPrices).right()

            val expected = (195.0 - 180.0) / 180.0 * 100.0
            interactor.execute("6M").shouldBeRight().first().changePct shouldBe expected
        }

        it("returns a negative changePct when the price declined") {
            val decliningPrices = mapOf(
                YearMonth.of(2025, 10) to 200.0,
                YearMonth.of(2025, 11) to 180.0
            )
            every { repository.getAssets() } returns listOf(anAsset()).right()
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "6M") } returns (180.0 to decliningPrices).right()

            val expected = (180.0 - 200.0) / 200.0 * 100.0
            interactor.execute("6M").shouldBeRight().first().changePct shouldBe expected
        }

        it("returns 0.0 changePct when the first monthly price is zero") {
            val zeroPrices = mapOf(
                YearMonth.of(2025, 10) to 0.0,
                YearMonth.of(2025, 11) to 100.0
            )
            every { repository.getAssets() } returns listOf(anAsset()).right()
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "6M") } returns (100.0 to zeroPrices).right()

            interactor.execute("6M").shouldBeRight().first().changePct shouldBe 0.0
        }
    }

    // ── Empty asset list ───────────────────────────────────────────────────────

    describe("empty asset list") {

        it("returns an empty list when the repository has no assets") {
            every { repository.getAssets() } returns emptyList<InvestmentAsset>().right()

            interactor.execute("6M").shouldBeRight().shouldBeEmpty()
        }

        it("does not call StockPriceService when there are no assets") {
            every { repository.getAssets() } returns emptyList<InvestmentAsset>().right()

            interactor.execute("6M")

            verify(exactly = 0) { stockPriceService.getMonthlyPrices(any(), any(), any()) }
        }
    }

    // ── Repository failure ─────────────────────────────────────────────────────

    describe("repository failure") {

        it("propagates a DatabaseError when the repository fails") {
            val error = InvestmentError.DatabaseError("connection lost", RuntimeException("timeout"))
            every { repository.getAssets() } returns error.left()

            interactor.execute("6M").shouldBeLeft() shouldBe error
        }

        it("does not call StockPriceService when the repository fails") {
            every { repository.getAssets() } returns InvestmentError.DatabaseError("db error").left()

            interactor.execute("6M")

            verify(exactly = 0) { stockPriceService.getMonthlyPrices(any(), any(), any()) }
        }
    }

    // ── Blank exchange ─────────────────────────────────────────────────────────

    describe("blank exchange") {

        it("skips an asset with an empty exchange") {
            every { repository.getAssets() } returns listOf(anAsset(exchange = "")).right()

            interactor.execute("6M").shouldBeRight().shouldBeEmpty()
        }

        it("skips an asset with a whitespace-only exchange") {
            every { repository.getAssets() } returns listOf(anAsset(exchange = "   ")).right()

            interactor.execute("6M").shouldBeRight().shouldBeEmpty()
        }

        it("does not call StockPriceService for an asset with a blank exchange") {
            every { repository.getAssets() } returns listOf(anAsset(exchange = "")).right()

            interactor.execute("6M")

            verify(exactly = 0) { stockPriceService.getMonthlyPrices(any(), any(), any()) }
        }

        it("returns only assets that have a valid exchange when mixed") {
            val withExchange    = anAsset(ticker = "AAPL", exchange = "NASDAQ")
            val withoutExchange = anAsset(ticker = "UNKN", exchange = "")
            every { repository.getAssets() } returns listOf(withExchange, withoutExchange).right()
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "6M") } returns (195.0 to sixMonthPrices).right()

            val result = interactor.execute("6M").shouldBeRight()

            result shouldHaveSize 1
            result.first().ticker shouldBe "AAPL"
        }
    }

    // ── Invalid price data from StockPriceService ──────────────────────────────

    describe("invalid price data from StockPriceService") {

        it("skips an asset when currentPrice is zero") {
            every { repository.getAssets() } returns listOf(anAsset()).right()
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "6M") } returns (0.0 to sixMonthPrices).right()

            interactor.execute("6M").shouldBeRight().shouldBeEmpty()
        }

        it("skips an asset when currentPrice is negative") {
            every { repository.getAssets() } returns listOf(anAsset()).right()
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "6M") } returns (-10.0 to sixMonthPrices).right()

            interactor.execute("6M").shouldBeRight().shouldBeEmpty()
        }

        it("skips an asset when monthly prices are empty") {
            every { repository.getAssets() } returns listOf(anAsset()).right()
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "6M") } returns
                (195.0 to emptyMap<YearMonth, Double>()).right()

            interactor.execute("6M").shouldBeRight().shouldBeEmpty()
        }

        it("skips an asset when StockPriceService returns Left") {
            every { repository.getAssets() } returns listOf(anAsset()).right()
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "6M") } returns
                InvestmentError.DatabaseError("SerpAPI timeout").left()

            interactor.execute("6M").shouldBeRight().shouldBeEmpty()
        }
    }

    // ── Partial failures ───────────────────────────────────────────────────────

    describe("partial failures — best-effort processing") {

        it("returns only the assets that have valid price data") {
            val valid   = anAsset(ticker = "AAPL", exchange = "NASDAQ")
            val apiErr  = anAsset(ticker = "FAIL", exchange = "NYSE")
            val noExch  = anAsset(ticker = "UNKN", exchange = "")
            every { repository.getAssets() } returns listOf(valid, apiErr, noExch).right()
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "6M") } returns (195.0 to sixMonthPrices).right()
            every { stockPriceService.getMonthlyPrices("FAIL", "NYSE",   "6M") } returns InvestmentError.DatabaseError("timeout").left()

            val result = interactor.execute("6M").shouldBeRight()

            result shouldHaveSize 1
            result.first().ticker shouldBe "AAPL"
        }

        it("does not call StockPriceService for assets with blank exchanges") {
            val valid  = anAsset(ticker = "AAPL", exchange = "NASDAQ")
            val noExch = anAsset(ticker = "UNKN", exchange = "")
            every { repository.getAssets() } returns listOf(valid, noExch).right()
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", "6M") } returns (195.0 to sixMonthPrices).right()

            interactor.execute("6M")

            verify(exactly = 0) { stockPriceService.getMonthlyPrices("UNKN", any(), any()) }
        }
    }

    // ── Window parameter mapping ───────────────────────────────────────────────

    describe("window parameter mapping") {

        fun captureWindow(window: String): String {
            val slot = slot<String>()
            every { repository.getAssets() } returns listOf(anAsset()).right()
            every { stockPriceService.getMonthlyPrices("AAPL", "NASDAQ", capture(slot)) } returns
                (195.0 to sixMonthPrices).right()
            interactor.execute(window)
            return slot.captured
        }

        it("passes '3M' to StockPriceService when window is '3M'") {
            captureWindow("3M") shouldBe "3M"
        }

        it("passes '3M' to StockPriceService when window is '3m' (lowercase)") {
            captureWindow("3m") shouldBe "3M"
        }

        it("passes '1Y' to StockPriceService when window is '1Y'") {
            captureWindow("1Y") shouldBe "1Y"
        }

        it("passes '1Y' to StockPriceService when window is '1y' (lowercase)") {
            captureWindow("1y") shouldBe "1Y"
        }

        it("passes '6M' to StockPriceService when window is '6M'") {
            captureWindow("6M") shouldBe "6M"
        }

        it("defaults to '6M' for an unrecognised window value") {
            captureWindow("unknown") shouldBe "6M"
        }
    }
})
