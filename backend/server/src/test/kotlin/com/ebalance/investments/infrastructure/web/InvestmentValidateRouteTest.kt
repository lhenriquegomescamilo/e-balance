package com.ebalance.investments.infrastructure.web

import arrow.core.left
import arrow.core.right
import com.ebalance.investments.application.GetStockPriceHistoryUseCase
import com.ebalance.investments.application.GetWalletHoldingsUseCase
import com.ebalance.investments.application.GetWalletProgressUseCase
import com.ebalance.investments.application.GetWalletSummaryUseCase
import com.ebalance.investments.application.UpsertInvestmentAssetUseCase
import com.ebalance.investments.application.ValidateStockUseCase
import com.ebalance.investments.domain.InvestmentError
import com.ebalance.investments.infrastructure.web.dto.ValidateStockResponse
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk

// Top-level helper — installs JSON negotiation and mounts only investment routes
private fun Application.configureTestRoutes(validateStockUseCase: ValidateStockUseCase) {
    install(ServerContentNegotiation) { json() }
    routing {
        route("/api/v1") {
            investmentRoutes(
                summaryUseCase          = mockk(),
                holdingsUseCase         = mockk(),
                progressUseCase         = mockk(),
                upsertUseCase           = mockk(),
                stockPriceHistoryUseCase = mockk(),
                validateStockUseCase    = validateStockUseCase
            )
        }
    }
}

class InvestmentValidateRouteTest : DescribeSpec({

    val validateUseCase = mockk<ValidateStockUseCase>()

    afterEach { clearAllMocks() }

    // ── Stock exists ───────────────────────────────────────────────────────────

    describe("GET /api/v1/investments/assets/validate") {

        it("returns 200 with exists=true when the stock is found") {
            every { validateUseCase.execute("AAPL", "NASDAQ") } returns true.right()

            testApplication {
                application { configureTestRoutes(validateUseCase) }
                val client = createClient { install(ClientContentNegotiation) { json() } }

                val response = client.get("/api/v1/investments/assets/validate?ticker=AAPL&exchange=NASDAQ")

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<ValidateStockResponse>()
                body.ticker   shouldBe "AAPL"
                body.exchange shouldBe "NASDAQ"
                body.exists   shouldBe true
            }
        }

        it("returns 200 with exists=false when the stock is not found in SerpAPI") {
            every { validateUseCase.execute("FAKE", "NASDAQ") } returns false.right()

            testApplication {
                application { configureTestRoutes(validateUseCase) }
                val client = createClient { install(ClientContentNegotiation) { json() } }

                val response = client.get("/api/v1/investments/assets/validate?ticker=FAKE&exchange=NASDAQ")

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<ValidateStockResponse>()
                body.ticker   shouldBe "FAKE"
                body.exchange shouldBe "NASDAQ"
                body.exists   shouldBe false
            }
        }

        it("normalises ticker to uppercase before forwarding to the use case") {
            every { validateUseCase.execute("AAPL", "NASDAQ") } returns true.right()

            testApplication {
                application { configureTestRoutes(validateUseCase) }
                val client = createClient { install(ClientContentNegotiation) { json() } }

                val response = client.get("/api/v1/investments/assets/validate?ticker=aapl&exchange=NASDAQ")

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<ValidateStockResponse>()
                body.ticker shouldBe "AAPL"
            }
        }

        it("normalises exchange to uppercase before forwarding to the use case") {
            every { validateUseCase.execute("AAPL", "NASDAQ") } returns true.right()

            testApplication {
                application { configureTestRoutes(validateUseCase) }
                val client = createClient { install(ClientContentNegotiation) { json() } }

                val response = client.get("/api/v1/investments/assets/validate?ticker=AAPL&exchange=nasdaq")

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<ValidateStockResponse>()
                body.exchange shouldBe "NASDAQ"
            }
        }

        // ── Missing parameters ───────────────────────────────────────────────

        it("returns 400 when ticker query parameter is missing") {
            testApplication {
                application { configureTestRoutes(validateUseCase) }
                val client = createClient { install(ClientContentNegotiation) { json() } }

                val response = client.get("/api/v1/investments/assets/validate?exchange=NASDAQ")

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        it("returns 400 when exchange query parameter is missing") {
            testApplication {
                application { configureTestRoutes(validateUseCase) }
                val client = createClient { install(ClientContentNegotiation) { json() } }

                val response = client.get("/api/v1/investments/assets/validate?ticker=AAPL")

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        it("returns 400 when both query parameters are missing") {
            testApplication {
                application { configureTestRoutes(validateUseCase) }
                val client = createClient { install(ClientContentNegotiation) { json() } }

                val response = client.get("/api/v1/investments/assets/validate")

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        // ── Use case errors ──────────────────────────────────────────────────

        it("returns 400 when the use case returns InvalidParameter") {
            every { validateUseCase.execute("", "NASDAQ") } returns
                InvestmentError.InvalidParameter("Ticker cannot be empty").left()

            testApplication {
                application { configureTestRoutes(validateUseCase) }
                val client = createClient { install(ClientContentNegotiation) { json() } }

                // Route guards blank after trim+uppercase, but verify error propagation too
                val response = client.get("/api/v1/investments/assets/validate?ticker=%20&exchange=NASDAQ")

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }
    }
})
