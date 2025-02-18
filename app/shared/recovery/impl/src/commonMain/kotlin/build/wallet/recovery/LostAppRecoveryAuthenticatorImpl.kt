package build.wallet.recovery

import build.wallet.auth.*
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.notifications.DeviceTokenManager
import build.wallet.recovery.LostAppRecoveryAuthenticator.DelayNotifyLostAppAuthError
import build.wallet.recovery.LostAppRecoveryAuthenticator.DelayNotifyLostAppAuthError.AccessTokensNotSavedError
import build.wallet.recovery.LostAppRecoveryAuthenticator.DelayNotifyLostAppAuthError.F8eAccountAuthenticationFailed
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.sync.withLock

@BitkeyInject(AppScope::class)
class LostAppRecoveryAuthenticatorImpl(
  private val accountAuthenticator: AccountAuthenticator,
  private val authTokensService: AuthTokensService,
  private val deviceTokenManager: DeviceTokenManager,
  private val recoveryLock: RecoveryLock,
) : LostAppRecoveryAuthenticator {
  override suspend fun authenticate(
    fullAccountConfig: FullAccountConfig,
    fullAccountId: FullAccountId,
    authResponseSessionToken: String,
    hardwareAuthSignature: String,
    hardwareAuthPublicKey: HwAuthPublicKey,
  ): Result<AccountAuthTokens, DelayNotifyLostAppAuthError> =
    coroutineBinding {
      recoveryLock.withLock {
        val authTokens =
          accountAuthenticator
            .hwAuth(
              f8eEnvironment = fullAccountConfig.f8eEnvironment,
              fullAccountId = fullAccountId,
              session = authResponseSessionToken,
              signature = hardwareAuthSignature
            )
            .logAuthFailure { "Failed to authenticate for lost app recovery" }
            .mapError(::F8eAccountAuthenticationFailed)
            .bind()

        authTokensService
          .setTokens(fullAccountId, authTokens, AuthTokenScope.Global)
          .mapError(::AccessTokensNotSavedError)
          .bind()

        // send in device-token for notifications
        // TODO(W-3372): validate result of this method
        deviceTokenManager
          .addDeviceTokenIfPresentForAccount(
            fullAccountId = fullAccountId,
            f8eEnvironment = fullAccountConfig.f8eEnvironment,
            authTokenScope = AuthTokenScope.Global
          )

        authTokens
      }
    }
}
