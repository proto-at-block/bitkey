package build.wallet.statemachine.settings.full.mobilepay

import build.wallet.bitkey.account.FullAccount
import build.wallet.limit.SpendingLimit
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for the UI for the mobile pay settings screen that shows
 * the current state of the spending limit
 */
interface MobilePayStatusUiStateMachine : StateMachine<MobilePayUiProps, BodyModel>

data class MobilePayUiProps(
  val onBack: () -> Unit,
  val account: FullAccount,
  val onSetLimitClick: (currentLimit: SpendingLimit?) -> Unit,
)
