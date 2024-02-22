package build.wallet.recovery

import build.wallet.auth.AccountAuthTokens
import build.wallet.auth.AccountAuthenticator
import build.wallet.auth.AuthTokenDao
import build.wallet.auth.AuthTokenScope
import build.wallet.auth.logAuthFailure
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.notifications.DeviceTokenManager
import build.wallet.recovery.LostAppRecoveryAuthenticator.DelayNotifyLostAppAuthError
import build.wallet.recovery.LostAppRecoveryAuthenticator.DelayNotifyLostAppAuthError.AccessTokensNotSavedError
import build.wallet.recovery.LostAppRecoveryAuthenticator.DelayNotifyLostAppAuthError.F8eAccountAuthenticationFailed
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapError

class LostAppRecoveryAuthenticatorImpl(
  private val accountAuthenticator: AccountAuthenticator,
  private val authTokenDao: AuthTokenDao,
  private val deviceTokenManager: DeviceTokenManager,
) : LostAppRecoveryAuthenticator {
  override suspend fun authenticate(
    keyboxConfig: KeyboxConfig,
    fullAccountId: FullAccountId,
    authResponseSessionToken: String,
    hardwareAuthSignature: String,
    hardwareAuthPublicKey: HwAuthPublicKey,
  ): Result<AccountAuthTokens, DelayNotifyLostAppAuthError> =
    binding {
      val authTokens =
        accountAuthenticator
          .hwAuth(
            f8eEnvironment = keyboxConfig.f8eEnvironment,
            fullAccountId = fullAccountId,
            session = authResponseSessionToken,
            signature = hardwareAuthSignature
          )
          .logAuthFailure { "Failed to authenticate for lost app recovery" }
          .mapError(::F8eAccountAuthenticationFailed)
          .bind()

      authTokenDao
        .setTokensOfScope(fullAccountId, authTokens, AuthTokenScope.Global)
        .mapError(::AccessTokensNotSavedError)
        .bind()

      // send in device-token for notifications
      // TODO(W-3372): validate result of this method
      deviceTokenManager
        .addDeviceTokenIfPresentForAccount(
          fullAccountId = fullAccountId,
          f8eEnvironment = keyboxConfig.f8eEnvironment,
          authTokenScope = AuthTokenScope.Global
        )

      authTokens
    }
}
