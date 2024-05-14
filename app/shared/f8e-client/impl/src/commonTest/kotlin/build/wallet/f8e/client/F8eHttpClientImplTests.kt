package build.wallet.f8e.client

import build.wallet.analytics.events.PlatformInfoProviderMock
import build.wallet.auth.AccessToken
import build.wallet.auth.AccountAuthTokens
import build.wallet.auth.AppAuthKeyMessageSignerMock
import build.wallet.auth.AuthTokensRepositoryMock
import build.wallet.auth.RefreshToken
import build.wallet.availability.NetworkConnection
import build.wallet.availability.NetworkReachability
import build.wallet.availability.NetworkReachabilityProviderMock
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.datadog.DatadogSpan
import build.wallet.datadog.DatadogTracer
import build.wallet.datadog.TracerHeaders
import build.wallet.encrypt.WsmVerifierMock
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.client.F8eHttpClientImpl.Companion.CONSTANT_PROOF_OF_POSSESSION_APP_HEADER
import build.wallet.f8e.client.F8eHttpClientImpl.Companion.CONSTANT_PROOF_OF_POSSESSION_HW_HEADER
import build.wallet.f8e.debug.NetworkingDebugConfigRepositoryFake
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.ktor.result.EmptyResponseBody
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.result.bodyResult
import build.wallet.platform.config.AppId
import build.wallet.platform.config.AppVariant.Development
import build.wallet.platform.data.MimeType
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.getErrorOr
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestCaseOrder.Sequential
import io.kotest.matchers.collections.shouldEndWith
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel

private data class StubDatadogSpan(
  override var resourceName: String?,
  val tags: MutableMap<String, String> = mutableMapOf(),
  var finished: Boolean = false,
  var cause: Throwable? = null,
) : DatadogSpan {
  override fun setTag(
    key: String,
    value: String,
  ) {
    tags[key] = value
  }

  override fun finish() {
    finished = true
  }

  override fun finish(cause: Throwable) = finish().also { this.cause = cause }
}

class F8eHttpClientImplTests : FunSpec({
  testOrder = Sequential

  val fakeAppAuthKeyMessageSigner = AppAuthKeyMessageSignerMock()
  val fakeKeyboxDao = KeyboxDaoMock(turbines::create, KeyboxMock)
  val authTokensRepository = AuthTokensRepositoryMock()

  beforeTest {
    authTokensRepository.reset()
  }

  val datadogTracer =
    object : DatadogTracer {
      var spans = mutableListOf<StubDatadogSpan>()

      override fun buildSpan(spanName: String) = StubDatadogSpan(spanName).also { spans += it }

      override fun buildSpan(
        spanName: String,
        parentSpan: DatadogSpan,
      ): DatadogSpan {
        TODO("No sub-span is used this test")
      }

      override fun inject(span: DatadogSpan) =
        TracerHeaders(
          mapOf(
            "a" to "1",
            "b" to "2"
          )
        )
    }

  val networkReachabilityProvider = NetworkReachabilityProviderMock(turbines::create)
  val f8eHttpClientProvider =
    F8eHttpClientProvider(
      appId = AppId("world.bitkey.test"),
      appVersion = "2008.10.31",
      appVariant = Development,
      platformInfoProvider = PlatformInfoProviderMock(),
      datadogTracerPluginProvider = DatadogTracerPluginProvider(datadogTracer),
      networkingDebugConfigRepository = NetworkingDebugConfigRepositoryFake()
    )

  val client =
    F8eHttpClientImpl(
      authTokensRepository = AuthTokensRepositoryMock(),
      proofOfPossessionPluginProvider =
        ProofOfPossessionPluginProvider(
          authTokensRepository = authTokensRepository,
          keyboxDao = fakeKeyboxDao,
          appAuthKeyMessageSigner = fakeAppAuthKeyMessageSigner
        ),
      unauthenticatedF8eHttpClient =
        UnauthenticatedOnlyF8eHttpClientImpl(
          f8eHttpClientProvider = f8eHttpClientProvider,
          networkReachabilityProvider = networkReachabilityProvider
        ),
      f8eHttpClientProvider = f8eHttpClientProvider,
      networkReachabilityProvider = networkReachabilityProvider,
      wsmVerifier = WsmVerifierMock()
    )

  test("datadog tracer plugin is installed and headers are sent") {
    val engine =
      MockEngine {
        it.headers["a"] shouldBe "1"
        it.headers["b"] shouldBe "2"

        respond(
          content = ByteReadChannel("{}"),
          status = HttpStatusCode.OK,
          headers = headersOf(HttpHeaders.ContentType, MimeType.JSON.name)
        )
      }

    client.unauthenticated(
      f8eEnvironment = F8eEnvironment.Development,
      engine = engine
    )
      .bodyResult<EmptyResponseBody> {
        get("/soda/can")
      }

    networkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.awaitItem()

    datadogTracer.spans shouldEndWith
      StubDatadogSpan(
        resourceName = "/soda/can",
        tags =
          mutableMapOf(
            "http.method" to "GET",
            "http.url" to "https://api.dev.wallet.build/soda/can",
            "http.status_code" to "200",
            "http.version" to "HTTP/1.1"
          ),
        finished = true
      )
  }

  test("account id is removed from request url tracing") {
    val engine =
      MockEngine {
        it.headers["a"] shouldBe "1"
        it.headers["b"] shouldBe "2"

        respond(
          content = ByteReadChannel("{}"),
          status = HttpStatusCode.OK,
          headers = headersOf(HttpHeaders.ContentType, MimeType.JSON.name)
        )
      }

    client.authenticated(
      f8eEnvironment = F8eEnvironment.Development,
      engine = engine,
      accountId = FullAccountId("1234")
    ).bodyResult<EmptyResponseBody> {
      put("/1234/soda/can")
    }

    networkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.awaitItem()

    datadogTracer.spans shouldEndWith
      StubDatadogSpan(
        resourceName = "/:account_id/soda/can",
        tags =
          mutableMapOf(
            "http.method" to "PUT",
            "http.url" to "https://api.dev.wallet.build/:account_id/soda/can",
            "http.status_code" to "200",
            "http.version" to "HTTP/1.1"
          ),
        finished = true
      )
  }

  test("hw-factor proof of possession adds proper headers") {
    authTokensRepository.authTokensResult =
      Ok(
        AccountAuthTokens(
          AccessToken("access-token"),
          RefreshToken("refresh-token")
        )
      )

    val engine =
      MockEngine {
        it.headers[CONSTANT_PROOF_OF_POSSESSION_HW_HEADER] shouldBe "hw-signed-token"
        it.headers[CONSTANT_PROOF_OF_POSSESSION_APP_HEADER] shouldBe null

        respond(
          content = ByteReadChannel("{}"),
          status = HttpStatusCode.OK,
          headers = headersOf(HttpHeaders.ContentType, MimeType.JSON.name)
        )
      }

    client.authenticated(
      f8eEnvironment = F8eEnvironment.Development,
      engine = engine,
      accountId = FullAccountId("1234"),
      hwFactorProofOfPossession = HwFactorProofOfPossession("hw-signed-token")
    ).bodyResult<EmptyResponseBody> {
      put("/1234/soda/can")
    }

    networkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.awaitItem()

    datadogTracer.spans shouldEndWith
      StubDatadogSpan(
        resourceName = "/:account_id/soda/can",
        tags =
          mutableMapOf(
            "http.method" to "PUT",
            "http.url" to "https://api.dev.wallet.build/:account_id/soda/can",
            "http.status_code" to "200",
            "http.version" to "HTTP/1.1"
          ),
        finished = true
      )
  }

  test("app-factor proof of possession adds proper headers") {
    fakeAppAuthKeyMessageSigner.result = Ok("signed-access-token")

    val mockEngine =
      MockEngine {
        it.headers[CONSTANT_PROOF_OF_POSSESSION_APP_HEADER] shouldBe "signed-access-token"
        it.headers[CONSTANT_PROOF_OF_POSSESSION_HW_HEADER] shouldBe null

        respond(
          content = ByteReadChannel("{}"),
          status = HttpStatusCode.OK,
          headers = headersOf(HttpHeaders.ContentType, MimeType.JSON.name)
        )
      }

    client.authenticated(
      f8eEnvironment = F8eEnvironment.Development,
      engine = mockEngine,
      accountId = FullAccountId("1234")
    ).bodyResult<EmptyResponseBody> {
      put("/1234/soda/can")
    }

    networkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.awaitItem()

    datadogTracer.spans shouldEndWith
      StubDatadogSpan(
        resourceName = "/:account_id/soda/can",
        tags =
          mutableMapOf(
            "http.method" to "PUT",
            "http.url" to "https://api.dev.wallet.build/:account_id/soda/can",
            "http.status_code" to "200",
            "http.version" to "HTTP/1.1"
          ),
        finished = true
      )
  }

  test("two-factor proof of possession adds proper headers") {
    fakeAppAuthKeyMessageSigner.result = Ok("signed-access-token")

    val mockEngine =
      MockEngine {
        it.headers[CONSTANT_PROOF_OF_POSSESSION_HW_HEADER] shouldBe "hw-signed-token"
        it.headers[CONSTANT_PROOF_OF_POSSESSION_APP_HEADER] shouldBe "signed-access-token"

        respond(
          content = ByteReadChannel("{}"),
          status = HttpStatusCode.OK,
          headers = headersOf(HttpHeaders.ContentType, MimeType.JSON.name)
        )
      }

    client.authenticated(
      f8eEnvironment = F8eEnvironment.Development,
      engine = mockEngine,
      accountId = FullAccountId("1234"),
      hwFactorProofOfPossession = HwFactorProofOfPossession("hw-signed-token")
    ).bodyResult<EmptyResponseBody> {
      put("/1234/soda/can")
    }

    networkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.awaitItem()

    datadogTracer.spans shouldEndWith
      StubDatadogSpan(
        resourceName = "/:account_id/soda/can",
        tags =
          mutableMapOf(
            "http.method" to "PUT",
            "http.url" to "https://api.dev.wallet.build/:account_id/soda/can",
            "http.status_code" to "200",
            "http.version" to "HTTP/1.1"
          ),
        finished = true
      )
  }

  test("OK response updates network status to REACHABLE") {
    client.authenticated(
      f8eEnvironment = F8eEnvironment.Development,
      engine = MockEngine { respondOk() },
      accountId = FullAccountId("1234")
    ).bodyResult<EmptyResponseBody> {
      put("/1234/soda/can")
    }

    networkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.awaitItem()
      .shouldBeTypeOf<NetworkReachabilityProviderMock.UpdateNetworkReachabilityForConnectionParams>()
      .apply {
        connection.shouldBeTypeOf<NetworkConnection.HttpClientNetworkConnection.F8e>()
        reachability.shouldBe(NetworkReachability.REACHABLE)
      }
  }

  test("Error response updates network status to UNREACHABLE") {
    client.authenticated(
      f8eEnvironment = F8eEnvironment.Development,
      engine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) },
      accountId = FullAccountId("1234")
    ).bodyResult<EmptyResponseBody> {
      put("/1234/soda/can")
    }

    networkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.awaitItem()
      .shouldBeTypeOf<NetworkReachabilityProviderMock.UpdateNetworkReachabilityForConnectionParams>()
      .apply {
        connection.shouldBeTypeOf<NetworkConnection.HttpClientNetworkConnection.F8e>()
        reachability.shouldBe(NetworkReachability.UNREACHABLE)
      }
  }

  test("F8e calls disabled when environment is set to ForceOffline - Unauthenticated") {
    val engine = MockEngine { respondOk() }

    client.unauthenticated(
      f8eEnvironment = F8eEnvironment.ForceOffline,
      engine = engine
    ).bodyResult<EmptyResponseBody> {
      get("/soda/can")
    }.should {
      it.getErrorOr(null)
        .shouldNotBeNull()
        .shouldBeTypeOf<HttpError.UnhandledException>()
        .cause.shouldBeTypeOf<OfflineOperationException>()
    }
    networkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.awaitItem()
      .shouldBeTypeOf<NetworkReachabilityProviderMock.UpdateNetworkReachabilityForConnectionParams>()
      .apply {
        connection.shouldBeTypeOf<NetworkConnection.HttpClientNetworkConnection.F8e>()
        reachability.shouldBe(NetworkReachability.UNREACHABLE)
      }
  }

  test("F8e calls disabled when environment is set to ForceOffline - Authenticated") {
    val engine = MockEngine { respondOk() }

    client.authenticated(
      f8eEnvironment = F8eEnvironment.ForceOffline,
      engine = engine,
      accountId = FullAccountId("1234")
    ).bodyResult<EmptyResponseBody> {
      get("/soda/can")
    }.should {
      it.getErrorOr(null)
        .shouldNotBeNull()
        .shouldBeTypeOf<HttpError.UnhandledException>()
        .cause.shouldBeTypeOf<OfflineOperationException>()
    }
    networkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.awaitItem()
      .shouldBeTypeOf<NetworkReachabilityProviderMock.UpdateNetworkReachabilityForConnectionParams>()
      .apply {
        connection.shouldBeTypeOf<NetworkConnection.HttpClientNetworkConnection.F8e>()
        reachability.shouldBe(NetworkReachability.UNREACHABLE)
      }
  }
})
