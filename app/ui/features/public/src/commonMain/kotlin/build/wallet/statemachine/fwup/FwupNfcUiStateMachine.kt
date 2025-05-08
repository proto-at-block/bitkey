package build.wallet.statemachine.fwup

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for managing the UI for the entire FWUP experience,
 * which includes the initial informational screen and then the NFC
 * session screens which is managed by [FwupNfcSessionUiStateMachine]
 */
interface FwupNfcUiStateMachine :
  StateMachine<FwupNfcUiProps, ScreenModel>

data class FwupNfcUiProps(
  val onDone: () -> Unit,
)
