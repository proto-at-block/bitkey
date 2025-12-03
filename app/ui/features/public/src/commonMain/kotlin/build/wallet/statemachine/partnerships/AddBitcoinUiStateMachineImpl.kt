package build.wallet.statemachine.partnerships

import androidx.compose.runtime.*
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.money.FiatMoney
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.partnerships.AddBitcoinUiState.*
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseAmountUiProps
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseAmountUiStateMachine

@BitkeyInject(ActivityScope::class)
class AddBitcoinUiStateMachineImpl(
  private val partnershipsPurchaseAmountUiStateMachine: PartnershipsPurchaseAmountUiStateMachine,
) : AddBitcoinUiStateMachine {
  @Composable
  override fun model(props: AddBitcoinUiProps): SheetModel {
    var uiState: AddBitcoinUiState by remember {
      mutableStateOf(
        when (props.initialState) {
          AddBitcoinBottomSheetDisplayState.ShowingPurchaseOrTransferUiState -> ShowingBuyOrTransferUiState
          is AddBitcoinBottomSheetDisplayState.PurchasingUiState -> SelectingPurchaseAmountUiState(
            props.initialState.selectedAmount
          )
        }
      )
    }
    return when (val currentState = uiState) {
      ShowingBuyOrTransferUiState ->
        BuyOrTransferModel(
          onPurchase = {
            uiState = SelectingPurchaseAmountUiState(selectedAmount = null)
          },
          onTransfer = props.onTransfer,
          onBack = props.onExit
        )

      is SelectingPurchaseAmountUiState ->
        partnershipsPurchaseAmountUiStateMachine.model(
          props = PartnershipsPurchaseAmountUiProps(
            selectedAmount = currentState.selectedAmount,
            onAmountConfirmed = { amount ->
              props.onPurchaseAmountConfirmed(amount)
            },
            onSelectCustomAmount = props.onSelectCustomAmount,
            onExit = props.onExit
          )
        )
    }
  }
}

private sealed interface AddBitcoinUiState {
  data object ShowingBuyOrTransferUiState : AddBitcoinUiState

  data class SelectingPurchaseAmountUiState(val selectedAmount: FiatMoney?) : AddBitcoinUiState
}
