package build.wallet.recovery

import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.recovery.HardwareKeysForRecovery
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.InitiateAccountDelayNotifyErrorCode
import build.wallet.recovery.RecoverySyncer.SyncError
import com.github.michaelbull.result.Result

/**
 * Initiates new Delay + Notify recovery for Lost App/Mobile key.
 */
interface LostAppRecoveryInitiator {
  /**
   * Initiates delay + notify recovery for Lost App/Mobile key. Process is initiated
   * through f8e, DN recovery is written into local state.
   *
   * @param fullAccountConfig config to use for recovery. Mainly used to tell f8e which network type
   * we are recovering for.
   * @param hardwareKeysForRecovery The auth/spending keys we got from the hardware to begin recovery
   * @param newAppKeys new app spending and auth keys to use after recovery for the new keybox.
   * @param appGlobalAuthKeyHwSignature the hardware signature of the new [newAppKeys].
   * @param f8eEnvironment the active F8E environment
   * @param fullAccountId The customer's account ID
   * @param hwFactorProofOfPossession The proof of possession obtained by signing the latest access
   * token with the HW
   */
  suspend fun initiate(
    fullAccountConfig: FullAccountConfig,
    hardwareKeysForRecovery: HardwareKeysForRecovery,
    newAppKeys: AppKeyBundle,
    appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, InitiateDelayNotifyAppRecoveryError>

  sealed class InitiateDelayNotifyAppRecoveryError : Error() {
    /**
     * Corresponds to an error when starting D&N recovery with f8e:
     * - due to needing additional comms verification. In this case, we initiate the comms flow.
     * - due to regular networking error (poor connectivity, outages, etc). In this case, we can
     * retry the recovery initiation.
     * - due to some server error. In this case, we are unlikely to be able to start recovery.
     * - due to client error - e.g. bad input or serialization bug.
     */
    data class F8eInitiateDelayNotifyError(
      val error: F8eError<InitiateAccountDelayNotifyErrorCode>,
    ) : InitiateDelayNotifyAppRecoveryError() {
      override val cause = error.error
    }

    /**
     * We successfully initiated recovery with f8e but failed to persist local database state:
     * - due to a corrupted database. To recover from this we need to retrieve D&N status from f8e
     * and retry persisting locally.
     * - due to some rare I/O error
     */
    data class FailedToPersistRecoveryStateError(
      override val cause: Error,
    ) : InitiateDelayNotifyAppRecoveryError()

    data class FailedToSyncRecoveryError(
      override val cause: SyncError,
    ) : InitiateDelayNotifyAppRecoveryError()
  }
}
