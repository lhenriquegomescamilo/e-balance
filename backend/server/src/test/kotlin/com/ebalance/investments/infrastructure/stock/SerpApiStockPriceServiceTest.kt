package com.ebalance.investments.infrastructure.stock

import com.redis.testcontainers.RedisContainer
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.lettuce.core.RedisClient
import io.lettuce.core.api.sync.RedisCommands
import java.time.YearMonth

class SerpApiStockPriceServiceTest : DescribeSpec({

    // ── Redis container (shared across all tests in this spec) ─────────────────

    val redis = RedisContainer("redis:7-alpine").apply { start() }

    afterSpec { redis.stop() }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Opens a fresh Lettuce connection to the container and flushes all keys. */
    fun freshCommands(): RedisCommands<String, String> {
        val commands = RedisClient.create(redis.redisURI).connect().sync()
        commands.flushall()
        return commands
    }

    // ── Fake subclass ──────────────────────────────────────────────────────────
    //
    // Overrides doFetch so tests never make real HTTP calls.
    // fetchCount lets us assert how many real "network" calls were made.

    class FakeService(
        redis: RedisCommands<String, String>,
        private val onFetch: (String, String, String) -> Pair<Double, Map<YearMonth, Double>>
    ) : SerpApiStockPriceService("test-key", redis) {
        var fetchCount = 0
        override fun doFetch(ticker: String, exchange: String, window: String): Pair<Double, Map<YearMonth, Double>> {
            fetchCount++
            return onFetch(ticker, exchange, window)
        }
    }

    // ── Fixtures ───────────────────────────────────────────────────────────────

    val sampleResult = Pair(
        195.0,
        mapOf(
            YearMonth.of(2025, 10) to 180.0,
            YearMonth.of(2025, 11) to 185.0,
            YearMonth.of(2026,  3) to 195.0
        )
    )

    fun freshService(
        onFetch: (String, String, String) -> Pair<Double, Map<YearMonth, Double>> = { _, _, _ -> sampleResult }
    ) = FakeService(freshCommands(), onFetch)

    // ── Caching behaviour ──────────────────────────────────────────────────────

    describe("caching") {

        it("calls doFetch on the first request (cache miss)") {
            val service = freshService()

            service.getMonthlyPrices("AAPL", "NASDAQ", "6M")

            service.fetchCount shouldBe 1
        }

        it("does not call doFetch again on a second request for the same key") {
            val service = freshService()

            service.getMonthlyPrices("AAPL", "NASDAQ", "6M")
            service.getMonthlyPrices("AAPL", "NASDAQ", "6M")

            service.fetchCount shouldBe 1
        }

        it("returns the same data on the second call") {
            val service = freshService()

            val first  = service.getMonthlyPrices("AAPL", "NASDAQ", "6M").shouldBeRight()
            val second = service.getMonthlyPrices("AAPL", "NASDAQ", "6M").shouldBeRight()

            first  shouldBe sampleResult
            second shouldBe sampleResult
        }

        it("does not cache a failed fetch — doFetch is called again on retry") {
            val service = FakeService(freshCommands()) { _, _, _ -> throw RuntimeException("timeout") }

            service.getMonthlyPrices("AAPL", "NASDAQ", "6M").shouldBeLeft()
            service.getMonthlyPrices("AAPL", "NASDAQ", "6M").shouldBeLeft()

            service.fetchCount shouldBe 2
        }

        it("after a failure, a successful retry is cached for subsequent calls") {
            var attempt = 0
            val service = FakeService(freshCommands()) { _, _, _ ->
                if (++attempt == 1) throw RuntimeException("transient error")
                else sampleResult
            }

            service.getMonthlyPrices("AAPL", "NASDAQ", "6M").shouldBeLeft()   // miss + error
            service.getMonthlyPrices("AAPL", "NASDAQ", "6M").shouldBeRight()  // miss + success → cached
            service.getMonthlyPrices("AAPL", "NASDAQ", "6M").shouldBeRight()  // hit

            service.fetchCount shouldBe 2
        }

        it("treats different windows as separate cache entries") {
            val service = freshService()

            service.getMonthlyPrices("AAPL", "NASDAQ", "3M")
            service.getMonthlyPrices("AAPL", "NASDAQ", "1Y")
            service.getMonthlyPrices("AAPL", "NASDAQ", "3M")  // cache hit
            service.getMonthlyPrices("AAPL", "NASDAQ", "1Y")  // cache hit

            service.fetchCount shouldBe 2
        }

        it("treats different tickers as separate cache entries") {
            val service = freshService()

            service.getMonthlyPrices("AAPL", "NASDAQ", "6M")
            service.getMonthlyPrices("MSFT", "NASDAQ", "6M")
            service.getMonthlyPrices("AAPL", "NASDAQ", "6M")  // cache hit
            service.getMonthlyPrices("MSFT", "NASDAQ", "6M")  // cache hit

            service.fetchCount shouldBe 2
        }

        it("treats different exchanges as separate cache entries") {
            val service = freshService()

            service.getMonthlyPrices("VOW", "XETRA",    "6M")
            service.getMonthlyPrices("VOW", "EURONEXT", "6M")
            service.getMonthlyPrices("VOW", "XETRA",    "6M")  // cache hit
            service.getMonthlyPrices("VOW", "EURONEXT", "6M")  // cache hit

            service.fetchCount shouldBe 2
        }

        it("persists cache entries in Redis with a TTL set") {
            val commands = freshCommands()
            val service  = FakeService(commands) { _, _, _ -> sampleResult }

            service.getMonthlyPrices("AAPL", "NASDAQ", "6M")

            val ttl = commands.ttl("serpapi:AAPL:NASDAQ:6M")
            (ttl > 0) shouldBe true
        }
    }

    // ── Cache key normalisation ────────────────────────────────────────────────

    describe("cache key normalisation") {

        it("lowercase and uppercase ticker share the same cache entry") {
            val service = freshService()

            service.getMonthlyPrices("AAPL", "NASDAQ", "6M")
            service.getMonthlyPrices("aapl", "NASDAQ", "6M")  // same key after uppercase

            service.fetchCount shouldBe 1
        }

        it("lowercase and uppercase exchange share the same cache entry") {
            val service = freshService()

            service.getMonthlyPrices("AAPL", "NASDAQ", "6M")
            service.getMonthlyPrices("AAPL", "nasdaq", "6M")  // same key after uppercase

            service.fetchCount shouldBe 1
        }

        it("lowercase and uppercase window share the same cache entry") {
            val service = freshService()

            service.getMonthlyPrices("AAPL", "NASDAQ", "6M")
            service.getMonthlyPrices("AAPL", "NASDAQ", "6m")  // same key after uppercase

            service.fetchCount shouldBe 1
        }

        it("fully lowercase call shares cache with fully uppercase call") {
            val service = freshService()

            service.getMonthlyPrices("AAPL", "NASDAQ", "6M")
            service.getMonthlyPrices("aapl", "nasdaq", "6m")  // all lowercase → same key

            service.fetchCount shouldBe 1
        }
    }

    // ── Redis serialisation round-trip ─────────────────────────────────────────

    describe("Redis serialisation") {

        it("correctly round-trips monthly price data through Redis") {
            val service = freshService()

            service.getMonthlyPrices("AAPL", "NASDAQ", "6M")  // populates Redis
            service.getMonthlyPrices("AAPL", "NASDAQ", "6M")  // reads from Redis

            val result = service.getMonthlyPrices("AAPL", "NASDAQ", "6M").shouldBeRight()
            result shouldBe sampleResult
        }

        it("two service instances sharing the same Redis see the same cached value") {
            val commands = freshCommands()
            val serviceA = FakeService(commands) { _, _, _ -> sampleResult }
            val serviceB = FakeService(commands) { _, _, _ -> error("should not be called") }

            serviceA.getMonthlyPrices("AAPL", "NASDAQ", "6M")  // populates cache
            val result = serviceB.getMonthlyPrices("AAPL", "NASDAQ", "6M").shouldBeRight()

            result shouldBe sampleResult
        }
    }
})
