package build.wallet.statemachine.home.full.bottomsheet

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.id.CurrencyEventTrackerScreenId.CURRENCY_CHANGE_RE_ENABLE_MOBILE_PAY_SHEET
import build.wallet.bitkey.account.FullAccount
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.feature.flags.MobilePayRevampFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.home.HomeUiBottomSheetDao
import build.wallet.home.HomeUiBottomSheetId.CURRENCY_CHANGE_RE_ENABLE_MOBILE_PAY
import build.wallet.limit.MobilePayData
import build.wallet.limit.MobilePayData.MobilePayDisabledData
import build.wallet.limit.MobilePayData.MobilePayEnabledData
import build.wallet.limit.MobilePayService
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.statemachine.limit.SpendingLimitsCopy
import kotlinx.coroutines.launch

class HomeUiBottomSheetStateMachineImpl(
  private val homeUiBottomSheetDao: HomeUiBottomSheetDao,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val mobilePayService: MobilePayService,
  private val mobilePayRevampFeatureFlag: MobilePayRevampFeatureFlag,
) : HomeUiBottomSheetStateMachine {
  @Composable
  override fun model(props: HomeUiBottomSheetProps): SheetModel? {
    // Observe the global bottom sheet to show on root-level home screens
    // and convert it into a sheet model (below)
    val homeUiBottomSheetId =
      remember { homeUiBottomSheetDao.currentHomeUiBottomSheet() }
        .collectAsState(null)
        .value

    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()
    val mobilePayData by remember { mobilePayService.mobilePayData }
      .collectAsState()

    // Return the persisted model to a SheetModel
    return when (homeUiBottomSheetId) {
      null ->
        null

      CURRENCY_CHANGE_RE_ENABLE_MOBILE_PAY -> {
        CurrencyChangeMobilePayHomeUiBottomSheetModel(
          account = props.account as FullAccount,
          fiatCurrency = fiatCurrency,
          mobilePayData = mobilePayData,
          isMobilePayRevampEnabled = mobilePayRevampFeatureFlag.isEnabled(),
          onClearSheet = {
            homeUiBottomSheetDao.clearHomeUiBottomSheet()
          },
          onShowSetSpendingLimitFlow = props.onShowSetSpendingLimitFlow
        )
      }
    }
  }

  @Composable
  private fun CurrencyChangeMobilePayHomeUiBottomSheetModel(
    account: FullAccount,
    fiatCurrency: FiatCurrency,
    mobilePayData: MobilePayData?,
    isMobilePayRevampEnabled: Boolean,
    onClearSheet: suspend () -> Unit,
    onShowSetSpendingLimitFlow: () -> Unit,
  ): SheetModel? {
    var isClearingBottomSheet by remember { mutableStateOf(false) }
    if (isClearingBottomSheet) {
      LaunchedEffect("close-sheet") {
        onClearSheet()
      }
      isClearingBottomSheet = false
      return null
    }

    // Get the currency from the Mobile Pay data, early returning if there is no currency because
    // there's no reason to show a sheet to the customer in that case
    val mobilePayCurrency =
      when (mobilePayData) {
        null -> return null
        is MobilePayDisabledData ->
          mobilePayData.mostRecentSpendingLimit?.amount?.currency
            ?: return null
        is MobilePayEnabledData -> mobilePayData.activeSpendingLimit.amount.currency
      }

    val oldCurrencyCode = mobilePayCurrency.textCode.code
    val newCurrencyCode = fiatCurrency.textCode.code

    val bottomSheetCopy = SpendingLimitsCopy.get(isRevampOn = isMobilePayRevampEnabled)
      .reenableSpendingLimitBottomSheetCopy(
        oldCurrencyString = oldCurrencyCode,
        newCurrencyString = newCurrencyCode
      )

    val scope = rememberStableCoroutineScope()

    return SheetModel(
      onClosed = { isClearingBottomSheet = true },
      body =
        ErrorFormBodyModel(
          onLoaded = {
            // For Mobile Pay Re-Enable sheet, we only want to actually disable Mobile Pay
            // when the sheet is actually loaded on to the screen. Because if the customer
            // changes the currency back to the limit currency before seeing this (i.e. they
            // are still in the Currency Preference screen), we'll just clear the bottom sheet
            // and keep Mobile Pay enabled.
            when (mobilePayData) {
              is MobilePayDisabledData, null -> Unit
              is MobilePayEnabledData -> {
                scope.launch { mobilePayService.disable(account) }
              }
            }
          },
          title = bottomSheetCopy.title,
          subline = bottomSheetCopy.subline,
          primaryButton =
            ButtonDataModel(
              text = bottomSheetCopy.primaryActionString,
              onClick = {
                isClearingBottomSheet = true
                onShowSetSpendingLimitFlow()
              }
            ),
          onBack = { isClearingBottomSheet = true },
          renderContext = RenderContext.Sheet,
          eventTrackerScreenId = CURRENCY_CHANGE_RE_ENABLE_MOBILE_PAY_SHEET
        )
    )
  }
}
