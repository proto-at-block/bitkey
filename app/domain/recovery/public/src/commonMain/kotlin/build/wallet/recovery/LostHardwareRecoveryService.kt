package build.wallet.recovery

import bitkey.recovery.InitiateDelayNotifyRecoveryError
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwKeyBundle
import com.github.michaelbull.result.Result

/**
 * Domain service for managing Lost Hardware Delay & Notify recovery.
 *
 * TODO: move remaining domain operations here.
 */
interface LostHardwareRecoveryService {
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
  ): Result<Unit, InitiateDelayNotifyRecoveryError>

  /**
   * Cancels in progress D&N recovery using app proof of possession.
   */
  suspend fun cancelRecovery(): Result<Unit, CancelDelayNotifyRecoveryError>
}
