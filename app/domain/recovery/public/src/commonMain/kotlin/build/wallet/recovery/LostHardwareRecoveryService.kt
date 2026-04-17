package build.wallet.recovery

import bitkey.account.HardwareType
import bitkey.recovery.InitiateDelayNotifyRecoveryError
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwKeyBundle
import com.github.michaelbull.result.Result

/**
 * Domain service for managing Lost Hardware Delay & Notify recovery.
 */
interface LostHardwareRecoveryService {
  /**
   * Generates a new set of app keys to initialize the lost HW recovery process
   */
  suspend fun generateNewAppKeys(): Result<AppKeyBundle, Throwable>

  /**
   * Initiates delay + notify recovery for lost or stolen hardware, process is initiated
   * through f8e, DN recovery is written into local state.
   *
   * @param destinationAppKeyBundle new App's Key Bundle.
   * @param destinationHardwareKeyBundle new Hardware's Key Bundle.
   */
  suspend fun initiate(
    destinationAppKeyBundle: AppKeyBundle,
    destinationHardwareKeyBundle: HwKeyBundle,
    appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    hardwareType: HardwareType,
  ): Result<Unit, InitiateDelayNotifyRecoveryError>

  /**
   * Cancels in progress D&N recovery using app proof of possession.
   */
  suspend fun cancelRecovery(): Result<Unit, CancelDelayNotifyRecoveryError>

  /**
   * Cancels a conflicting in-progress D&N recovery.
   * Builds an app-signed CancelConflictingRecovery action proof for W3 accounts.
   */
  suspend fun cancelConflictingRecovery(): Result<Unit, CancelDelayNotifyRecoveryError>
}
