package build.wallet.statemachine.settings.full.device.wipedevice

import build.wallet.bitkey.account.FullAccount
import build.wallet.device.wipe.InactiveHardwareDevice
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
  val initialStep: WipingDeviceInitialStep = WipingDeviceInitialStep.Intro,
  /**
   * Context for the wipe operation.
   *
   * [WipeContext.Default] follows the normal flow with intro/confirmation screens.
   * [WipeContext.InactiveDevice] can skip the intro; confirmation still validates the
   * tapped inactive device before wiping.
   */
  val wipeContext: WipeContext = WipeContext.Default,
)

enum class WipingDeviceInitialStep {
  Intro,
  ScanDevice,
}

/**
 * Context describing why a device wipe is being performed.
 */
sealed interface WipeContext {
  /** Normal wipe flow from device settings. Shows intro and confirmation screens. */
  data object Default : WipeContext

  /**
   * Wiping a hardware device that was previously paired with this wallet.
   *
   * Skips the intro screen since:
   * - The inactive device has no sweepable funds
   * - For the W3-upgrade old W1, any required sweep confirmation checks have passed
   *
   * The confirmation screen is still shown so the final NFC tap can validate the
   * tapped inactive device before wiping.
   */
  data class InactiveDevice(
    val device: InactiveHardwareDevice,
  ) : WipeContext
}
