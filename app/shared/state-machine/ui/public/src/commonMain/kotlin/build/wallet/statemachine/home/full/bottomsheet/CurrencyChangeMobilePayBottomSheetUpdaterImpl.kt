package build.wallet.statemachine.home.full.bottomsheet

import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.home.HomeUiBottomSheetDao
import build.wallet.home.HomeUiBottomSheetId.CURRENCY_CHANGE_RE_ENABLE_MOBILE_PAY
import build.wallet.limit.MobilePayData
import build.wallet.limit.MobilePayData.MobilePayDisabledData
import build.wallet.limit.MobilePayData.MobilePayEnabledData
import build.wallet.money.currency.FiatCurrency
import kotlinx.coroutines.flow.firstOrNull

@BitkeyInject(ActivityScope::class)
class CurrencyChangeMobilePayBottomSheetUpdaterImpl(
  private val homeUiBottomSheetDao: HomeUiBottomSheetDao,
) : CurrencyChangeMobilePayBottomSheetUpdater {
  override suspend fun setOrClearHomeUiBottomSheet(
    fiatCurrency: FiatCurrency,
    mobilePayData: MobilePayData?,
  ) {
    val currentBottomSheetId = homeUiBottomSheetDao.currentHomeUiBottomSheet().firstOrNull()

    when (mobilePayData) {
      null, is MobilePayDisabledData ->
        // Mobile Pay is not enabled. Nothing to do.
        Unit

      is MobilePayEnabledData -> {
        // Mobile Pay is enabled. If the current fiat currency preference does not match the
        // current Mobile Pay limit, we need to show an alert to the customer. We don't fully
        // disable Mobile Pay yet, because we want to keep it on if the customer switches the
        // preference back to the limit currency before seeing the alert (the bottom sheet alert
        // only shows on Money Home or Settings).
        if (fiatCurrency != mobilePayData.activeSpendingLimit.amount.currency) {
          // The currencies differ. Set a bottom sheet to be shown if needed.
          if (currentBottomSheetId == null) {
            homeUiBottomSheetDao.setHomeUiBottomSheet(CURRENCY_CHANGE_RE_ENABLE_MOBILE_PAY)
          }
        } else if (currentBottomSheetId == CURRENCY_CHANGE_RE_ENABLE_MOBILE_PAY) {
          // The currencies are the same and there is a Mobile Pay Re-Enable sheet set to
          // be shown. Clear it.
          homeUiBottomSheetDao.clearHomeUiBottomSheet()
        }
      }
    }
  }
}
