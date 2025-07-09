package build.wallet.statemachine.settings.lite

import app.cash.turbine.plusAssign
import bitkey.ui.framework.NavigatorPresenterFake
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.config.AppVariant
import build.wallet.statemachine.BodyStateMachineMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.money.currency.AppearancePreferenceProps
import build.wallet.statemachine.money.currency.AppearancePreferenceUiStateMachine
import build.wallet.statemachine.recovery.socrec.LiteTrustedContactManagementProps
import build.wallet.statemachine.recovery.socrec.LiteTrustedContactManagementUiStateMachine
import build.wallet.statemachine.settings.SettingsListUiProps
import build.wallet.statemachine.settings.SettingsListUiProps.SettingsListRow.DebugMenu
import build.wallet.statemachine.settings.SettingsListUiStateMachine
import build.wallet.statemachine.settings.full.feedback.FeedbackUiProps
import build.wallet.statemachine.settings.full.feedback.FeedbackUiStateMachine
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiProps
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiStateMachine
import build.wallet.statemachine.status.StatusBannerModelMock
import build.wallet.statemachine.ui.awaitBodyMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class LiteSettingsHomeUiStateMachineImplTests : FunSpec({
  val navigatorPresenter = NavigatorPresenterFake()

  fun stateMachine(appVariant: AppVariant = AppVariant.Customer) =
    LiteSettingsHomeUiStateMachineImpl(
      appVariant = appVariant,
      appearancePreferenceUiStateMachine =
        object : AppearancePreferenceUiStateMachine,
          ScreenStateMachineMock<AppearancePreferenceProps>(
            "currency-preference"
          ) {},
      helpCenterUiStateMachine =
        object : HelpCenterUiStateMachine, ScreenStateMachineMock<HelpCenterUiProps>(
          "help-center"
        ) {},
      liteTrustedContactManagementUiStateMachine = object :
        LiteTrustedContactManagementUiStateMachine,
        ScreenStateMachineMock<LiteTrustedContactManagementProps>(
          "tc-management"
        ) {},
      settingsListUiStateMachine =
        object : SettingsListUiStateMachine, BodyStateMachineMock<SettingsListUiProps>(
          "settings-list"
        ) {},
      feedbackUiStateMachine = object : FeedbackUiStateMachine,
        ScreenStateMachineMock<FeedbackUiProps>(
          "feedback"
        ) {},
      navigatorPresenter = navigatorPresenter
    )

  val propsOnBackCalls = turbines.create<Unit>("props onBack calls")
  val onAccountUpgradedCalls = turbines.create<FullAccount>("onAccountUpgraded calls")
  val props = LiteSettingsHomeUiProps(
    account = LiteAccountMock,
    homeStatusBannerModel = null,
    onBack = { propsOnBackCalls.add(Unit) },
    onAccountUpgraded = {
      onAccountUpgradedCalls += it
    }
  )

  test("onBack calls props onBack") {
    stateMachine().test(props) {
      awaitBodyMock<SettingsListUiProps> {
        onBack()
      }
      propsOnBackCalls.awaitItem()
    }
  }

  test("settings list") {
    stateMachine().test(props) {
      awaitBodyMock<SettingsListUiProps> {
        supportedRows
          .map { it::class }.toSet()
          .shouldBe(
            setOf(
              SettingsListUiProps.SettingsListRow.TrustedContacts::class,
              SettingsListUiProps.SettingsListRow.AppearancePreference::class,
              SettingsListUiProps.SettingsListRow.ContactUs::class,
              SettingsListUiProps.SettingsListRow.HelpCenter::class
            )
          )
      }
    }
  }

  test("open and close currency preference") {
    stateMachine().test(props) {
      awaitBodyMock<SettingsListUiProps> {
        supportedRows.first { it is SettingsListUiProps.SettingsListRow.AppearancePreference }
          .onClick()
      }
      awaitBodyMock<AppearancePreferenceProps> {
        onBack.shouldNotBeNull().invoke()
      }
      awaitBodyMock<SettingsListUiProps>()
    }
  }

  test("open and close trusted contact management") {
    stateMachine().test(props) {
      awaitBodyMock<SettingsListUiProps> {
        supportedRows.first { it is SettingsListUiProps.SettingsListRow.TrustedContacts }.onClick()
      }
      awaitBodyMock<LiteTrustedContactManagementProps> {
        onExit()
      }
      awaitBodyMock<SettingsListUiProps>()
    }
  }

  test("open and close help center") {
    stateMachine().test(props) {
      awaitBodyMock<SettingsListUiProps> {
        supportedRows.first { it is SettingsListUiProps.SettingsListRow.HelpCenter }.onClick()
      }
      awaitBodyMock<HelpCenterUiProps> {
        onBack()
      }
      awaitBodyMock<SettingsListUiProps>()
    }
  }

  test("shows status bar from props") {
    stateMachine().test(props.copy(homeStatusBannerModel = StatusBannerModelMock)) {
      awaitItem().statusBannerModel.shouldNotBeNull()
    }
  }

  test("onAccountUpgraded is called by trusted contact management") {
    stateMachine().test(props) {
      awaitBodyMock<SettingsListUiProps> {
        supportedRows.first { it is SettingsListUiProps.SettingsListRow.TrustedContacts }.onClick()
      }
      awaitBodyMock<LiteTrustedContactManagementProps> {
        onAccountUpgraded(FullAccountMock)
      }

      onAccountUpgradedCalls.awaitItem().shouldBe(FullAccountMock)
    }
  }

  context("debug menu") {
    test("enabled in AppVariant.Team") {
      stateMachine(AppVariant.Team).test(props) {
        awaitBodyMock<SettingsListUiProps> {
          supportedRows.single { it is DebugMenu }
        }
      }
    }

    test("enabled in AppVariant.Development") {
      stateMachine(AppVariant.Development).test(props) {
        awaitBodyMock<SettingsListUiProps> {
          supportedRows.single { it is DebugMenu }
        }
      }
    }

    test("disabled in AppVariant.Customer") {
      stateMachine(AppVariant.Customer).test(props) {
        awaitBodyMock<SettingsListUiProps> {
          supportedRows.none { it is DebugMenu }
        }
      }
    }

    test("disabled in AppVariant.Emergency") {
      stateMachine(AppVariant.Emergency).test(props) {
        awaitBodyMock<SettingsListUiProps> {
          supportedRows.none { it is DebugMenu }
        }
      }
    }
  }
})
