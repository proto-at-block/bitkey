package build.wallet.statemachine.settings.full.device.wipedevice.intro

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.settings.full.device.wipedevice.WipeContext
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceInitialStep

/**
 * State machine to the intro screen, and its various modals for wiping a Bitkey device.
 */
interface WipingDeviceIntroUiStateMachine : StateMachine<WipingDeviceIntroProps, ScreenModel>

data class WipingDeviceIntroProps(
  val onBack: () -> Unit,
  val onUnwindToMoneyHome: () -> Unit,
  val onDeviceConfirmed: (pairedDevice: Boolean, wipeContext: WipeContext) -> Unit,
  val fullAccount: FullAccount?,
  val initialStep: WipingDeviceInitialStep = WipingDeviceInitialStep.Intro,
  val wipeContext: WipeContext = WipeContext.Default,
)
