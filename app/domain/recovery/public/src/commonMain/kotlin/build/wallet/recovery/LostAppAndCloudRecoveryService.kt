package build.wallet.recovery

import bitkey.auth.AccountAuthTokens
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.f8e.auth.AuthF8eClient.InitiateAuthenticationSuccess
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Result

/**
 * Domain service for managing Lost App + Cloud Delay & Notify recovery.
 *
 * TODO: move remaining domain operations here.
 */
interface LostAppAndCloudRecoveryService {
  /**
   * Initiate authentication for starting Lost App recovery.
   * Requests an auth challenge from f8e to be signed by customer's existing hardware devices.
   * The challenge will be used to complete auth process of initiating Lost App recovery.
   *
   * @param hwAuthKey current auth key of the hardware.
   *
   * @return [InitiateAuthenticationSuccess] with auth session and challenge to be signed by hardware.
   */
  suspend fun initiateAuth(
    hwAuthKey: HwAuthPublicKey,
  ): Result<InitiateAuthenticationSuccess, Error>

  /**
   * After auth challenge is signed by hardware, complete authentication with f8e using hardware factor.
   *
   * @param hwAuthKey current auth key of the hardware.
   * @param session auth session from [initiateAuth] call.
   * @param hwSignedChallenge [InitiateAuthenticationSuccess.challenge] signed with hardware.
   *
   * @return [CompletedAuth] with access tokens and keys.
   */
  suspend fun completeAuth(
    accountId: FullAccountId,
    session: String,
    hwAuthKey: HwAuthPublicKey,
    hwSignedChallenge: String,
  ): Result<CompletedAuth, Throwable>

  /**
   * Represents successful response for [completeAuth]. Contains auth tokens for the account,
   * newly generated app keys (not rotated yet) and hardware keys.
   *
   * @property accountId recovered account ID from authenticating with f8e using hardware.
   * @property authTokens new auth tokens for the account from authenticating with f8e using hardware.
   * @property hwAuthKey current auth key of the hardware.
   */
  data class CompletedAuth(
    val accountId: FullAccountId,
    val authTokens: AccountAuthTokens,
    val hwAuthKey: HwAuthPublicKey,
    val destinationAppKeys: AppKeyBundle,
    val existingHwSpendingKeys: List<HwSpendingPublicKey>,
  )

  /**
   * Cancels in progress D&N recovery using hardware proof of possession.
   */
  suspend fun cancelRecovery(
    accountId: FullAccountId,
    hwProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, CancelDelayNotifyRecoveryError>
}
