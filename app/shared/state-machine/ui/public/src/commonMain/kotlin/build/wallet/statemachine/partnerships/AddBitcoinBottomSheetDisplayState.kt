package build.wallet.statemachine.partnerships

import build.wallet.money.FiatMoney

sealed interface AddBitcoinBottomSheetDisplayState {
  data object ShowingPurchaseOrTransferUiState : AddBitcoinBottomSheetDisplayState

  data class PurchasingUiState(val selectedAmount: FiatMoney?) : AddBitcoinBottomSheetDisplayState

  data object TransferringUiState : AddBitcoinBottomSheetDisplayState
}
