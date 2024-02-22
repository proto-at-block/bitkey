package build.wallet.statemachine.home.full.bottomsheet

import build.wallet.money.currency.FiatCurrency
import build.wallet.statemachine.data.mobilepay.MobilePayData

/**
 * Handles listening to changes in currency selection and setting a [HomeUiBottomSheet] to be
 * shown (or not shown) as appropriate.
 *
 * Note: The actual disabling of Mobile Pay does not happen until the bottom sheet is shown to
 * the customer, so that action takes place in the [onLoaded] of the sheet and is set in the
 * model by [HomeUiBottomSheetStateMachine].
 */
interface CurrencyChangeMobilePayBottomSheetUpdater {
  suspend fun setOrClearHomeUiBottomSheet(
    fiatCurrency: FiatCurrency,
    mobilePayData: MobilePayData,
  )
}
