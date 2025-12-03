package build.wallet.statemachine.partnerships

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.keybox.Keybox
import build.wallet.money.FiatMoney
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.StateMachine

interface AddBitcoinUiStateMachine : StateMachine<AddBitcoinUiProps, SheetModel>

data class AddBitcoinUiProps(
  val account: FullAccount,
  val onTransfer: () -> Unit,
  val onExit: () -> Unit,
  val keybox: Keybox,
  val onSelectCustomAmount: (minAmount: FiatMoney, maxAmount: FiatMoney) -> Unit,
  val initialState: AddBitcoinBottomSheetDisplayState,
  /**
   * Called when the user has confirmed a purchase amount and is ready to see partner quotes.
   */
  val onPurchaseAmountConfirmed: (FiatMoney) -> Unit,
)
