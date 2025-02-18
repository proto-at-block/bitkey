package build.wallet.f8e

import app.cash.turbine.Turbine
import build.wallet.account.analytics.AppInstallationDaoMock
import build.wallet.analytics.events.PlatformInfoProviderMock
import build.wallet.auth.AppAuthKeyMessageSignerMock
import build.wallet.auth.AuthTokensServiceFake
import build.wallet.availability.NetworkReachabilityProviderMock
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.datadog.DatadogSpan
import build.wallet.datadog.DatadogTracer
import build.wallet.datadog.TracerHeaders
import build.wallet.encrypt.WsmVerifierMock
import build.wallet.f8e.client.*
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.debug.NetworkingDebugServiceFake
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.ktor.result.EmptyResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.platform.config.AppId
import build.wallet.platform.config.AppVariant.Development
import build.wallet.platform.config.AppVersion
import build.wallet.platform.data.MimeType
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.platform.settings.CountryCodeGuesserMock
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

@State(Scope.Benchmark)
open class F8eHttpClientImplBenchmarks {
  private val fakeAppAuthKeyMessageSigner = AppAuthKeyMessageSignerMock()
  private val fakeKeyboxDao = KeyboxDaoMock({ Turbine() }, KeyboxMock)
  private val authTokensRepository = AuthTokensServiceFake()

  private val engine =
    MockEngine {
      it.headers["a"] shouldBe "1"
      it.headers["b"] shouldBe "2"

      respond(
        content = ByteReadChannel("{}"),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, MimeType.JSON.name)
      )
    }

  private val datadogTracer =
    object : DatadogTracer {
      override fun buildSpan(spanName: String): DatadogSpan {
        return object : DatadogSpan {
          override var resourceName: String? = spanName

          override fun setTag(
            key: String,
            value: String,
          ) = Unit

          override fun finish() = Unit

          override fun finish(cause: Throwable) = Unit
        }
      }

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
  private val deviceInfoProvider = DeviceInfoProviderMock()

  private val networkReachabilityProvider = NetworkReachabilityProviderMock { Turbine() }
  private val f8eHttpClientProvider =
    F8eHttpClientProvider(
      appId = AppId("world.bitkey.test"),
      appVersion = AppVersion("2008.10.31"),
      appVariant = Development,
      platformInfoProvider = PlatformInfoProviderMock(),
      datadogTracerPluginProvider = DatadogTracerPluginProvider(datadogTracer),
      networkingDebugService = NetworkingDebugServiceFake(),
      appInstallationDao = AppInstallationDaoMock(),
      countryCodeGuesser = CountryCodeGuesserMock()
    )

  private val unauthenticatedF8eHttpClientFactory =
    UnauthenticatedF8eHttpClientFactory(
      appVariant = Development,
      platformInfoProvider = PlatformInfoProviderMock(),
      datadogTracer = datadogTracer,
      deviceInfoProvider = deviceInfoProvider,
      networkReachabilityProvider = NetworkReachabilityProviderMock { Turbine(name = it) },
      appInstallationDao = AppInstallationDaoMock(),
      countryCodeGuesser = CountryCodeGuesserMock(),
      networkingDebugService = NetworkingDebugServiceFake(),
      engine = engine
    )

  private val client =
    F8eHttpClientImpl(
      authTokensRepository = AuthTokensServiceFake(),
      deviceInfoProvider = deviceInfoProvider,
      proofOfPossessionPluginProvider =
        ProofOfPossessionPluginProvider(
          authTokensRepository = authTokensRepository,
          keyboxDao = fakeKeyboxDao,
          appAuthKeyMessageSigner = fakeAppAuthKeyMessageSigner
        ),
      unauthenticatedF8eHttpClient =
        UnauthenticatedOnlyF8eHttpClientImpl(
          appCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
          unauthenticatedF8eHttpClientFactory = unauthenticatedF8eHttpClientFactory
        ),
      f8eHttpClientProvider = f8eHttpClientProvider,
      networkReachabilityProvider = networkReachabilityProvider,
      wsmVerifier = WsmVerifierMock()
    )

  @Setup
  fun prepare() {
    authTokensRepository.reset()
  }

  @Benchmark
  fun unauthenticatedRequest() {
    @Suppress("ForbiddenMethodCall")
    runBlocking {
      client.unauthenticated()
        .bodyResult<EmptyResponseBody> {
          get("/soda/can") {
            withEnvironment(F8eEnvironment.Development)
          }
        }
    }
  }

  @Benchmark
  fun authenticatedRequest() {
    @Suppress("ForbiddenMethodCall")
    runBlocking {
      client.authenticated(
        f8eEnvironment = F8eEnvironment.Development,
        engine = engine,
        accountId = FullAccountId("1234")
      ).bodyResult<EmptyResponseBody> {
        put("/1234/soda/can")
      }
    }
  }
}
