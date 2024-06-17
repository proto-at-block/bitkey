package build.wallet.f8e.featureflags

import build.wallet.account.analytics.AppInstallation
import build.wallet.account.analytics.AppInstallationDao
import build.wallet.analytics.events.PlatformInfoProvider
import build.wallet.analytics.v1.PlatformInfo
import build.wallet.auth.AuthTokenScope.Global
import build.wallet.auth.AuthTokenScope.Recovery
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.f8e.LiteAccountId
import build.wallet.catchingResult
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.setRedactedBody
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.logging.logNetworkFailure
import build.wallet.platform.settings.LocaleCountryCodeProvider
import build.wallet.platform.settings.LocaleLanguageCodeProvider
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import dev.zacsweers.redacted.annotations.Unredacted
import io.ktor.client.request.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

class FeatureFlagsF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
  private val appInstallationDao: AppInstallationDao,
  private val platformInfoProvider: PlatformInfoProvider,
  private val localeCountryCodeProvider: LocaleCountryCodeProvider,
  private val localeLanguageCodeProvider: LocaleLanguageCodeProvider,
) : FeatureFlagsF8eClient {
  override suspend fun getF8eFeatureFlags(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId?,
    flagKeys: List<String>,
  ): Result<List<FeatureFlagsF8eClient.F8eFeatureFlag>, NetworkingError> {
    val httpClient = when (accountId) {
      null ->
        f8eHttpClient.unauthenticated(f8eEnvironment)
      is FullAccountId ->
        f8eHttpClient.authenticated(f8eEnvironment, accountId, authTokenScope = Global)
      is LiteAccountId ->
        f8eHttpClient.authenticated(f8eEnvironment, accountId, authTokenScope = Recovery)
    }

    val url =
      accountId?.let {
        "/api/accounts/${it.serverId}/feature-flags"
      } ?: run {
        "/api/feature-flags"
      }

    val appInstallation =
      appInstallationDao.getOrCreateAppInstallation()
        .getOrElse {
          log(LogLevel.Error, throwable = it.cause) { "Failed to get App Installation" }
          AppInstallation(localId = "", hardwareSerialNumber = null)
        }

    return httpClient.bodyResult<FeatureFlagsResponse> {
      post(url) {
        setRedactedBody(
          RequestBody(
            flagKeys = flagKeys,
            appInstallationId = appInstallation.localId,
            hardwareSerialNumber = appInstallation.hardwareSerialNumber,
            platformInfo = platformInfoProvider.getPlatformInfo().platformInfoBody,
            deviceCountryCode = localeCountryCodeProvider.countryCode(),
            deviceLanguageCode = localeLanguageCodeProvider.languageCode()
          )
        )
      }
    }
      .map {
        it.decodeValidFlags()
      }
      .logNetworkFailure { "Failed to get feature flags" }
  }

  @Serializable
  internal data class FeatureFlagsResponse(
    private var flags: List<JsonObject>,
  ) : RedactedResponseBody {
    fun decodeValidFlags(): List<FeatureFlagsF8eClient.F8eFeatureFlag> {
      val json = Json {
        allowTrailingComma = true
        ignoreUnknownKeys = true
      }

      return flags.mapNotNull { flag ->
        catchingResult {
          json.decodeFromJsonElement<FeatureFlagsF8eClient.F8eFeatureFlag>(flag)
        }.get()
      }
    }
  }

  @Serializable
  private data class RequestBody(
    @SerialName("flag_keys")
    private val flagKeys: List<String>,
    @SerialName("app_installation_id")
    private val appInstallationId: String,
    @SerialName("hardware_id")
    private val hardwareSerialNumber: String?,
    @SerialName("platform_info")
    private val platformInfo: PlatformInfoBody,
    @Unredacted
    @SerialName("device_region")
    private val deviceCountryCode: String,
    @Unredacted
    @SerialName("device_language")
    private val deviceLanguageCode: String,
  ) : RedactedRequestBody

  @Serializable
  private data class PlatformInfoBody(
    @SerialName("device_id")
    private val deviceId: String,
    @SerialName("client_type")
    private val clientType: String,
    @SerialName("application_version")
    private val applicationVersion: String,
    @SerialName("os_type")
    private val osType: String,
    @SerialName("os_version")
    private val osVersion: String,
    @SerialName("device_make")
    private val deviceMake: String,
    @SerialName("device_model")
    private val deviceModel: String,
    @SerialName("app_id")
    private val appId: String,
  )

  private val PlatformInfo.platformInfoBody
    get() =
      PlatformInfoBody(
        deviceId = device_id,
        clientType = client_type.name,
        applicationVersion = application_version,
        osType = os_type.name,
        osVersion = os_version,
        deviceMake = device_make,
        deviceModel = device_model,
        appId = app_id
      )
}
