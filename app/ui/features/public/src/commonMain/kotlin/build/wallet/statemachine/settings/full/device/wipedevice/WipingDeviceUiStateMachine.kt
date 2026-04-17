package build.wallet.statemachine.settings.full.device.wipedevice

import bitkey.account.HardwareType
import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine to present screens for wiping a Bitkey device.
 */
interface WipingDeviceUiStateMachine : StateMachine<WipingDeviceProps, ScreenModel>

data class WipingDeviceProps(
  val onBack: () -> Unit,
  val onSuccess: () -> Unit,
  val fullAccount: FullAccount?,
  /**
   * Context for the wipe operation.
   *
   * [WipeContext.Default] follows the normal flow with intro/confirmation screens.
   * [WipeContext.W3UpgradeOldDevice] skips intro/confirmation since we already know
   * the old device has no funds and should be wiped.
   */
  val wipeContext: WipeContext = WipeContext.Default,
)

/**
 * Context describing why a device wipe is being performed.
 */
sealed interface WipeContext {
  /** Normal wipe flow from device settings. Shows intro and confirmation screens. */
  data object Default : WipeContext

  /**
   * Wiping the old hardware device after a W3 upgrade.
   *
   * Skips intro and confirmation screens since:
   * - Funds have already been swept to the new wallet
   * - The old device serial is known and enforced
   *
   * @param oldHardwareType The hardware type of the old device (used to select the correct
   *   NFC commands, especially important for fake hardware).
   * @param oldSerial The serial number of the old device. The NFC session will verify that
   *   the tapped device matches this serial before wiping.
   */
  data class W3UpgradeOldDevice(
    val oldHardwareType: HardwareType,
    val oldSerial: String? = null,
  ) : WipeContext
}
