package build.wallet.statemachine.recovery.conflict

import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringData

/**
 * UI state machine corresponding to [SomeoneElseIsRecoveringData] outputs from
 * [SomeoneElseIsRecoveringDataStateMachine]
 */
interface SomeoneElseIsRecoveringUiStateMachine :
  StateMachine<SomeoneElseIsRecoveringUiProps, ScreenModel>

data class SomeoneElseIsRecoveringUiProps(
  val data: SomeoneElseIsRecoveringData,
  val fullAccountConfig: FullAccountConfig,
  val fullAccountId: FullAccountId,
)
