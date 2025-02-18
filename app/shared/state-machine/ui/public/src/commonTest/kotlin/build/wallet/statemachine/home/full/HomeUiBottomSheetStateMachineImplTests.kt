package build.wallet.statemachine.home.full

import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.home.HomeUiBottomSheetDaoMock
import build.wallet.home.HomeUiBottomSheetId
import build.wallet.limit.MobilePayEnabledDataMock
import build.wallet.limit.MobilePayServiceMock
import build.wallet.money.currency.EUR
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.home.full.bottomsheet.HomeUiBottomSheetProps
import build.wallet.statemachine.home.full.bottomsheet.HomeUiBottomSheetStateMachineImpl
import build.wallet.statemachine.ui.clickPrimaryButton
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.flowOf

class HomeUiBottomSheetStateMachineImplTests : FunSpec({

  val homeUiBottomSheetDao = HomeUiBottomSheetDaoMock(turbines::create)
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)
  val mobilePayService = MobilePayServiceMock(turbines::create)

  val stateMachine = HomeUiBottomSheetStateMachineImpl(
    homeUiBottomSheetDao = homeUiBottomSheetDao,
    fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
    mobilePayService = mobilePayService
  )

  val onShowSetSpendingLimitFlowCalls = turbines.create<Unit>("onShowSetSpendingLimitFlow calls")
  val props = HomeUiBottomSheetProps(
    account = FullAccountMock,
    onShowSetSpendingLimitFlow = { onShowSetSpendingLimitFlowCalls.add(Unit) }
  )

  beforeTest {
    mobilePayService.reset()

    mobilePayService.mobilePayData.value = MobilePayEnabledDataMock
    fiatCurrencyPreferenceRepository.internalFiatCurrencyPreference.value = EUR
  }

  test("sheet model contents") {
    homeUiBottomSheetDao.homeUiBottomSheetFlow =
      flowOf(HomeUiBottomSheetId.CURRENCY_CHANGE_RE_ENABLE_MOBILE_PAY)
    stateMachine.testWithVirtualTime(props) {
      // Initial state
      awaitItem().shouldBeNull()
      with(awaitItem().shouldNotBeNull().body.shouldBeInstanceOf<FormBodyModel>()) {
        header?.headline.shouldBe("Update daily limit")
        header?.sublineModel?.string.shouldBe(
          "Your currency changed from USD to EUR. It's a good idea to update your daily limit in the new currency."
        )
        primaryButton?.text.shouldBe("Update daily limit")
      }
    }
  }

  test("sheet model onLoaded disables transfer settings") {
    homeUiBottomSheetDao.homeUiBottomSheetFlow =
      flowOf(HomeUiBottomSheetId.CURRENCY_CHANGE_RE_ENABLE_MOBILE_PAY)
    stateMachine.testWithVirtualTime(props) {
      // Initial state
      awaitItem().shouldBeNull()
      awaitItem().shouldNotBeNull().body.shouldBeInstanceOf<FormBodyModel>().onLoaded?.invoke()
      mobilePayService.disableCalls.awaitItem()
    }
  }

  test("primary button calls onShowSetSpendingLimitFlow") {
    homeUiBottomSheetDao.homeUiBottomSheetFlow =
      flowOf(HomeUiBottomSheetId.CURRENCY_CHANGE_RE_ENABLE_MOBILE_PAY)
    stateMachine.testWithVirtualTime(props) {
      // Initial state
      awaitItem().shouldBeNull()
      val formModel = awaitItem().shouldNotBeNull().body.shouldBeInstanceOf<FormBodyModel>()
      formModel.clickPrimaryButton()
      homeUiBottomSheetDao.clearHomeUiBottomSheetCalls.awaitItem()
      // Emits null while it's being cleared
      awaitItem().shouldBeNull()
      onShowSetSpendingLimitFlowCalls.awaitItem()

      // Re-emits because the flow emission didn't change
      awaitItem().shouldNotBeNull()
    }
  }
})
