package build.wallet.statemachine.moneyhome.lite

import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.bitkey.socrec.ProtectedCustomerFake
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.HasActiveLiteAccountDataFake
import build.wallet.statemachine.moneyhome.MoneyHomeButtonsModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.recovery.socrec.help.HelpingWithRecoveryUiProps
import build.wallet.statemachine.recovery.socrec.help.HelpingWithRecoveryUiStateMachine
import build.wallet.statemachine.recovery.socrec.view.ViewingProtectedCustomerProps
import build.wallet.statemachine.recovery.socrec.view.ViewingProtectedCustomerUiStateMachine
import build.wallet.statemachine.status.StatusBannerModelMock
import build.wallet.ui.model.list.ListItemAccessory.ButtonAccessory
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class LiteMoneyHomeUiStateMachineImplTests : FunSpec({
  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)
  val stateMachine =
    LiteMoneyHomeUiStateMachineImpl(
      inAppBrowserNavigator = inAppBrowserNavigator,
      viewingProtectedCustomerUiStateMachine =
        object : ViewingProtectedCustomerUiStateMachine, ScreenStateMachineMock<ViewingProtectedCustomerProps>(
          "protected-customer-detail"
        ) {},
      helpingWithRecoveryUiStateMachine =
        object : HelpingWithRecoveryUiStateMachine, ScreenStateMachineMock<HelpingWithRecoveryUiProps>(
          "helping-with-recovery"
        ) {}
    )

  val propsOnRemoveRelationshipCalls =
    turbines.create<ProtectedCustomer>(
      "props onRemoveRelationship call"
    )
  val propsOnSettingsCalls = turbines.create<Unit>("props onSettings call")
  val onAcceptInvite = turbines.create<Unit>("props onAcceptInvite call")
  val propsOnUpgradeAccountCalls = turbines.create<Unit>("props onUpgradeAccount call")
  val props =
    LiteMoneyHomeUiProps(
      accountData =
        HasActiveLiteAccountDataFake.copy(
          onUpgradeAccount = { propsOnUpgradeAccountCalls.add(Unit) }
        ),
      protectedCustomers = immutableListOf(),
      homeStatusBannerModel = null,
      onRemoveRelationship = {
        propsOnRemoveRelationshipCalls.add(it)
        Ok(Unit)
      },
      onSettings = { propsOnSettingsCalls.add(Unit) },
      onAcceptInvite = { onAcceptInvite.add(Unit) }
    )

  beforeTest {
    inAppBrowserNavigator.reset()
  }

  test("initially shows Money Home screen") {
    stateMachine.test(props) {
      awaitScreenWithBody<LiteMoneyHomeBodyModel>()
    }
  }

  test("settings tap invokes props") {
    stateMachine.test(props) {
      awaitScreenWithBody<LiteMoneyHomeBodyModel> {
        trailingToolbarAccessoryModel
          .shouldBeTypeOf<ToolbarAccessoryModel.IconAccessory>()
          .model.onClick.invoke()
      }

      propsOnSettingsCalls.awaitItem()
    }
  }

  test(
    "protected customer tap shows bottom sheet and sheet button calls props remove relationship"
  ) {
    val protectedCustomer = ProtectedCustomerFake
    stateMachine.test(props.copy(protectedCustomers = immutableListOf(protectedCustomer))) {
      // Showing Money Home, tap on first row (first protected customer)
      // of "Wallets you're Protecting" card (which is the first card)
      awaitScreenWithBody<LiteMoneyHomeBodyModel> {
        cardsModel.cards.first()
          .content.shouldNotBeNull()
          .shouldBeTypeOf<CardModel.CardContent.DrillList>()
          .items.first().onClick.shouldNotBeNull().invoke()
      }

      awaitScreenWithBodyModelMock<ViewingProtectedCustomerProps> {
        onRemoveProtectedCustomer()
        propsOnRemoveRelationshipCalls.awaitItem()
          .shouldBe(protectedCustomer)
        onExit()
      }

      awaitScreenWithBody<LiteMoneyHomeBodyModel>()
    }
  }

  test(
    "Accept Invite button calls invokes props"
  ) {
    stateMachine.test(props.copy(protectedCustomers = immutableListOf())) {
      // Showing Money Home, tap on first row (first protected customer)
      // of "Wallets you're Protecting" card (which is the first card)
      awaitScreenWithBody<LiteMoneyHomeBodyModel> {
        cardsModel.cards.first()
          .content.shouldNotBeNull()
          .shouldBeTypeOf<CardModel.CardContent.DrillList>()
          .items.last()
          .leadingAccessory.shouldNotBeNull().shouldBeTypeOf<ButtonAccessory>()
          .model.onClick()
      }

      onAcceptInvite.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("buy bitkey card calls in-app browser") {
    stateMachine.test(props) {
      // Showing Money Home, tap on "Buy Your Own Bitkey" card
      // (which is the first card when there's no protected customers)
      awaitScreenWithBody<LiteMoneyHomeBodyModel> {
        cardsModel.cards.last().onClick.shouldNotBeNull().invoke()
      }

      awaitScreenWithBody<InAppBrowserModel> {
        open()
      }

      inAppBrowserNavigator.onOpenCalls.awaitItem()
        .shouldBe("https://bitkey.world/")

      inAppBrowserNavigator.onCloseCallback.shouldNotBeNull().invoke()
      awaitScreenWithBody<LiteMoneyHomeBodyModel>()
    }
  }

  test("set up bitkey button invokes props") {
    stateMachine.test(props) {
      // Showing Money Home, tap on "Buy Your Own Bitkey" card
      // (which is the first card when there's no protected customers)
      awaitScreenWithBody<LiteMoneyHomeBodyModel> {
        buttonsModel
          .shouldBeTypeOf<MoneyHomeButtonsModel.SingleButtonModel>()
          .button.onClick()
      }

      propsOnUpgradeAccountCalls.awaitItem()
    }
  }

  test("shows status bar from props") {
    stateMachine.test(props.copy(homeStatusBannerModel = StatusBannerModelMock)) {
      awaitItem().statusBannerModel.shouldNotBeNull()
    }
  }
})
