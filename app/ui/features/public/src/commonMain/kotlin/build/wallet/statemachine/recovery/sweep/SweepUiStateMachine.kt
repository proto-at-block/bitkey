package build.wallet.statemachine.recovery.sweep

import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.keybox.Keybox
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.StateMachine

interface SweepUiStateMachine : StateMachine<SweepUiProps, ScreenModel>

data class SweepUiProps(
  val keybox: Keybox,
  val recoveredFactor: PhysicalFactor?,
  val presentationStyle: ScreenPresentationStyle,
  val onExit: () -> Unit,
  val onSuccess: () -> Unit,
)
