package build.wallet.statemachine.home.full

import app.cash.turbine.Turbine
import build.wallet.money.currency.FiatCurrency
import build.wallet.statemachine.data.mobilepay.MobilePayData
import build.wallet.statemachine.home.full.bottomsheet.CurrencyChangeMobilePayBottomSheetUpdater

class CurrencyChangeMobilePayBottomSheetUpdaterMock(
  turbine: (String) -> Turbine<Any>,
) : CurrencyChangeMobilePayBottomSheetUpdater {
  val setOrClearHomeUiBottomSheetCalls = turbine("setOrClearHomeUiBottomSheet calls")

  override suspend fun setOrClearHomeUiBottomSheet(
    fiatCurrency: FiatCurrency,
    mobilePayData: MobilePayData,
  ) {
    setOrClearHomeUiBottomSheetCalls.add(fiatCurrency)
  }
}
