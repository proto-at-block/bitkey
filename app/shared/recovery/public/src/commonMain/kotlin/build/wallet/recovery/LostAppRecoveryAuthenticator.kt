package build.wallet.recovery

import build.wallet.auth.AccountAuthTokens
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.db.DbError
import com.github.michaelbull.result.Result

interface LostAppRecoveryAuthenticator {
  /**
   * Authenticates with f8e for initiating Lost App DN recovery.
   *
   * @param fullAccountConfig config to use for recovery. Mainly used to tell f8e which network type
   * we are recovering for.
   * @param fullAccountId account ID of the user authenticating, from the server
   * @param authResponseSessionToken a token that uniquely identifies the authentication session, from the server
   * @param hardwareAuthSignature f8e auth challenge signed with hardware.
   */
  suspend fun authenticate(
    fullAccountConfig: FullAccountConfig,
    fullAccountId: FullAccountId,
    authResponseSessionToken: String,
    hardwareAuthSignature: String,
    hardwareAuthPublicKey: HwAuthPublicKey,
  ): Result<AccountAuthTokens, DelayNotifyLostAppAuthError>

  sealed class DelayNotifyLostAppAuthError : Error() {
    /**
     * Indicates we tried to authenticate with f8e using hardware auth key to get account,
     * but failed:
     * - due to regular networking error (poor connectivity, outages, etc). In this case, we can
     * retry the recovery initiation.
     * - due to some server error. In this case, we are unlikely to be able to start recovery.
     * - due to client error - e.g. bad input or serialization bug.
     */
    data class F8eAccountAuthenticationFailed(
      override val cause: Throwable,
    ) : DelayNotifyLostAppAuthError()

    /**
     * Indicates that the app failed to successfully saved access tokens after authenticating.
     */
    data class AccessTokensNotSavedError(
      override val cause: Throwable,
    ) : DelayNotifyLostAppAuthError()

    /**
     * Indicates we tried to cancel an active recovery with f8e using hardware auth key to get account,
     * but failed:
     * - due to regular networking error (poor connectivity, outages, etc). In this case, we can
     * retry the recovery initiation.
     * - due to some server error. In this case, we are unlikely to be able to start recovery.
     * - due to client error - e.g. bad input or serialization bug.
     */
    data class F8eCancelActiveRecoveryError(
      override val cause: Error,
    ) : DelayNotifyLostAppAuthError()

    /**
     * Indicates we tried to create new f8e spending keyset but failed:
     * - due to regular networking error (poor connectivity, outages, etc). In this case, we can
     * retry the recovery initiation.
     * - due to some server error. In this case, we are unlikely to be able to start recovery.
     * - due to client error - e.g. bad input or serialization bug.
     */
    data class F8eCreateNewServerKeysError(
      override val cause: Error,
    ) : DelayNotifyLostAppAuthError()

    /**
     * Corresponds to an error when getting account status from f8e:
     * - due to regular networking error (poor connectivity, outages, etc). In this case, we can
     * retry the recovery initiation.
     * - due to some server error. In this case, we are unlikely to be able to start recovery.
     * - due to client error - e.g. bad input or serialization bug.
     */
    data class F8eGetAccountStatusError(
      override val cause: Error,
    ) : DelayNotifyLostAppAuthError()

    data class ClearRecoveryTableError(
      override val cause: DbError,
    ) : DelayNotifyLostAppAuthError()
  }
}
