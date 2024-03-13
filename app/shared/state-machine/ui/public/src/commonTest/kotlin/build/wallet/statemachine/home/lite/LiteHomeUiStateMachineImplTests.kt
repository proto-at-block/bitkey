package build.wallet.statemachine.home.lite

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.ProtectedCustomerAlias
import build.wallet.bitkey.socrec.ProtectedCustomerFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.display.CurrencyPreferenceDataMock
import build.wallet.recovery.socrec.SocRecRelationshipsRepositoryMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.firmware.FirmwareDataUpToDateMock
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
import io.kotest.matchers.shouldBe

class LiteHomeUiStateMachineImplTests : FunSpec({

  val socRecRelationshipsRepository = SocRecRelationshipsRepositoryMock(turbines::create)
  val stateMachine =
    LiteHomeUiStateMachineImpl(
      homeStatusBannerUiStateMachine =
        object : HomeStatusBannerUiStateMachine, StateMachineMock<HomeStatusBannerUiProps, StatusBannerModel?>(
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
        object : LiteTrustedContactManagementUiStateMachine, ScreenStateMachineMock<LiteTrustedContactManagementProps>(
          "lite-trusted-contact-management"
        ) {},
      socRecRelationshipsRepository = socRecRelationshipsRepository,
      eventTracker = EventTrackerMock(turbines::create)
    )

  val props =
    LiteHomeUiProps(
      accountData = HasActiveLiteAccountDataFake,
      currencyPreferenceData = CurrencyPreferenceDataMock,
      firmwareData = FirmwareDataUpToDateMock
    )

  test("launches soc rec relationships sync") {
    stateMachine.test(props) {
      socRecRelationshipsRepository.launchSyncCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("money home onRemoveRelationship calls soc rec repository") {
    val customer = ProtectedCustomer("relationship-id", ProtectedCustomerAlias("allison"))
    stateMachine.test(props) {
      socRecRelationshipsRepository.launchSyncCalls.awaitItem()

      awaitItem() // Initial loading

      awaitScreenWithBodyModelMock<LiteMoneyHomeUiProps> {
        onRemoveRelationship(customer)
      }
      socRecRelationshipsRepository.removeRelationshipCalls.awaitItem()
        .shouldBe(customer.recoveryRelationshipId)
    }
  }

  test("money home onSettings shows settings, settings onBack shows money home") {
    stateMachine.test(props) {
      socRecRelationshipsRepository.launchSyncCalls.awaitItem()

      awaitItem() // Initial loading

      awaitScreenWithBodyModelMock<LiteMoneyHomeUiProps> {
        onSettings()
      }
      awaitScreenWithBodyModelMock<LiteSettingsHomeUiProps> {
        onBack()
      }
      awaitScreenWithBodyModelMock<LiteMoneyHomeUiProps>()
    }
  }

  test("settings onRemoveProtectedCustomer calls soc rec repository") {
    val relationshipId = "relationship-id"
    stateMachine.test(props) {
      socRecRelationshipsRepository.launchSyncCalls.awaitItem()

      awaitItem() // Initial loading

      awaitScreenWithBodyModelMock<LiteMoneyHomeUiProps> {
        onSettings()
      }
      awaitScreenWithBodyModelMock<LiteSettingsHomeUiProps> {
        socRecLiteAccountActions.removeProtectedCustomer(ProtectedCustomerFake.copy(relationshipId))
      }
      socRecRelationshipsRepository.removeRelationshipCalls.awaitItem()
        .shouldBe(relationshipId)
    }
  }
})
