package build.wallet.bitcoin.fees

import build.wallet.availability.NetworkReachabilityProviderMock
import build.wallet.bitcoin.BitcoinNetworkType.*
import build.wallet.bitcoin.fees.MempoolHttpClientImpl.MempoolResponse
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

class MempoolHttpClientImplTests : FunSpec({
  val networkReachabilityProvider = NetworkReachabilityProviderMock("mempool", turbines::create)

  fun createTestClient(mockEngine: HttpClientEngine): MempoolHttpClient {
    return MempoolHttpClientImpl(networkReachabilityProvider, mockEngine)
  }

  test("successful fee rate request for FASTEST priority on BITCOIN network") {
    val mockEngine = MockEngine { request ->
      request.url.encodedPath shouldBe "/api/v1/fees/recommended"
      request.method shouldBe HttpMethod.Get
      request.headers[HttpHeaders.Accept] shouldBe "application/json"
      request.url.host shouldBe "bitkey.mempool.space"

      respond(
        content = ByteReadChannel(successfulMempoolResponse),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
      )
    }

    val client = createTestClient(mockEngine)
    val result = client.getMempoolFeeRate(BITCOIN, FASTEST)

    result.isOk.shouldBeTrue()
    result.getOrThrow().satsPerVByte shouldBe 25.0f
  }

  test("successful fee rate request for THIRTY_MINUTES priority on TESTNET network") {
    val mockEngine = MockEngine { request ->
      request.url.encodedPath shouldBe "/testnet/api/v1/fees/recommended"
      request.method shouldBe HttpMethod.Get
      request.url.host shouldBe "bitkey.mempool.space"

      respond(
        content = ByteReadChannel(successfulMempoolResponse),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
      )
    }

    val client = createTestClient(mockEngine)
    val result = client.getMempoolFeeRate(TESTNET, THIRTY_MINUTES)

    result.isOk.shouldBeTrue()
    result.getOrThrow().satsPerVByte shouldBe 15.0f
  }

  test("successful fee rate request for SIXTY_MINUTES priority on SIGNET network") {
    val mockEngine = MockEngine { request ->
      request.url.encodedPath shouldBe "/signet/api/v1/fees/recommended"
      request.method shouldBe HttpMethod.Get
      request.url.host shouldBe "bitkey.mempool.space"

      respond(
        content = ByteReadChannel(successfulMempoolResponse),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
      )
    }

    val client = createTestClient(mockEngine)
    val result = client.getMempoolFeeRate(SIGNET, SIXTY_MINUTES)

    result.isOk.shouldBeTrue()
    result.getOrThrow().satsPerVByte shouldBe 10.0f
  }

  test("successful get all fee rates request on BITCOIN network") {
    val mockEngine = MockEngine { request ->
      request.url.encodedPath shouldBe "/api/v1/fees/recommended"
      request.method shouldBe HttpMethod.Get
      request.url.host shouldBe "bitkey.mempool.space"

      respond(
        content = ByteReadChannel(successfulMempoolResponse),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
      )
    }

    val client = createTestClient(mockEngine)
    val result = client.getMempoolFeeRates(BITCOIN)

    result.isOk.shouldBeTrue()
    val feeRates = result.getOrThrow()
    feeRates.fastestFeeRate.satsPerVByte shouldBe 25.0f
    feeRates.halfHourFeeRate.satsPerVByte shouldBe 15.0f
    feeRates.hourFeeRate.satsPerVByte shouldBe 10.0f
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
    val result = client.getMempoolFeeRate(BITCOIN, FASTEST)

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
    val result = client.getMempoolFeeRates(BITCOIN)

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
    val result = client.getMempoolFeeRate(BITCOIN, FASTEST)

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
    val result = client.getMempoolFeeRates(BITCOIN)

    result.isErr.shouldBeTrue()
  }

  test("validate request URL and headers") {
    val mockEngine = MockEngine { request ->
      request.url.protocol shouldBe URLProtocol.HTTPS
      request.url.host shouldBe "bitkey.mempool.space"
      request.url.encodedPath shouldBe "/api/v1/fees/recommended"
      request.method shouldBe HttpMethod.Get
      request.headers[HttpHeaders.Accept] shouldBe "application/json"
      request.headers[HttpHeaders.ContentType] shouldBe "application/json"

      respond(
        content = ByteReadChannel(successfulMempoolResponse),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
      )
    }

    val client = createTestClient(mockEngine)
    client.getMempoolFeeRate(BITCOIN, FASTEST)
  }

  test("handles missing required fields in response") {
    val mockEngine = MockEngine { request ->
      respond(
        content = ByteReadChannel("""{"economyFee": 5.0, "minimumFee": 1.0}"""),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
      )
    }

    val client = createTestClient(mockEngine)
    val result = client.getMempoolFeeRate(BITCOIN, FASTEST)

    result.isErr.shouldBeTrue()
  }
})

private val successfulMempoolResponse = Json.encodeToString(
  MempoolResponse(
    fastestFee = 25.0f,
    halfHourFee = 15.0f,
    hourFee = 10.0f,
    economyFee = 5.0f,
    minimumFee = 1.0f
  )
)
