package build.wallet.statemachine.settings.full.mobilepay

import build.wallet.limit.SpendingLimit
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData

/**
 * State machine for the UI for the mobile pay settings screen that shows
 * the current state of the spending limit
 */
interface MobilePayStatusUiStateMachine : StateMachine<MobilePayUiProps, BodyModel>

data class MobilePayUiProps(
  val onBack: () -> Unit,
  val accountData: ActiveFullAccountLoadedData,
  val onSetLimitClick: (currentLimit: SpendingLimit?) -> Unit,
)
