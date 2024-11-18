package build.wallet.f8e.client

import build.wallet.account.analytics.AppInstallationDao
import build.wallet.analytics.events.PlatformInfoProvider
import build.wallet.availability.NetworkReachabilityProvider
import build.wallet.datadog.DatadogTracer
import build.wallet.f8e.client.plugins.*
import build.wallet.f8e.debug.NetworkingDebugService
import build.wallet.f8e.logging.F8eHttpClientLogger
import build.wallet.logging.log
import build.wallet.platform.config.AppVariant
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.settings.CountryCodeGuesser
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

/**
 * The base factory for producing [HttpClient]s for the F8e service.
 *
 * [HttpClient]s used for F8e currently exist for two distinct contexts:
 * Authenticated and Unauthenticated.
 * This Factory collects common dependencies and configuration shared
 * between both contexts and provides [AuthenticatedF8eHttpClientFactory]
 * and [UnauthenticatedF8eHttpClientFactory] to preserve the existing
 * separation.
 *
 * In the future, this class will be final and be used to produce a
 * single [HttpClient] used in both contexts.
 */
abstract class BaseF8eHttpClientFactory(
  private val appVariant: AppVariant,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val platformInfoProvider: PlatformInfoProvider,
  private val datadogTracer: DatadogTracer,
  private val appInstallationDao: AppInstallationDao,
  private val countryCodeGuesser: CountryCodeGuesser,
  private val networkReachabilityProvider: NetworkReachabilityProvider?,
  private val networkingDebugService: NetworkingDebugService,
  private val engine: HttpClientEngine?,
) {
  private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    /**
     * With this set to `true`, deserialization won't fail for properties with a default value,
     * when one of the two happens:
     * - JSON value is null but the property type is non-nullable.
     * - Property type is an enum type, but JSON value contains unknown enum member.
     */
    coerceInputValues = true
  }

  suspend fun createClient(): HttpClient {
    return withContext(Dispatchers.IO) {
      if (engine == null) {
        HttpClient { configureClient(this) }
      } else {
        HttpClient(engine) { configureClient(this) }
      }
    }
  }

  open fun configureClient(config: HttpClientConfig<*>) =
    with(config) {
      val factory = this@BaseF8eHttpClientFactory
      install(F8eHttpClientLogger) {
        tag = "F8e"
        debug = appVariant == AppVariant.Development
      }

      install(ContentNegotiation) {
        json(json)
      }

      install(HttpTimeout) {
        socketTimeoutMillis = 15.seconds.inWholeMilliseconds
      }

      install(HttpRequestRetry) {
        maxRetries = 2
        retryOnExceptionIf { _, cause ->
          cause is SocketTimeoutException
        }
        modifyRequest {
          log { "retrying request: $request, retry count $retryCount" }
        }
      }

      defaultRequest {
        attributes.put(CheckReachabilityAttribute, true)
        attributes.put(DeviceInfoAttribute, deviceInfoProvider.getDeviceInfo())
        attributes.put(PlatformInfoAttribute, platformInfoProvider.getPlatformInfo())
      }

      install(CommonRequestConfigPlugin)
      install(ForceOfflinePlugin)
      install(FailF8eRequestsPlugin) {
        networkingDebugService = factory.networkingDebugService
      }
      factory.networkReachabilityProvider?.let { optionalReachabilityProvider ->
        install(NetworkReachabilityPlugin) {
          networkReachabilityProvider = optionalReachabilityProvider
        }
      }
      install(TargetingHeadersPlugin) {
        appInstallationDao = factory.appInstallationDao
        countryCodeGuesser = factory.countryCodeGuesser
      }
      install(DatadogTracerPlugin) {
        datadogTracer = factory.datadogTracer
      }
    }
}
