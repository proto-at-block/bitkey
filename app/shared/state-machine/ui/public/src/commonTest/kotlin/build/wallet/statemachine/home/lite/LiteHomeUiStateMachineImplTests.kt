package build.wallet.statemachine.home.lite

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.relationships.RelationshipsServiceMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.HasActiveLiteAccountDataFake
import build.wallet.statemachine.moneyhome.lite.LiteMoneyHomeUiProps
import build.wallet.statemachine.moneyhome.lite.LiteMoneyHomeUiStateMachine
import build.wallet.statemachine.recovery.socrec.LiteTrustedContactManagementProps
import build.wallet.statemachine.recovery.socrec.LiteTrustedContactManagementUiStateMachine
import build.wallet.statemachine.settings.lite.LiteSettingsHomeUiProps
import build.wallet.statemachine.settings.lite.LiteSettingsHomeUiStateMachine
import build.wallet.statemachine.status.HomeStatusBannerUiProps
import build.wallet.statemachine.status.HomeStatusBannerUiStateMachine
import build.wallet.ui.model.status.StatusBannerModel
import io.kotest.core.spec.style.FunSpec

class LiteHomeUiStateMachineImplTests : FunSpec({

  val relationshipsService = RelationshipsServiceMock(turbines::create)
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
      liteTrustedContactManagementUiStateMachine =
        object : LiteTrustedContactManagementUiStateMachine,
          ScreenStateMachineMock<LiteTrustedContactManagementProps>(
            "lite-trusted-contact-management"
          ) {},
      eventTracker = EventTrackerMock(turbines::create)
    )

  val props =
    LiteHomeUiProps(
      accountData = HasActiveLiteAccountDataFake
    )

  beforeTest {
    relationshipsService.clear()
  }

  test("money home onSettings shows settings, settings onBack shows money home") {
    stateMachine.test(props) {
      awaitScreenWithBodyModelMock<LiteMoneyHomeUiProps> {
        onSettings()
      }
      awaitScreenWithBodyModelMock<LiteSettingsHomeUiProps> {
        onBack()
      }
      awaitScreenWithBodyModelMock<LiteMoneyHomeUiProps>()
    }
  }
})
