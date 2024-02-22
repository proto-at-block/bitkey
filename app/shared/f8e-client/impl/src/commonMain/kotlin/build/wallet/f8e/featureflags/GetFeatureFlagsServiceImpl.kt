package build.wallet.f8e.featureflags

import build.wallet.analytics.v1.PlatformInfo
import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.bodyResult
import build.wallet.logging.logNetworkFailure
import build.wallet.platform.settings.LocaleCountryCodeProvider
import build.wallet.platform.settings.LocaleLanguageCodeProvider
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class GetFeatureFlagsServiceImpl(
  private val f8eHttpClient: F8eHttpClient,
  private val appInstallationId: String,
  private val hardwareSerialNumber: String?,
  private val platformInfo: PlatformInfo,
  private val localeCountryCodeProvider: LocaleCountryCodeProvider,
  private val localeLanguageCodeProvider: LocaleLanguageCodeProvider,
) : GetFeatureFlagsService {
  override suspend fun getF8eFeatureFlags(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId?,
  ): Result<List<GetFeatureFlagsService.F8eFeatureFlag>, NetworkingError> {
    val httpClient =
      accountId?.let {
        f8eHttpClient.authenticated(f8eEnvironment, accountId)
      } ?: run {
        f8eHttpClient.unauthenticated(f8eEnvironment)
      }

    return httpClient.bodyResult<FeatureFlagsResponse> {
      get("/api/feature_flags") {
        setBody(
          RequestBody(
            accountId = accountId,
            appInstallationId = appInstallationId,
            hardwareSerialNumber = hardwareSerialNumber,
            platformInfo = platformInfo.platformInfoBody,
            deviceCountryCode = localeCountryCodeProvider.countryCode(),
            deviceLanguageCode = localeLanguageCodeProvider.languageCode()
          )
        )
      }
    }
      .map { it.flags }
      .logNetworkFailure { "Failed to get feature flags" }
  }

  @Serializable
  private data class FeatureFlagsResponse(
    var flags: List<GetFeatureFlagsService.F8eFeatureFlag>,
  )

  @Serializable
  private data class RequestBody(
    @SerialName("account_id")
    private val accountId: AccountId?,
    @SerialName("app_installation_id")
    private val appInstallationId: String,
    @SerialName("bitkey_hardware")
    private val hardwareSerialNumber: String?,
    @SerialName("platform_info")
    private val platformInfo: PlatformInfoBody,
    @SerialName("device_region")
    private val deviceCountryCode: String,
    @SerialName("device_language")
    private val deviceLanguageCode: String,
  )

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
