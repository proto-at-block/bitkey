package build.wallet.f8e.client

import build.wallet.auth.AuthTokenScope
import build.wallet.auth.AuthTokensRepository
import build.wallet.availability.NetworkConnection
import build.wallet.availability.NetworkReachabilityProvider
import build.wallet.availability.networkReachabilityPlugin
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.f8e.AccountId
import build.wallet.crypto.PublicKey
import build.wallet.crypto.WsmVerifier
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.url
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.device.DevicePlatform.Android
import com.github.michaelbull.result.get
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders
import io.ktor.util.appendIfNameAbsent

class F8eHttpClientImpl(
  private val authTokensRepository: AuthTokensRepository,
  private val proofOfPossessionPluginProvider: ProofOfPossessionPluginProvider,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val unauthenticatedF8eHttpClient: UnauthenticatedF8eHttpClient,
  private val f8eHttpClientProvider: F8eHttpClientProvider,
  private val networkReachabilityProvider: NetworkReachabilityProvider,
  override val wsmVerifier: WsmVerifier,
) : UnauthenticatedF8eHttpClient by unauthenticatedF8eHttpClient, F8eHttpClient {
  companion object {
    const val CONSTANT_PROOF_OF_POSSESSION_APP_HEADER = "X-App-Signature"
    const val CONSTANT_PROOF_OF_POSSESSION_HW_HEADER = "X-Hw-Signature"
  }

  override suspend fun authenticated(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    appFactorProofOfPossessionAuthKey: PublicKey<out AppAuthKey>?,
    hwFactorProofOfPossession: HwFactorProofOfPossession?,
    engine: HttpClientEngine?,
    authTokenScope: AuthTokenScope,
  ): HttpClient {
    return f8eHttpClientProvider.getHttpClient(engine = engine) {
      f8eHttpClientProvider.configureCommon(this, accountId)
      // Add proof of possession headers
      install(
        proofOfPossessionPluginProvider.getPlugin(
          accountId = accountId,
          appAuthKey = appFactorProofOfPossessionAuthKey,
          hwProofOfPossession = hwFactorProofOfPossession
        )
      )

      installAuthPlugin(f8eEnvironment, accountId, authTokenScope)

      defaultRequest {
        val deviceInfo = deviceInfoProvider.getDeviceInfo()
        val isAndroidEmulator = deviceInfo.devicePlatform == Android && deviceInfo.isEmulator
        // We don't want to replace these two headers if already provided.
        headers.appendIfNameAbsent(HttpHeaders.Accept, Json.toString())
        headers.appendIfNameAbsent(HttpHeaders.ContentType, Json.toString())
        url(f8eEnvironment.url(isAndroidEmulator))
      }

      HttpResponseValidator {
        networkReachabilityPlugin(
          connection = NetworkConnection.HttpClientNetworkConnection.F8e(f8eEnvironment),
          networkReachabilityProvider = networkReachabilityProvider
        )
      }
    }.interceptWhenOffline(f8eEnvironment)
  }

  /**
   * Installs bearer auth plugin for Ktor HttpClient.
   * Uses f8e endpoints to facilitate authentication.
   */
  private fun HttpClientConfig<*>.installAuthPlugin(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    authTokenScope: AuthTokenScope,
  ) {
    install(Auth) {
      bearer {
        loadTokens {
          authTokensRepository
            .getAuthTokens(accountId, authTokenScope)
            .get()
            ?.let { tokens ->
              BearerTokens(
                accessToken = tokens.accessToken.raw,
                refreshToken = tokens.refreshToken.raw
              )
            }
        }
        refreshTokens {
          // Note: this only works for tokens generated via [AppAuthPublicKey].
          // If the tokens were generated for the request via [HwAuthPublicKey], that
          // needs more explicit handling because it requires the customer to tap with
          // their Bitkey device.
          authTokensRepository
            .refreshAccessToken(f8eEnvironment, accountId, authTokenScope)
            .get()
            ?.let { tokens ->
              BearerTokens(
                accessToken = tokens.accessToken.raw,
                refreshToken = tokens.refreshToken.raw
              )
            }
        }
      }
    }
  }
}
