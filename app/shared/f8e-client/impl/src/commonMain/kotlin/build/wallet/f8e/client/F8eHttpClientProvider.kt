package build.wallet.f8e.client

import build.wallet.account.analytics.AppInstallation
import build.wallet.account.analytics.AppInstallationDao
import build.wallet.analytics.events.PlatformInfoProvider
import build.wallet.bitkey.f8e.AccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.client.plugins.FailF8eRequestsPlugin
import build.wallet.f8e.debug.NetworkingDebugService
import build.wallet.f8e.logging.F8eHttpClientLogger
import build.wallet.logging.*
import build.wallet.platform.config.AppId
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVersion
import build.wallet.platform.settings.CountryCodeGuesser
import com.github.michaelbull.result.get
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

@BitkeyInject(AppScope::class)
class F8eHttpClientProvider(
  private val appId: AppId,
  private val appVersion: AppVersion,
  private val appVariant: AppVariant,
  private val platformInfoProvider: PlatformInfoProvider,
  private val datadogTracerPluginProvider: DatadogTracerPluginProvider,
  private val networkingDebugService: NetworkingDebugService,
  private val appInstallationDao: AppInstallationDao,
  private val countryCodeGuesser: CountryCodeGuesser,
) {
  private var appInstallation: AppInstallation? = null

  suspend fun getHttpClient(
    engine: HttpClientEngine? = null,
    block: HttpClientConfig<*>.() -> Unit,
  ): HttpClient {
    appInstallation = appInstallationDao.getOrCreateAppInstallation().get()

    // HttpClient constructor performs some IO work under the hood - this was caught as a
    // DiskReadViolation on Android, so we call it on IO dispatcher.
    return withContext(Dispatchers.IO) {
      engine?.let {
        HttpClient(engine = engine, block)
      } ?: run {
        HttpClient(block)
      }
    }
  }

  fun configureCommon(
    config: HttpClientConfig<*>,
    accountId: AccountId?,
  ) {
    config.install(F8eHttpClientLogger) {
      tag = "F8e"
      debug = appVariant == AppVariant.Development
    }

    config.install(ContentNegotiation) {
      json(
        Json {
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
      )
    }

    config.install(HttpTimeout) {
      socketTimeoutMillis = 60.seconds.inWholeMilliseconds
    }

    config.install(HttpRequestRetry) {
      maxRetries = 2
      retryOnExceptionIf { _, cause ->
        cause is SocketTimeoutException
      }
      modifyRequest {
        logDebug { "retrying request: $request, retry count $retryCount" }
      }
    }

    config.install(UserAgent) {
      val platformInfo = platformInfoProvider.getPlatformInfo()
      agent =
        "${appId.value}/${appVersion.value} ${platformInfo.device_make} (${platformInfo.device_model}; ${platformInfo.os_type.name}/${platformInfo.os_version})"
    }

    config.install(datadogTracerPluginProvider.getPlugin(accountId = accountId))
    config.install(FailF8eRequestsPlugin) {
      networkingDebugService = this@F8eHttpClientProvider.networkingDebugService
    }

    config.install(
      TargetingHeadersPluginProvider(
        appInstallation = appInstallation,
        deviceRegion = countryCodeGuesser.countryCode(),
        platformInfo = platformInfoProvider.getPlatformInfo()
      ).getPlugin()
    )
  }
}
