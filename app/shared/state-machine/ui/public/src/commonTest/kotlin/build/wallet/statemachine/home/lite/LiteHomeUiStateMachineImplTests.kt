package build.wallet.statemachine.home.lite

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.relationships.RelationshipsServiceMock
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.account.create.full.CreateAccountUiProps
import build.wallet.statemachine.account.create.full.CreateAccountUiStateMachine
import build.wallet.statemachine.core.test
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.moneyhome.lite.LiteMoneyHomeUiProps
import build.wallet.statemachine.moneyhome.lite.LiteMoneyHomeUiStateMachine
import build.wallet.statemachine.settings.lite.LiteSettingsHomeUiProps
import build.wallet.statemachine.settings.lite.LiteSettingsHomeUiStateMachine
import build.wallet.statemachine.status.HomeStatusBannerUiProps
import build.wallet.statemachine.status.HomeStatusBannerUiStateMachine
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiProps
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiStateMachine
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.ui.model.status.StatusBannerModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LiteHomeUiStateMachineImplTests : FunSpec({

  val relationshipsService = RelationshipsServiceMock(turbines::create)
  val onUpgradeComplete = turbines.create<Unit>("onUpgradeComplete calls")
  val stateMachine =
    LiteHomeUiStateMachineImpl(
      homeStatusBannerUiStateMachine =
        object : HomeStatusBannerUiStateMachine,
          StateMachineMock<HomeStatusBannerUiProps, StatusBannerModel?>(
            initialModel = null
          ) {},
      liteMoneyHomeUiStateMachine =
        object : LiteMoneyHomeUiStateMachine, ScreenStateMachineMock<LiteMoneyHomeUiProps>(
          "money-home"
        ) {},
      liteSettingsHomeUiStateMachine =
        object : LiteSettingsHomeUiStateMachine, ScreenStateMachineMock<LiteSettingsHomeUiProps>(
          "settings"
        ) {},
      trustedContactEnrollmentUiStateMachine =
        object : TrustedContactEnrollmentUiStateMachine,
          ScreenStateMachineMock<TrustedContactEnrollmentUiProps>(
            "trusted-contact-enrollment"
          ) {},
      eventTracker = EventTrackerMock(turbines::create),
      createAccountUiStateMachine = object : CreateAccountUiStateMachine,
        ScreenStateMachineMock<CreateAccountUiProps>(
          "create-account"
        ) {}
    )

  val props = LiteHomeUiProps(
    account = LiteAccountMock,
    onUpgradeComplete = {
      onUpgradeComplete += Unit
    },
    onAppDataDeleted = {}
  )

  beforeTest {
    Router.reset()
    relationshipsService.clear()
  }

  test("money home onSettings shows settings, settings onBack shows money home") {
    stateMachine.testWithVirtualTime(props) {
      awaitBodyMock<LiteMoneyHomeUiProps> {
        onSettings()
      }
      awaitBodyMock<LiteSettingsHomeUiProps> {
        onBack()
      }
      awaitBodyMock<LiteMoneyHomeUiProps>()
    }
  }

  test("deep link routing for beneficiary invite") {
    Router.route = Route.BeneficiaryInvite("inviteCode")

    stateMachine.test(props) {
      awaitBodyMock<LiteMoneyHomeUiProps>()

      awaitBodyMock<TrustedContactEnrollmentUiProps>("trusted-contact-enrollment") {
        inviteCode.shouldBe("inviteCode")
      }
    }
  }
})
