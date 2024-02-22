package build.wallet.statemachine.account.create.full

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.account.CreateFullAccountData

/**
 * UI state machine for deleting an onboarding full account, and then restoring a lite account
 * and upgrading it to a full account.
 */
interface ReplaceWithLiteAccountRestoreUiStateMachine :
  StateMachine<ReplaceWithLiteAccountRestoreUiProps, ScreenModel>

data class ReplaceWithLiteAccountRestoreUiProps(
  val data: CreateFullAccountData.ReplaceWithLiteAccountRestoreData,
)
