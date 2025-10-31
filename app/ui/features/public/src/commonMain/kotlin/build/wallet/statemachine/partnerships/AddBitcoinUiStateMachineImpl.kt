package build.wallet.statemachine.partnerships

import androidx.compose.runtime.*
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.money.FiatMoney
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.partnerships.AddBitcoinUiState.*
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseUiProps
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseUiStateMachine

@BitkeyInject(ActivityScope::class)
class AddBitcoinUiStateMachineImpl(
  val partnershipsPurchaseUiStateMachine: PartnershipsPurchaseUiStateMachine,
) : AddBitcoinUiStateMachine {
  @Composable
  override fun model(props: AddBitcoinUiProps): SheetModel {
    var uiState: AddBitcoinUiState by remember {
      mutableStateOf(
        when (props.initialState) {
          AddBitcoinBottomSheetDisplayState.ShowingPurchaseOrTransferUiState -> ShowingBuyOrTransferUiState
          is AddBitcoinBottomSheetDisplayState.PurchasingUiState -> PurchasingUiState(props.initialState.selectedAmount)
        }
      )
    }
    val showBuyOrTransferState: () -> Unit = {
      uiState = ShowingBuyOrTransferUiState
    }
    return when (val currentState = uiState) {
      ShowingBuyOrTransferUiState ->
        BuyOrTransferModel(
          onPurchase = {
            uiState = PurchasingUiState(selectedAmount = null)
          },
          onTransfer = props.onTransfer,
          onBack = props.onExit
        )

      is PurchasingUiState ->
        partnershipsPurchaseUiStateMachine.model(
          props = PartnershipsPurchaseUiProps(
            selectedAmount = currentState.selectedAmount,
            onPartnerRedirected = props.onPartnerRedirected,
            onBack = showBuyOrTransferState,
            onSelectCustomAmount = props.onSelectCustomAmount,
            onExit = props.onExit
          )
        )
    }
  }
}

private sealed interface AddBitcoinUiState {
  data object ShowingBuyOrTransferUiState : AddBitcoinUiState

  data class PurchasingUiState(val selectedAmount: FiatMoney?) : AddBitcoinUiState
}
