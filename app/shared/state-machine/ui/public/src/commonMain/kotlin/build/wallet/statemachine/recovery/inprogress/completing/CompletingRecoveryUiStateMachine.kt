package build.wallet.statemachine.recovery.inprogress.completing

import build.wallet.money.currency.FiatCurrency
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData

/**
 * UI state machine for completing recovery (for App or Hardware factor).
 */
interface CompletingRecoveryUiStateMachine : StateMachine<CompletingRecoveryUiProps, ScreenModel>

data class CompletingRecoveryUiProps(
  val presentationStyle: ScreenPresentationStyle,
  val fiatCurrency: FiatCurrency,
  val completingRecoveryData: CompletingRecoveryData,
  val onExit: (() -> Unit)?,
  val isHardwareFake: Boolean,
)
