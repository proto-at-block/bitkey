package build.wallet.f8e.client

import build.wallet.analytics.events.PlatformInfoProvider
import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.debug.NetworkingDebugConfigRepository
import build.wallet.ktor.result.client.KtorLogLevelPolicy
import build.wallet.ktor.result.client.installLogging
import build.wallet.logging.log
import build.wallet.platform.config.AppId
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

class F8eHttpClientProvider(
  private val appId: AppId,
  private val appVersion: String,
  private val platformInfoProvider: PlatformInfoProvider,
  private val ktorLogLevelPolicy: KtorLogLevelPolicy,
  private val datadogTracerPluginProvider: DatadogTracerPluginProvider,
  private val networkingDebugConfigRepository: NetworkingDebugConfigRepository,
) {
  suspend fun getHttpClient(
    engine: HttpClientEngine? = null,
    block: HttpClientConfig<*>.() -> Unit,
  ): HttpClient {
    // HttpClient constructor performs some IO work under the hood - this was caught as a
    // DiskReadViolation on Android, so we call it on IO dispatcher.
    return withContext(Dispatchers.IO) {
      engine?.let {
        HttpClient(engine = engine, block)
      } ?: run {
        HttpClient(block)
      }
    }.also {
      it.plugin(HttpSend).intercept { request ->
        maybeFailRequest(request)
        execute(request)
      }
    }
  }

  /**
   * Force fail f8e request if such networking debug option is enabled.
   */
  private fun maybeFailRequest(request: HttpRequestBuilder) {
    val networkingDebugConfig = networkingDebugConfigRepository.config.value
    if (networkingDebugConfig.failF8eRequests) {
      error("Intentionally failing f8e request: $request")
    }
  }

  @OptIn(ExperimentalSerializationApi::class)
  fun configureCommon(
    config: HttpClientConfig<*>,
    accountId: AccountId?,
  ) {
    config.installLogging(ktorLogLevelPolicy.level())

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
      socketTimeoutMillis = 15.seconds.inWholeMilliseconds
    }

    config.install(HttpRequestRetry) {
      maxRetries = 2
      retryOnExceptionIf { _, cause ->
        cause is SocketTimeoutException
      }
      modifyRequest {
        log { "retrying request: $request, retry count $retryCount" }
      }
    }

    config.install(UserAgent) {
      val platformInfo = platformInfoProvider.getPlatformInfo()
      agent = "${appId.value}/$appVersion ${platformInfo.device_make} (${platformInfo.device_model}; ${platformInfo.os_type.name}/${platformInfo.os_version})"
    }

    config.install(datadogTracerPluginProvider.getPlugin(accountId = accountId))
  }
}
