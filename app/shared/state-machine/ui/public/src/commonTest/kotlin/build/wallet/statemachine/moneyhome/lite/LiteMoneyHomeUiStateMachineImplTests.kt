package build.wallet.statemachine.moneyhome.lite

import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.relationships.ProtectedCustomerFake
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.relationships.Relationships
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.InheritanceFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.recovery.socrec.SocRecServiceFake
import build.wallet.router.Router
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.moneyhome.MoneyHomeButtonsModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.recovery.socrec.help.HelpingWithRecoveryUiProps
import build.wallet.statemachine.recovery.socrec.help.HelpingWithRecoveryUiStateMachine
import build.wallet.statemachine.recovery.socrec.view.ViewingProtectedCustomerProps
import build.wallet.statemachine.recovery.socrec.view.ViewingProtectedCustomerUiStateMachine
import build.wallet.statemachine.status.StatusBannerModelMock
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.ui.model.list.ListItemAccessory.ButtonAccessory
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class LiteMoneyHomeUiStateMachineImplTests : FunSpec({
  val socRecService = SocRecServiceFake()
  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)
  val inheritanceFeatureFlag = InheritanceFeatureFlag(FeatureFlagDaoFake())
  val stateMachine = LiteMoneyHomeUiStateMachineImpl(
    socRecService = socRecService,
    inAppBrowserNavigator = inAppBrowserNavigator,
    viewingProtectedCustomerUiStateMachine =
      object : ViewingProtectedCustomerUiStateMachine,
        ScreenStateMachineMock<ViewingProtectedCustomerProps>(
          "protected-customer-detail"
        ) {},
    helpingWithRecoveryUiStateMachine = object : HelpingWithRecoveryUiStateMachine,
      ScreenStateMachineMock<HelpingWithRecoveryUiProps>(
        "helping-with-recovery"
      ) {},
    inheritanceFeatureFlag = inheritanceFeatureFlag
  )

  val propsOnSettingsCalls = turbines.create<Unit>("props onSettings call")
  val onAcceptInvite = turbines.create<Unit>("props onAcceptInvite call")
  val propsOnUpgradeAccountCalls = turbines.create<Unit>("props onUpgradeAccount call")
  val props = LiteMoneyHomeUiProps(
    homeStatusBannerModel = null,
    onSettings = { propsOnSettingsCalls.add(Unit) },
    onAcceptInvite = { onAcceptInvite.add(Unit) },
    account = LiteAccountMock,
    onUpgradeAccount = { propsOnUpgradeAccountCalls.add(Unit) },
    onBecomeBeneficiary = {}
  )

  beforeTest {
    inAppBrowserNavigator.reset()
    socRecService.reset()
    Router.reset()
    inheritanceFeatureFlag.reset()
  }

  test("initially shows Money Home screen") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<LiteMoneyHomeBodyModel>()
    }
  }

  test("settings tap invokes props") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<LiteMoneyHomeBodyModel> {
        trailingToolbarAccessoryModel
          .shouldBeTypeOf<ToolbarAccessoryModel.IconAccessory>()
          .model.onClick.invoke()
      }

      propsOnSettingsCalls.awaitItem()
    }
  }

  test("protected customer tap shows bottom sheet") {
    val protectedCustomer = ProtectedCustomerFake
    socRecService.socRecRelationships.value = Relationships.EMPTY.copy(
      protectedCustomers = immutableListOf(protectedCustomer)
    )
    stateMachine.testWithVirtualTime(props) {
      // Showing Money Home, tap on first row (first protected customer)
      // of "Wallets you're Protecting" card (which is the first card)
      awaitBody<LiteMoneyHomeBodyModel> {
        cardsModel.cards.first()
          .content.shouldNotBeNull()
          .shouldBeTypeOf<CardModel.CardContent.DrillList>()
          .items.first().onClick.shouldNotBeNull().invoke()
      }

      awaitBodyMock<ViewingProtectedCustomerProps> {
        onExit()
      }

      awaitBody<LiteMoneyHomeBodyModel>()
    }
  }

  test("Accept Invite button calls invokes props") {
    stateMachine.testWithVirtualTime(props) {
      // Showing Money Home, tap on first row (first protected customer)
      // of "Wallets you're Protecting" card (which is the first card)
      awaitBody<LiteMoneyHomeBodyModel> {
        cardsModel.cards.first()
          .content.shouldNotBeNull()
          .shouldBeTypeOf<CardModel.CardContent.DrillList>()
          .items.last()
          .leadingAccessory.shouldNotBeNull().shouldBeTypeOf<ButtonAccessory>()
          .model.onClick()
      }

      onAcceptInvite.awaitItem()
    }
  }

  test("buy bitkey card calls in-app browser") {
    stateMachine.testWithVirtualTime(props) {
      // Showing Money Home, tap on "Buy Your Own Bitkey" card
      // (which is the first card when there's no protected customers)
      awaitBody<LiteMoneyHomeBodyModel> {
        cardsModel.cards.last().onClick.shouldNotBeNull().invoke()
      }

      awaitBody<InAppBrowserModel> {
        open()
      }

      inAppBrowserNavigator.onOpenCalls.awaitItem()
        .shouldBe("https://bitkey.world/")

      inAppBrowserNavigator.onCloseCallback.shouldNotBeNull().invoke()
      awaitBody<LiteMoneyHomeBodyModel>()
    }
  }

  test("set up bitkey button invokes props") {
    stateMachine.testWithVirtualTime(props) {
      // Showing Money Home, tap on "Buy Your Own Bitkey" card
      // (which is the first card when there's no protected customers)
      awaitBody<LiteMoneyHomeBodyModel> {
        buttonsModel
          .shouldBeTypeOf<MoneyHomeButtonsModel.SingleButtonModel>()
          .button.onClick()
      }

      propsOnUpgradeAccountCalls.awaitItem()
    }
  }

  test("shows status bar from props") {
    stateMachine.testWithVirtualTime(props.copy(homeStatusBannerModel = StatusBannerModelMock)) {
      awaitItem().statusBannerModel.shouldNotBeNull()
    }
  }

  test("inheritance card is shown with feature flag enabled") {
    inheritanceFeatureFlag.setFlagValue(true)
    stateMachine.test(props) {
      awaitBody<LiteMoneyHomeBodyModel> {
        cardsModel.cards.size
          .shouldBe(3)
      }
    }
  }

  test("inheritance card is not shown with feature flag disabled") {
    inheritanceFeatureFlag.setFlagValue(false)
    stateMachine.test(props) {
      awaitBody<LiteMoneyHomeBodyModel> {
        cardsModel.cards.size
          .shouldBe(2)
      }
    }
  }
})
