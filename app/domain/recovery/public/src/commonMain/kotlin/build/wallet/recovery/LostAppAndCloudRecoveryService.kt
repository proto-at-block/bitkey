package build.wallet.recovery

import bitkey.auth.AccountAuthTokens
import bitkey.backup.DescriptorBackup
import bitkey.recovery.InitiateDelayNotifyRecoveryError
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.recovery.HardwareKeysForRecovery
import build.wallet.cloud.backup.csek.SealedSsek
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
   * @return [CompletedAuth] with access tokens and keys or descriptor backup data requiring NFC interaction.
   */
  suspend fun completeAuth(
    accountId: FullAccountId,
    session: String,
    hwAuthKey: HwAuthPublicKey,
    hwSignedChallenge: String,
  ): Result<CompletedAuth, Throwable>

  /**
   * Initiates delay + notify recovery for Lost App/App Key. Process is initiated
   * through f8e, DN recovery is written into local state.
   *
   * @param completedAuth The auth tokens and keys we got from the hardware to begin recovery
   * @param hardwareKeysForRecovery The auth/spending keys we got from the hardware to begin recovery
   */
  suspend fun initiateRecovery(
    completedAuth: CompletedAuth,
    hardwareKeysForRecovery: HardwareKeysForRecovery,
  ): Result<Unit, InitiateDelayNotifyRecoveryError>

  /**
   * Represents successful response for [completeAuth].
   */
  sealed interface CompletedAuth {
    val accountId: FullAccountId
    val authTokens: AccountAuthTokens
    val hwAuthKey: HwAuthPublicKey
    val destinationAppKeys: AppKeyBundle

    /**
     * Hardware keys are directly available from keysets).
     */
    data class WithDirectKeys(
      override val accountId: FullAccountId,
      override val authTokens: AccountAuthTokens,
      override val hwAuthKey: HwAuthPublicKey,
      override val destinationAppKeys: AppKeyBundle,
      val existingHwSpendingKeys: List<HwSpendingPublicKey>,
    ) : CompletedAuth

    /**
     * Descriptor backups are available and valid, but require NFC interaction to decrypt the wrapped SSEK.
     * Consumer needs to:
     * 1. Use hardware NFC to decrypt the wrapped SSEK
     * 2. Store the unwrapped SSEK in SsekDao
     * 3. Use DescriptorBackupService.unsealDescriptors to get hardware keys
     */
    data class WithDescriptorBackups(
      override val accountId: FullAccountId,
      override val authTokens: AccountAuthTokens,
      override val hwAuthKey: HwAuthPublicKey,
      override val destinationAppKeys: AppKeyBundle,
      val descriptorBackups: List<DescriptorBackup>,
      val wrappedSsek: SealedSsek,
    ) : CompletedAuth
  }

  /**
   * Cancels in progress D&N recovery using hardware proof of possession.
   */
  suspend fun cancelRecovery(
    accountId: FullAccountId,
    hwProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, CancelDelayNotifyRecoveryError>
}
