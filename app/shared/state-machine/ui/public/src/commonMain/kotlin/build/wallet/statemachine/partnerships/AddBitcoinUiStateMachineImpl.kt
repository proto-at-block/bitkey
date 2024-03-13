package build.wallet.statemachine.partnerships

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.money.FiatMoney
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.partnerships.AddBitcoinUiState.PurchasingUiState
import build.wallet.statemachine.partnerships.AddBitcoinUiState.ShowingBuyOrTransferUiState
import build.wallet.statemachine.partnerships.AddBitcoinUiState.TransferringUiState
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseUiProps
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseUiStateMachine
import build.wallet.statemachine.partnerships.transfer.PartnershipsTransferUiProps
import build.wallet.statemachine.partnerships.transfer.PartnershipsTransferUiStateMachine

class AddBitcoinUiStateMachineImpl(
  val partnershipsTransferUiStateMachine: PartnershipsTransferUiStateMachine,
  val partnershipsPurchaseUiStateMachine: PartnershipsPurchaseUiStateMachine,
) : AddBitcoinUiStateMachine {
  @Composable
  override fun model(props: AddBitcoinUiProps): SheetModel {
    var uiState: AddBitcoinUiState by remember {
      mutableStateOf(
        props.purchaseAmount?.let { PurchasingUiState(it) } ?: ShowingBuyOrTransferUiState
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
          onTransfer = {
            uiState = TransferringUiState
          },
          onBack = props.onExit
        )

      TransferringUiState ->
        partnershipsTransferUiStateMachine.model(
          props =
            PartnershipsTransferUiProps(
              keybox = props.keybox,
              generateAddress = props.generateAddress,
              onBack = { uiState = ShowingBuyOrTransferUiState },
              onAnotherWalletOrExchange = props.onAnotherWalletOrExchange,
              onPartnerRedirected = props.onPartnerRedirected,
              onExit = props.onExit
            )
        )

      is PurchasingUiState ->
        partnershipsPurchaseUiStateMachine.model(
          props =
            PartnershipsPurchaseUiProps(
              keybox = props.keybox,
              generateAddress = props.generateAddress,
              fiatCurrency = props.fiatCurrency,
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

  data object TransferringUiState : AddBitcoinUiState

  data class PurchasingUiState(val selectedAmount: FiatMoney?) : AddBitcoinUiState
}
