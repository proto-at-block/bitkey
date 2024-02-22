package build.wallet.statemachine.recovery.sweep

import build.wallet.money.currency.FiatCurrency
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.sweep.SweepData

interface SweepUiStateMachine : StateMachine<SweepUiProps, ScreenModel>

data class SweepUiProps(
  val fiatCurrency: FiatCurrency,
  val sweepData: SweepData,
  val presentationStyle: ScreenPresentationStyle,
  val onExit: () -> Unit,
)
