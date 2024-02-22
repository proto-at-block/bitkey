package build.wallet.statemachine.home.full

import build.wallet.coroutines.turbine.turbines
import build.wallet.home.HomeUiBottomSheetDaoMock
import build.wallet.home.HomeUiBottomSheetId
import build.wallet.limit.MobilePayBalanceMock
import build.wallet.limit.SpendingLimitMock
import build.wallet.money.FiatMoney
import build.wallet.money.currency.EUR
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.USD
import build.wallet.statemachine.data.mobilepay.MobilePayData
import build.wallet.statemachine.home.full.bottomsheet.CurrencyChangeMobilePayBottomSheetUpdaterImpl
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.flow.flowOf

class CurrencyChangeMobilePayBottomSheetUpdaterImplTests : FunSpec({

  val homeUiBottomSheetDao = HomeUiBottomSheetDaoMock(turbines::create)
  val updater =
    CurrencyChangeMobilePayBottomSheetUpdaterImpl(
      homeUiBottomSheetDao = homeUiBottomSheetDao
    )

  val limitCurrency = USD
  val otherCurrency = EUR

  test("change currency to different currency than limit sets bottom sheet") {
    updater.setOrClearHomeUiBottomSheet(
      fiatCurrency = otherCurrency,
      mobilePayData = mobilePayEnabledData(limitCurrency)
    )
    val sheet =
      homeUiBottomSheetDao.setHomeUiBottomSheetCalls.awaitItem()
        .shouldBeTypeOf<HomeUiBottomSheetId>()
    sheet.shouldBe(HomeUiBottomSheetId.CURRENCY_CHANGE_RE_ENABLE_MOBILE_PAY)
  }

  test("change currency back to original currency clears bottom sheet") {
    homeUiBottomSheetDao.homeUiBottomSheetFlow =
      flowOf(HomeUiBottomSheetId.CURRENCY_CHANGE_RE_ENABLE_MOBILE_PAY)
    updater.setOrClearHomeUiBottomSheet(
      fiatCurrency = limitCurrency,
      mobilePayData = mobilePayEnabledData(limitCurrency)
    )
    homeUiBottomSheetDao.clearHomeUiBottomSheetCalls.awaitItem()
  }
})

private fun mobilePayEnabledData(currency: FiatCurrency) =
  MobilePayData.MobilePayEnabledData(
    activeSpendingLimit = SpendingLimitMock.copy(amount = FiatMoney(currency, 1.0.toBigDecimal())),
    balance = MobilePayBalanceMock,
    remainingFiatSpendingAmount = FiatMoney.Companion.usd(100.0),
    disableMobilePay = {},
    changeSpendingLimit = { _, _, _, _ -> },
    refreshBalance = {}
  )
