package build.wallet.statemachine.recovery.conflict

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.conflict.NoLongerRecoveringData

/**
 * UI state machine corresponding to [NoLongerRecoveringData] outputs from
 * [NoLongerRecoveringDataStateMachine]
 */
interface NoLongerRecoveringUiStateMachine : StateMachine<NoLongerRecoveringUiProps, ScreenModel>

data class NoLongerRecoveringUiProps(
  val data: NoLongerRecoveringData,
)
