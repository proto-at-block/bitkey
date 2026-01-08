package build.wallet.bitcoin.fees

import build.wallet.availability.NetworkReachabilityProviderMock
import build.wallet.bitcoin.BitcoinNetworkType.*
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.*
import build.wallet.coroutines.turbine.turbines
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AugurFeesHttpClientImplTests : FunSpec({
  val networkReachabilityProvider = NetworkReachabilityProviderMock("augur", turbines::create)

  fun createTestClient(mockEngine: HttpClientEngine): AugurFeesHttpClient {
    return AugurFeesHttpClientImpl(networkReachabilityProvider, mockEngine)
  }

  test("successful fee rate request for FASTEST priority") {
    val mockEngine = MockEngine { request ->
      request.url.encodedPath shouldBe "/fees"
      request.method shouldBe HttpMethod.Get
      request.headers[HttpHeaders.Accept] shouldBe "application/json"

      respond(
        content = ByteReadChannel(successfulAugurResponse),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
      )
    }

    val client = createTestClient(mockEngine)
    val result = client.getAugurFeesFeeRate(BITCOIN, FASTEST)

    result.isOk.shouldBeTrue()
    result.getOrThrow().satsPerVByte shouldBe 10f
  }

  test("successful fee rate request for THIRTY_MINUTES priority") {
    val mockEngine = MockEngine { request ->
      respond(
        content = ByteReadChannel(successfulAugurResponse),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
      )
    }

    val client = createTestClient(mockEngine)
    val result = client.getAugurFeesFeeRate(BITCOIN, THIRTY_MINUTES)

    result.isOk.shouldBeTrue()
    result.getOrThrow().satsPerVByte shouldBe 5f
  }

  test("successful fee rate request for SIXTY_MINUTES priority") {
    val mockEngine = MockEngine { request ->
      respond(
        content = ByteReadChannel(successfulAugurResponse),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
      )
    }

    val client = createTestClient(mockEngine)
    val result = client.getAugurFeesFeeRate(BITCOIN, SIXTY_MINUTES)

    result.isOk.shouldBeTrue()
    result.getOrThrow().satsPerVByte shouldBe 3f
  }

  test("successful get all fee rates request") {
    val mockEngine = MockEngine { request ->
      request.url.encodedPath shouldBe "/fees"
      request.method shouldBe HttpMethod.Get

      respond(
        content = ByteReadChannel(successfulAugurResponse),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
      )
    }

    val client = createTestClient(mockEngine)
    val result = client.getAugurFeesFeeRates(BITCOIN)

    result.isOk.shouldBeTrue()
    val feeRates = result.getOrThrow()
    feeRates.fastestFeeRate.satsPerVByte shouldBe 10f // 3 blocks @ 95%
    feeRates.halfHourFeeRate.satsPerVByte shouldBe 5f // 3 blocks @ 80%
    feeRates.hourFeeRate.satsPerVByte shouldBe 3f // 6 blocks @ 80%
  }

  test("handles HTTP 500 server error") {
    val mockEngine = MockEngine { request ->
      respond(
        content = ByteReadChannel("Internal Server Error"),
        status = HttpStatusCode.InternalServerError,
        headers = headersOf(HttpHeaders.ContentType, "text/plain")
      )
    }

    val client = createTestClient(mockEngine)
    val result = client.getAugurFeesFeeRate(BITCOIN, FASTEST)

    result.isErr.shouldBeTrue()
  }

  test("handles HTTP 404 not found error") {
    val mockEngine = MockEngine { request ->
      respond(
        content = ByteReadChannel("Not Found"),
        status = HttpStatusCode.NotFound,
        headers = headersOf(HttpHeaders.ContentType, "text/plain")
      )
    }

    val client = createTestClient(mockEngine)
    val result = client.getAugurFeesFeeRates(BITCOIN)

    result.isErr.shouldBeTrue()
  }

  test("handles malformed JSON response") {
    val mockEngine = MockEngine { request ->
      respond(
        content = ByteReadChannel("{ invalid json }"),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
      )
    }

    val client = createTestClient(mockEngine)
    val result = client.getAugurFeesFeeRate(BITCOIN, FASTEST)

    result.isErr.shouldBeTrue()
  }

  test("handles missing blocks") {
    val mockEngine = MockEngine { request ->
      respond(
        content = ByteReadChannel(responseWithMissingThreeBlocks),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
      )
    }

    val client = createTestClient(mockEngine)
    val result = client.getAugurFeesFeeRate(BITCOIN, FASTEST)

    result.isErr.shouldBeTrue()
  }

  test("handles missing confidence level data") {
    val mockEngine = MockEngine { request ->
      respond(
        content = ByteReadChannel(responseWithMissingConfidenceLevels),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
      )
    }

    val client = createTestClient(mockEngine)
    val result = client.getAugurFeesFeeRate(BITCOIN, FASTEST)

    result.isErr.shouldBeTrue()
  }

  test("handles empty response body") {
    val mockEngine = MockEngine { request ->
      respond(
        content = ByteReadChannel(""),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
      )
    }

    val client = createTestClient(mockEngine)
    val result = client.getAugurFeesFeeRates(BITCOIN)

    result.isErr.shouldBeTrue()
  }

  test("validate request URL and headers for production") {
    val mockEngine = MockEngine { request ->
      request.url.protocol shouldBe URLProtocol.HTTPS
      request.url.host shouldBe "pricing.bitcoin.block.xyz"
      request.url.encodedPath shouldBe "/fees"
      request.method shouldBe HttpMethod.Get
      request.headers[HttpHeaders.Accept] shouldBe "application/json"
      request.headers[HttpHeaders.ContentType] shouldBe "application/json"

      respond(
        content = ByteReadChannel(successfulAugurResponse),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
      )
    }

    val client = createTestClient(mockEngine)
    client.getAugurFeesFeeRate(BITCOIN, FASTEST)
  }

  test("validate request URL and headers for signet") {
    val mockEngine = MockEngine { request ->
      request.url.protocol shouldBe URLProtocol.HTTPS
      request.url.host shouldBe "pricing.bitcoin.blockstaging.xyz"
      request.url.encodedPath shouldBe "/fees"
      request.method shouldBe HttpMethod.Get
      request.headers[HttpHeaders.Accept] shouldBe "application/json"
      request.headers[HttpHeaders.ContentType] shouldBe "application/json"

      respond(
        content = ByteReadChannel(successfulAugurResponse),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
      )
    }

    val client = createTestClient(mockEngine)
    client.getAugurFeesFeeRate(SIGNET, FASTEST)
  }
})

// Successful response
private val successfulAugurResponse = Json.encodeToString(
  AugurFeesResponse(
    estimates = AugurFeesEstimates(
      threeBlocks = AugurFeesTimeEstimate(
        probabilities = AugurFeesProbabilities(
          eightyPercent = AugurFeesFeeRate(feeRate = 5f),
          ninetyFivePercent = AugurFeesFeeRate(feeRate = 10f)
        )
      ),
      sixBlocks = AugurFeesTimeEstimate(
        probabilities = AugurFeesProbabilities(
          eightyPercent = AugurFeesFeeRate(feeRate = 3f)
        )
      )
    ),
    mempoolUpdateTime = "2024-01-15T10:30:00Z"
  )
)

// Missing three blocks data
private val responseWithMissingThreeBlocks = Json.encodeToString(
  AugurFeesResponse(
    estimates = AugurFeesEstimates(
      sixBlocks = AugurFeesTimeEstimate(
        probabilities = AugurFeesProbabilities(
          eightyPercent = AugurFeesFeeRate(feeRate = 3f)
        )
      )
    ),
    mempoolUpdateTime = "2024-01-15T10:30:00Z"
  )
)

private val responseWithMissingConfidenceLevels = Json.encodeToString(
  AugurFeesResponse(
    estimates = AugurFeesEstimates(
      threeBlocks = AugurFeesTimeEstimate(
        probabilities = AugurFeesProbabilities(
          fivePercent = AugurFeesFeeRate(feeRate = 10.0f)
          // Missing 80% and 95% confidence levels
        )
      )
    ),
    mempoolUpdateTime = "2024-01-15T10:30:00Z"
  )
)
