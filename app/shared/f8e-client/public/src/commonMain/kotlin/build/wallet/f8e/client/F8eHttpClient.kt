package build.wallet.f8e.client

import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.f8e.AccountId
import build.wallet.crypto.PublicKey
import build.wallet.crypto.WsmVerifier
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine

/**
 * Provides [HttpClient] for F8e APIs.
 */
interface F8eHttpClient : UnauthenticatedF8eHttpClient {
  val wsmVerifier: WsmVerifier

  /**
   * Client that talks to F8e
   *
   * @param appFactorProofOfPossessionAuthKey app authentication key to be used to create App
   * factor proof of possession header. If `null`, will try to use active or onboarding account's
   * (if any) authentication key.
   *
   * @param hwFactorProofOfPossession Hardware factor proof of possession to include in the
   * request header.
   *
   * @param authTokenScope The scope of auth tokens to include in the Bearer header on the request.
   * Note: based on this scope, we will automatically look up the corresponding tokens in
   * [AuthTokenDao] and attach them to the request, and we will also try to look up the
   * [AppAuthPublicKey] associated with the current account in case we need to refresh the tokens.
   * Refreshing the tokens with this mechanism only works for app-generated auth public keys
   * (i.e. not [HwAuthPublicKey]).
   */
  suspend fun authenticated(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    appFactorProofOfPossessionAuthKey: PublicKey<out AppAuthKey>? = null,
    hwFactorProofOfPossession: HwFactorProofOfPossession? = null,
    engine: HttpClientEngine? = null,
    authTokenScope: AuthTokenScope = AuthTokenScope.Global,
  ): HttpClient
}
