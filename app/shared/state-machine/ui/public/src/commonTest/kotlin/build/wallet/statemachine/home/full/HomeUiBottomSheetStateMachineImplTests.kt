package build.wallet.statemachine.home.full

import build.wallet.coroutines.turbine.turbines
import build.wallet.home.HomeUiBottomSheetDaoMock
import build.wallet.home.HomeUiBottomSheetId
import build.wallet.money.currency.EUR
import build.wallet.money.currency.USD
import build.wallet.platform.web.BrowserNavigator
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.mobilepay.MobilePayEnabledDataMock
import build.wallet.statemachine.home.full.bottomsheet.HomeUiBottomSheetProps
import build.wallet.statemachine.home.full.bottomsheet.HomeUiBottomSheetStateMachineImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.flow.flowOf

class HomeUiBottomSheetStateMachineImplTests : FunSpec({

  val homeUiBottomSheetDao = HomeUiBottomSheetDaoMock(turbines::create)
  val stateMachine =
    HomeUiBottomSheetStateMachineImpl(
      homeUiBottomSheetDao = homeUiBottomSheetDao
    )

  val limitCurrency = USD
  val otherCurrency = EUR

  val disableMobilePayCalls = turbines.create<Unit>("disableMobilePay calls")
  val onShowSetSpendingLimitFlowCalls = turbines.create<Unit>("onShowSetSpendingLimitFlow calls")
  val props =
    HomeUiBottomSheetProps(
      fiatCurrency = limitCurrency,
      mobilePayData =
        MobilePayEnabledDataMock.copy(
          disableMobilePay = { disableMobilePayCalls.add(Unit) }
        ),
      onShowSetSpendingLimitFlow = { onShowSetSpendingLimitFlowCalls.add(Unit) }
    )

  test("sheet model contents") {
    homeUiBottomSheetDao.homeUiBottomSheetFlow =
      flowOf(HomeUiBottomSheetId.CURRENCY_CHANGE_RE_ENABLE_MOBILE_PAY)
    stateMachine.test(props.copy(otherCurrency)) {
      // Initial state
      awaitItem().shouldBeNull()
      with(awaitItem().shouldNotBeNull().body.shouldBeTypeOf<FormBodyModel>()) {
        header?.headline.shouldBe("Re-enable Mobile Pay")
        header?.sublineModel?.string.shouldBe(
          "We noticed that you changed your currency from USD to EUR. Please make sure your Mobile Pay amount is correct."
        )
        primaryButton?.text.shouldBe("Enable Mobile Pay")
      }
    }
  }

  test("sheet model onLoaded disables mobile pay") {
    homeUiBottomSheetDao.homeUiBottomSheetFlow =
      flowOf(HomeUiBottomSheetId.CURRENCY_CHANGE_RE_ENABLE_MOBILE_PAY)
    stateMachine.test(props.copy(otherCurrency)) {
      // Initial state
      awaitItem().shouldBeNull()
      awaitItem().shouldNotBeNull().body.shouldBeTypeOf<FormBodyModel>().onLoaded(
        BrowserNavigator {}
      )
      disableMobilePayCalls.awaitItem()
    }
  }

  test("primary button calls onShowSetSpendingLimitFlow") {
    homeUiBottomSheetDao.homeUiBottomSheetFlow =
      flowOf(HomeUiBottomSheetId.CURRENCY_CHANGE_RE_ENABLE_MOBILE_PAY)
    stateMachine.test(props.copy(otherCurrency)) {
      // Initial state
      awaitItem().shouldBeNull()
      val formModel = awaitItem().shouldNotBeNull().body.shouldBeTypeOf<FormBodyModel>()
      formModel.primaryButton.shouldNotBeNull().onClick()
      homeUiBottomSheetDao.clearHomeUiBottomSheetCalls.awaitItem()
      // Emits null while it's being cleared
      awaitItem().shouldBeNull()
      onShowSetSpendingLimitFlowCalls.awaitItem()

      // Re-emits because the flow emission didn't change
      awaitItem().shouldNotBeNull()
    }
  }
})
