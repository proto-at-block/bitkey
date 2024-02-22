package build.wallet.statemachine.settings.lite

import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.socrec.ProtectedCustomerFake
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoMock
import build.wallet.feature.setFlagValue
import build.wallet.money.MultipleFiatCurrencyEnabledFeatureFlag
import build.wallet.money.display.CurrencyPreferenceDataMock
import build.wallet.recovery.socrec.SocRecRelationshipsRepositoryMock
import build.wallet.statemachine.BodyStateMachineMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.firmware.FirmwareDataUpToDateMock
import build.wallet.statemachine.data.keybox.HasActiveLiteAccountDataFake
import build.wallet.statemachine.money.currency.CurrencyPreferenceProps
import build.wallet.statemachine.money.currency.CurrencyPreferenceUiStateMachine
import build.wallet.statemachine.recovery.socrec.LiteTrustedContactManagementProps
import build.wallet.statemachine.recovery.socrec.LiteTrustedContactManagementUiStateMachine
import build.wallet.statemachine.settings.SettingsListUiProps
import build.wallet.statemachine.settings.SettingsListUiStateMachine
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiProps
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiStateMachine
import build.wallet.statemachine.status.StatusBannerModelMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class LiteSettingsHomeUiStateMachineImplTests : FunSpec({
  val multipleFiatCurrencyEnabledFeatureFlag =
    MultipleFiatCurrencyEnabledFeatureFlag(FeatureFlagDaoMock())

  val stateMachine =
    LiteSettingsHomeUiStateMachineImpl(
      currencyPreferenceUiStateMachine =
        object : CurrencyPreferenceUiStateMachine, ScreenStateMachineMock<CurrencyPreferenceProps>(
          "currency-preference"
        ) {},
      helpCenterUiStateMachine =
        object : HelpCenterUiStateMachine, ScreenStateMachineMock<HelpCenterUiProps>(
          "help-center"
        ) {},
      liteTrustedContactManagementUiStateMachine =
        object : LiteTrustedContactManagementUiStateMachine, ScreenStateMachineMock<LiteTrustedContactManagementProps>(
          "tc-management"
        ) {},
      settingsListUiStateMachine =
        object : SettingsListUiStateMachine, BodyStateMachineMock<SettingsListUiProps>(
          "settings-list"
        ) {}
    )

  val socrecRepositoryMock = SocRecRelationshipsRepositoryMock(turbines::create)
  val propsOnBackCalls = turbines.create<Unit>("props onBack calls")
  val props =
    LiteSettingsHomeUiProps(
      accountData = HasActiveLiteAccountDataFake,
      currencyPreferenceData = CurrencyPreferenceDataMock,
      firmwareData = FirmwareDataUpToDateMock,
      protectedCustomers = immutableListOf(),
      homeStatusBannerModel = null,
      socRecLiteAccountActions = socrecRepositoryMock.toActions(LiteAccountMock),
      onBack = { propsOnBackCalls.add(Unit) }
    )

  beforeEach {
    multipleFiatCurrencyEnabledFeatureFlag.apply {
      setFlagValue(defaultFlagValue)
    }
  }

  test("onBack calls props onBack") {
    stateMachine.test(props) {
      awaitScreenWithBodyModelMock<SettingsListUiProps> {
        onBack()
      }
      propsOnBackCalls.awaitItem()
    }
  }

  test("trusted contacts calls props onRemoveProtectedCustomer") {
    val protectedCustomer = ProtectedCustomerFake
    stateMachine.test(props) {
      awaitScreenWithBodyModelMock<SettingsListUiProps> {
        supportedRows.first { it is SettingsListUiProps.SettingsListRow.TrustedContacts }.onClick()
      }
      awaitScreenWithBodyModelMock<LiteTrustedContactManagementProps> {
        actions.removeProtectedCustomer(protectedCustomer)
      }
      socrecRepositoryMock.removeRelationshipCalls.awaitItem()
    }
  }

  test("settings list") {
    stateMachine.test(props) {
      awaitScreenWithBodyModelMock<SettingsListUiProps> {
        supportedRows
          .map { it::class }.toSet()
          .shouldBe(
            setOf(
              SettingsListUiProps.SettingsListRow.TrustedContacts::class,
              SettingsListUiProps.SettingsListRow.CurrencyPreference::class,
              SettingsListUiProps.SettingsListRow.HelpCenter::class
            )
          )
      }
    }
  }

  test("open and close currency preference") {
    multipleFiatCurrencyEnabledFeatureFlag.setFlagValue(true)
    stateMachine.test(props) {
      awaitScreenWithBodyModelMock<SettingsListUiProps> {
        supportedRows.first { it is SettingsListUiProps.SettingsListRow.CurrencyPreference }.onClick()
      }
      awaitScreenWithBodyModelMock<CurrencyPreferenceProps> {
        onBack.shouldNotBeNull().invoke()
      }
      awaitScreenWithBodyModelMock<SettingsListUiProps>()
    }
  }

  test("open and close trusted contact management") {
    stateMachine.test(props) {
      awaitScreenWithBodyModelMock<SettingsListUiProps> {
        supportedRows.first { it is SettingsListUiProps.SettingsListRow.TrustedContacts }.onClick()
      }
      awaitScreenWithBodyModelMock<LiteTrustedContactManagementProps> {
        onExit()
      }
      awaitScreenWithBodyModelMock<SettingsListUiProps>()
    }
  }

  test("open and close help center") {
    stateMachine.test(props) {
      awaitScreenWithBodyModelMock<SettingsListUiProps> {
        supportedRows.first { it is SettingsListUiProps.SettingsListRow.HelpCenter }.onClick()
      }
      awaitScreenWithBodyModelMock<HelpCenterUiProps> {
        onBack()
      }
      awaitScreenWithBodyModelMock<SettingsListUiProps>()
    }
  }

  test("shows status bar from props") {
    stateMachine.test(props.copy(homeStatusBannerModel = StatusBannerModelMock)) {
      awaitItem().statusBannerModel.shouldNotBeNull()
    }
  }
})
