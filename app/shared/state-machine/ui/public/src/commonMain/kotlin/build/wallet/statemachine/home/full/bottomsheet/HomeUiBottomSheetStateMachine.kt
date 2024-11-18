package build.wallet.statemachine.home.full.bottomsheet

import build.wallet.bitkey.account.Account
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.StateMachine

/**
 * Handles translating the persisted [HomeUiBottomSheet] into a [SheetModel].
 */
interface HomeUiBottomSheetStateMachine : StateMachine<HomeUiBottomSheetProps, SheetModel?>

data class HomeUiBottomSheetProps(
  val account: Account,
  val onShowSetSpendingLimitFlow: () -> Unit,
)
