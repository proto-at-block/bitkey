package build.wallet.f8e.client

import build.wallet.account.analytics.AppInstallationDaoMock
import build.wallet.analytics.events.PlatformInfoProviderMock
import build.wallet.auth.*
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
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.debug.NetworkingDebugServiceFake
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.ktor.result.EmptyResponseBody
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.result.bodyResult
import build.wallet.platform.config.AppId
import build.wallet.platform.config.AppVariant.Development
import build.wallet.platform.config.AppVersion
import build.wallet.platform.data.MimeType
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.platform.settings.CountryCodeGuesserMock
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.getErrorOr
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestCaseOrder.Sequential
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldEndWith
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
  val deviceInfoProvider = DeviceInfoProviderMock()

  val networkReachabilityProvider = NetworkReachabilityProviderMock(turbines::create)
  val networkingDebugService = NetworkingDebugServiceFake()
  val f8eHttpClientProvider =
    F8eHttpClientProvider(
      appId = AppId("world.bitkey.test"),
      appVersion = AppVersion("2008.10.31"),
      appVariant = Development,
      platformInfoProvider = PlatformInfoProviderMock(),
      datadogTracerPluginProvider = DatadogTracerPluginProvider(datadogTracer),
      networkingDebugService = networkingDebugService,
      appInstallationDao = AppInstallationDaoMock(),
      countryCodeGuesser = CountryCodeGuesserMock()
    )

  val unauthenticatedMockEngine = InlineMockEngine()

  val unauthenticatedF8eHttpClientFactory = UnauthenticatedF8eHttpClientFactory(
    appVariant = Development,
    platformInfoProvider = PlatformInfoProviderMock(),
    datadogTracer = datadogTracer,
    deviceInfoProvider = deviceInfoProvider,
    appInstallationDao = AppInstallationDaoMock(),
    countryCodeGuesser = CountryCodeGuesserMock(),
    networkReachabilityProvider = networkReachabilityProvider,
    networkingDebugService = networkingDebugService,
    engine = unauthenticatedMockEngine.engine
  )
  val client =
    F8eHttpClientImpl(
      authTokensRepository = AuthTokensRepositoryMock(),
      deviceInfoProvider = deviceInfoProvider,
      proofOfPossessionPluginProvider =
        ProofOfPossessionPluginProvider(
          authTokensRepository = authTokensRepository,
          keyboxDao = fakeKeyboxDao,
          appAuthKeyMessageSigner = fakeAppAuthKeyMessageSigner
        ),
      unauthenticatedF8eHttpClient = UnauthenticatedOnlyF8eHttpClientImpl(
        appCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        unauthenticatedF8eHttpClientFactory = unauthenticatedF8eHttpClientFactory
      ),
      f8eHttpClientProvider = f8eHttpClientProvider,
      networkReachabilityProvider = networkReachabilityProvider,
      wsmVerifier = WsmVerifierMock()
    )

  test("datadog tracer plugin is installed and headers are sent") {
    launch {
      unauthenticatedMockEngine.handle {
        it.headers["a"] shouldBe "1"
        it.headers["b"] shouldBe "2"

        respond(
          content = ByteReadChannel("{}"),
          status = HttpStatusCode.OK,
          headers = headersOf(HttpHeaders.ContentType, MimeType.JSON.name)
        )
      }
    }

    client.unauthenticated()
      .bodyResult<EmptyResponseBody> {
        get("/soda/can") {
          withEnvironment(F8eEnvironment.Development)
        }
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
    client.unauthenticated().bodyResult<EmptyResponseBody> {
      get("/soda/can") {
        withEnvironment(F8eEnvironment.ForceOffline)
      }
    }.should {
      it.isErr.shouldBeTrue()
      it.error
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

  test("targeting plugin is installed and headers are sent") {
    launch {
      unauthenticatedMockEngine.handle { _ ->
        respond(
          content = ByteReadChannel("{}"),
          status = HttpStatusCode.OK,
          headers = headersOf(HttpHeaders.ContentType, MimeType.JSON.name)
        )
      }
    }

    val response = client.unauthenticated()
      .get("/soda/can") {
        withEnvironment(F8eEnvironment.Development)
      }

    val request = response.request
    request.headers["Bitkey-App-Installation-ID"].shouldNotBeNull().shouldBeEqual("local-id")
    request.headers["Bitkey-App-Version"].shouldNotBeNull().shouldBeEqual("2023.1.3")
    request.headers["Bitkey-Device-Region"].shouldNotBeNull().shouldBeEqual("US")
    request.headers["Bitkey-OS-Type"].shouldNotBeNull().shouldBeEqual("OS_TYPE_ANDROID")
    request.headers["Bitkey-OS-Version"].shouldNotBeNull().shouldBeEqual("version_num_1")

    networkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.awaitItem()

    response.status.isSuccess().shouldBeTrue()
    response.body<EmptyResponseBody>().shouldBe(EmptyResponseBody)
  }
})
