package build.wallet.statemachine.recovery.sweep

import build.wallet.bitkey.keybox.Keybox
import build.wallet.recovery.sweep.SweepContext
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.StateMachine

interface SweepUiStateMachine : StateMachine<SweepUiProps, ScreenModel>

data class SweepUiProps(
  val hasAttemptedSweep: Boolean,
  val keybox: Keybox,
  val sweepContext: SweepContext = SweepContext.InactiveWallet,
  val presentationStyle: ScreenPresentationStyle,
  val onExit: (() -> Unit)?,
  val onSuccess: () -> Unit,
  val onAttemptSweep: () -> Unit,
)
