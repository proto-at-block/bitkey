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
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.client.plugins.withHardwareFactor
import build.wallet.f8e.debug.NetworkingDebugServiceFake
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.ktor.InlineMockEngine
import build.wallet.ktor.result.EmptyResponseBody
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.result.bodyResult
import build.wallet.platform.config.AppVariant.Development
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
import io.ktor.client.engine.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
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
  val authTokensService = AuthTokensServiceFake()

  beforeTest {
    authTokensService.reset()
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

  val authedNetworkReachabilityProvider =
    NetworkReachabilityProviderMock("authed", turbines::create)
  val unauthedNetworkReachabilityProvider =
    NetworkReachabilityProviderMock("unauthed", turbines::create)
  val networkingDebugService = NetworkingDebugServiceFake()

  val unauthenticatedMockEngine = InlineMockEngine()

  fun buildClient(overrideEngine: HttpClientEngine? = null): F8eHttpClientImpl {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    return F8eHttpClientImpl(
      authenticatedF8eHttpClient = AuthenticatedF8eHttpClientImpl(
        appCoroutineScope = scope,
        authenticatedF8eHttpClientFactory = AuthenticatedF8eHttpClientFactory(
          appVariant = Development,
          platformInfoProvider = PlatformInfoProviderMock(),
          authTokensService = authTokensService,
          datadogTracer = datadogTracer,
          deviceInfoProvider = deviceInfoProvider,
          keyboxDao = fakeKeyboxDao,
          appAuthKeyMessageSigner = fakeAppAuthKeyMessageSigner,
          appInstallationDao = AppInstallationDaoMock(),
          countryCodeGuesser = CountryCodeGuesserMock(),
          networkReachabilityProvider = authedNetworkReachabilityProvider,
          networkingDebugService = networkingDebugService,
          engine = overrideEngine
        )
      ),
      unauthenticatedF8eHttpClient = UnauthenticatedOnlyF8eHttpClientImpl(
        appCoroutineScope = scope,
        unauthenticatedF8eHttpClientFactory = UnauthenticatedF8eHttpClientFactory(
          appVariant = Development,
          platformInfoProvider = PlatformInfoProviderMock(),
          datadogTracer = datadogTracer,
          deviceInfoProvider = deviceInfoProvider,
          appInstallationDao = AppInstallationDaoMock(),
          countryCodeGuesser = CountryCodeGuesserMock(),
          networkReachabilityProvider = unauthedNetworkReachabilityProvider,
          networkingDebugService = networkingDebugService,
          engine = unauthenticatedMockEngine.engine
        )
      ),
      wsmVerifier = WsmVerifierMock()
    )
  }

  lateinit var client: F8eHttpClientImpl

  afterTest {
    client.authenticated().close()
    client.unauthenticated().close()
  }

  test("datadog tracer plugin is installed and headers are sent") {
    client = buildClient()
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

    unauthedNetworkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.awaitItem()

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
    client = buildClient(engine)

    client.authenticated()
      .bodyResult<EmptyResponseBody> {
        put("/1234/soda/can") {
          withEnvironment(F8eEnvironment.Development)
          withAccountId(FullAccountId("1234"))
        }
      }

    authedNetworkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.awaitItem()

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
    val tokens = AccountAuthTokens(
      AccessToken("access-token"),
      RefreshToken("refresh-token")
    )
    authTokensService.setTokens(FullAccountId("1234"), tokens, AuthTokenScope.Global)

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
    client = buildClient(engine)

    client.authenticated().bodyResult<EmptyResponseBody> {
      put("/1234/soda/can") {
        withEnvironment(F8eEnvironment.Development)
        withAccountId(FullAccountId("1234"))
        withHardwareFactor(HwFactorProofOfPossession("hw-signed-token"))
      }
    }

    authedNetworkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.awaitItem()

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
    val tokens = AccountAuthTokens(
      AccessToken("access-token"),
      RefreshToken("refresh-token")
    )
    authTokensService.setTokens(FullAccountId("1234"), tokens, AuthTokenScope.Global)

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
    client = buildClient(mockEngine)

    client.authenticated().bodyResult<EmptyResponseBody> {
      put("/1234/soda/can") {
        withEnvironment(F8eEnvironment.Development)
        withAccountId(FullAccountId("1234"))
      }
    }

    authedNetworkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.awaitItem()

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
    val tokens = AccountAuthTokens(
      AccessToken("access-token"),
      RefreshToken("refresh-token")
    )
    authTokensService.setTokens(FullAccountId("1234"), tokens, AuthTokenScope.Global)

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
    client = buildClient(mockEngine)

    client.authenticated().bodyResult<EmptyResponseBody> {
      put("/1234/soda/can") {
        withEnvironment(F8eEnvironment.Development)
        withAccountId(FullAccountId("1234"))
        withHardwareFactor(HwFactorProofOfPossession("hw-signed-token"))
      }
    }

    authedNetworkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.awaitItem()

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
    client = buildClient(MockEngine { respondOk() })
    client.authenticated().bodyResult<EmptyResponseBody> {
      put("/1234/soda/can") {
        withEnvironment(F8eEnvironment.Development)
        withAccountId(FullAccountId("1234"))
      }
    }

    authedNetworkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.awaitItem()
      .shouldBeTypeOf<NetworkReachabilityProviderMock.UpdateNetworkReachabilityForConnectionParams>()
      .apply {
        connection.shouldBeTypeOf<NetworkConnection.HttpClientNetworkConnection.F8e>()
        reachability.shouldBe(NetworkReachability.REACHABLE)
      }
  }

  test("Error response updates network status to UNREACHABLE") {
    client = buildClient(
      MockEngine {
        respondError(HttpStatusCode.ServiceUnavailable)
      }
    )
    client.authenticated().bodyResult<EmptyResponseBody> {
      put("/1234/soda/can") {
        withEnvironment(F8eEnvironment.Development)
        withAccountId(FullAccountId("1234"))
      }
    }

    authedNetworkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.awaitItem()
      .shouldBeTypeOf<NetworkReachabilityProviderMock.UpdateNetworkReachabilityForConnectionParams>()
      .apply {
        connection.shouldBeTypeOf<NetworkConnection.HttpClientNetworkConnection.F8e>()
        reachability.shouldBe(NetworkReachability.UNREACHABLE)
      }
  }

  test("F8e calls disabled when environment is set to ForceOffline - Unauthenticated") {
    client = buildClient()
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
    unauthedNetworkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.awaitItem()
      .shouldBeTypeOf<NetworkReachabilityProviderMock.UpdateNetworkReachabilityForConnectionParams>()
      .apply {
        connection.shouldBeTypeOf<NetworkConnection.HttpClientNetworkConnection.F8e>()
        reachability.shouldBe(NetworkReachability.UNREACHABLE)
      }
  }

  test("F8e calls disabled when environment is set to ForceOffline - Authenticated") {
    val engine = MockEngine { respondOk() }
    client = buildClient(engine)
    client.authenticated().bodyResult<EmptyResponseBody> {
      get("/soda/can") {
        withAccountId(FullAccountId("1234"))
        withEnvironment(F8eEnvironment.ForceOffline)
      }
    }.should {
      it.getErrorOr(null)
        .shouldNotBeNull()
        .shouldBeTypeOf<HttpError.UnhandledException>()
        .cause.shouldBeTypeOf<OfflineOperationException>()
    }
    authedNetworkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.awaitItem()
      .shouldBeTypeOf<NetworkReachabilityProviderMock.UpdateNetworkReachabilityForConnectionParams>()
      .apply {
        connection.shouldBeTypeOf<NetworkConnection.HttpClientNetworkConnection.F8e>()
        reachability.shouldBe(NetworkReachability.UNREACHABLE)
      }
  }
  test("retry requests with socket timeout exceptions") {
    var requestCount = 0
    client = buildClient(
      MockEngine {
        if (requestCount == 0) {
          requestCount += 1
          throw SocketTimeoutException(it)
        } else {
          respondOk("hello!")
        }
      }
    )

    client.authenticated().get("/soda/can") {
      withEnvironment(F8eEnvironment.Development)
      withAccountId(FullAccountId("1234"))
    }.status.shouldBe(HttpStatusCode.OK)

    requestCount.shouldBe(1)

    authedNetworkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.cancelAndIgnoreRemainingEvents()
  }

  test("targeting plugin is installed and headers are sent") {
    client = buildClient()
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

    unauthedNetworkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.awaitItem()

    response.status.isSuccess().shouldBeTrue()
    response.body<EmptyResponseBody>().shouldBe(EmptyResponseBody)
  }
})
