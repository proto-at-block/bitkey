package build.wallet.f8e

import app.cash.turbine.Turbine
import bitkey.datadog.DatadogSpan
import bitkey.datadog.DatadogTracer
import bitkey.datadog.TracerHeaders
import build.wallet.account.analytics.AppInstallationDaoMock
import build.wallet.analytics.events.PlatformInfoProviderMock
import build.wallet.auth.AppAuthKeyMessageSignerMock
import build.wallet.auth.AuthTokensServiceFake
import build.wallet.availability.NetworkReachabilityProviderMock
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.encrypt.WsmVerifierMock
import build.wallet.f8e.client.*
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.debug.NetworkingDebugServiceFake
import build.wallet.firmware.FirmwareDeviceInfoDaoMock
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.ktor.result.EmptyResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.platform.config.AppVariant.Development
import build.wallet.platform.data.MimeType
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.platform.settings.CountryCodeGuesserMock
import build.wallet.time.ClockFake
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
  private val authTokensService = AuthTokensServiceFake()
  private val firmwareDeviceInfoDao = FirmwareDeviceInfoDaoMock { Turbine() }

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

  private val authedNetworkReachabilityProvider = NetworkReachabilityProviderMock("authed") {
    Turbine()
  }
  private val unauthedNetworkReachabilityProvider = NetworkReachabilityProviderMock("unauthed") {
    Turbine()
  }

  private val unauthenticatedF8eHttpClientFactory =
    UnauthenticatedF8eHttpClientFactory(
      appVariant = Development,
      platformInfoProvider = PlatformInfoProviderMock(),
      datadogTracer = datadogTracer,
      deviceInfoProvider = deviceInfoProvider,
      appInstallationDao = AppInstallationDaoMock(),
      firmwareDeviceInfoDao = firmwareDeviceInfoDao,
      countryCodeGuesser = CountryCodeGuesserMock(),
      networkReachabilityProvider = unauthedNetworkReachabilityProvider,
      networkingDebugService = NetworkingDebugServiceFake(),
      engine = engine
    )

  private val authenticatedF8eHttpClientFactory =
    AuthenticatedF8eHttpClientFactory(
      appVariant = Development,
      platformInfoProvider = PlatformInfoProviderMock(),
      authTokensService = authTokensService,
      datadogTracer = datadogTracer,
      deviceInfoProvider = deviceInfoProvider,
      keyboxDao = fakeKeyboxDao,
      appAuthKeyMessageSigner = fakeAppAuthKeyMessageSigner,
      appInstallationDao = AppInstallationDaoMock(),
      firmwareDeviceInfoDao = firmwareDeviceInfoDao,
      countryCodeGuesser = CountryCodeGuesserMock(),
      networkReachabilityProvider = authedNetworkReachabilityProvider,
      networkingDebugService = NetworkingDebugServiceFake(),
      engine = engine,
      clock = ClockFake()
    )

  private val client =
    F8eHttpClientImpl(
      authenticatedF8eHttpClient = AuthenticatedF8eHttpClientImpl(
        appCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        authenticatedF8eHttpClientFactory = authenticatedF8eHttpClientFactory
      ),
      unauthenticatedF8eHttpClient = UnauthenticatedOnlyF8eHttpClientImpl(
        appCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        unauthenticatedF8eHttpClientFactory = unauthenticatedF8eHttpClientFactory
      ),
      wsmVerifier = WsmVerifierMock()
    )

  @Setup
  fun prepare() {
    @Suppress("ForbiddenMethodCall")
    runBlocking {
      authTokensService.reset()
      firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)
    }
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
      client.authenticated().bodyResult<EmptyResponseBody> {
        put("/1234/soda/can") {
          withEnvironment(F8eEnvironment.Development)
          withAccountId(FullAccountId("1234"))
        }
      }
    }
  }
}
