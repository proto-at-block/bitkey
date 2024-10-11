package build.wallet.statemachine.home.full

import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.MobilePayRevampFeatureFlag
import build.wallet.home.HomeUiBottomSheetDaoMock
import build.wallet.home.HomeUiBottomSheetId
import build.wallet.limit.MobilePayEnabledDataMock
import build.wallet.limit.MobilePayServiceMock
import build.wallet.money.currency.EUR
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
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
    mobilePayService = mobilePayService,
    mobilePayRevampFeatureFlag = MobilePayRevampFeatureFlag(featureFlagDao = FeatureFlagDaoFake())
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
    stateMachine.test(props) {
      // Initial state
      awaitItem().shouldBeNull()
      with(awaitItem().shouldNotBeNull().body.shouldBeInstanceOf<FormBodyModel>()) {
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
    stateMachine.test(props) {
      // Initial state
      awaitItem().shouldBeNull()
      awaitItem().shouldNotBeNull().body.shouldBeInstanceOf<FormBodyModel>().onLoaded()
      mobilePayService.disableCalls.awaitItem()
    }
  }

  test("primary button calls onShowSetSpendingLimitFlow") {
    homeUiBottomSheetDao.homeUiBottomSheetFlow =
      flowOf(HomeUiBottomSheetId.CURRENCY_CHANGE_RE_ENABLE_MOBILE_PAY)
    stateMachine.test(props) {
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
