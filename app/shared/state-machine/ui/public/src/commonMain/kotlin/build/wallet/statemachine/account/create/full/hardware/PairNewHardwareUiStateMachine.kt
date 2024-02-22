package build.wallet.statemachine.account.create.full.hardware

import build.wallet.analytics.events.screen.context.PairHardwareEventTrackerScreenIdContext
import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.nfc.transaction.PairingTransactionResponse
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.StateMachine

/** UI State Machine for navigating the pairing of a new hardware device. */
interface PairNewHardwareUiStateMachine :
  StateMachine<PairNewHardwareProps, ScreenModel>

data class PairNewHardwareProps(
  val keyboxConfig: KeyboxConfig,
  val onExit: () -> Unit,
  val onSuccess: ((PairingTransactionResponse.FingerprintEnrolled) -> Unit)?,
  val eventTrackerContext: PairHardwareEventTrackerScreenIdContext,
  val screenPresentationStyle: ScreenPresentationStyle,
)
