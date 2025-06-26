package build.wallet.statemachine.account

import app.cash.turbine.plusAssign
import bitkey.ui.framework.NavigatorModelFake
import bitkey.ui.framework.NavigatorPresenterFake
import bitkey.ui.screens.demo.DemoModeDisabledScreen
import build.wallet.coroutines.turbine.turbines
import build.wallet.emergencyexitkit.EmergencyExitKitAssociation
import build.wallet.emergencyexitkit.EmergencyExitKitDataProviderFake
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.SoftwareWalletIsEnabledFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.platform.config.AppVariant
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.create.CreateAccountOptionsModel
import build.wallet.statemachine.account.create.CreateSoftwareWalletProps
import build.wallet.statemachine.account.create.CreateSoftwareWalletUiStateMachine
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.GettingStartedData
import build.wallet.statemachine.dev.DebugMenuScreen
import build.wallet.statemachine.ui.awaitBody
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class ChooseAccountAccessUiStateMachineImplTests : FunSpec({

  val emergencyExitKitDataProvider = EmergencyExitKitDataProviderFake()
  val featureFlagDao = FeatureFlagDaoFake()
  val softwareWalletIsEnabledFeatureFlag = SoftwareWalletIsEnabledFeatureFlag(featureFlagDao)
  val createSoftwareWalletUiStateMachine = object : CreateSoftwareWalletUiStateMachine,
    ScreenStateMachineMock<CreateSoftwareWalletProps>(
      id = "create-software-wallet"
    ) {}
  val navigatorPresenter = NavigatorPresenterFake()

  fun buildStateMachine(appVariant: AppVariant) =
    ChooseAccountAccessUiStateMachineImpl(
      appVariant = appVariant,
      navigatorPresenter = navigatorPresenter,
      deviceInfoProvider = DeviceInfoProviderMock(),
      emergencyExitKitDataProvider = emergencyExitKitDataProvider,
      softwareWalletIsEnabledFeatureFlag = softwareWalletIsEnabledFeatureFlag,
      createSoftwareWalletUiStateMachine = createSoftwareWalletUiStateMachine
    )

  val stateMachine = buildStateMachine(appVariant = AppVariant.Development)

  val startRecoveryCalls = turbines.create<Unit>("startRecovery calls")
  val startLiteAccountCreationCalls = turbines.create<Unit>("startLiteAccountCreation calls")
  val startEmergencyExitRecoveryCalls =
    turbines.create<Unit>("startEmergencyExitRecovery calls")
  val onCreateFullAccountCalls = turbines.create<Unit>("onCreateFullAccount calls")

  val props = ChooseAccountAccessUiProps(
    chooseAccountAccessData = GettingStartedData(
      startRecovery = { startRecoveryCalls.add(Unit) },
      startLiteAccountCreation = { startLiteAccountCreationCalls.add(Unit) },
      startEmergencyExitRecovery = { startEmergencyExitRecoveryCalls.add(Unit) }
    ),
    onSoftwareWalletCreated = {},
    onCreateFullAccount = { onCreateFullAccountCalls += Unit }
  )

  beforeTest {
    emergencyExitKitDataProvider.reset()
    featureFlagDao.reset()
  }

  test("initial state") {
    stateMachine.test(props) {
      awaitBody<ChooseAccountAccessModel>()
    }
  }

  test("create full account") {
    stateMachine.test(props) {
      awaitBody<ChooseAccountAccessModel> {
        buttons.first().shouldNotBeNull().onClick()
      }

      onCreateFullAccountCalls.awaitItem()
    }
  }

  test("create lite account") {
    stateMachine.test(props) {
      awaitBody<ChooseAccountAccessModel> {
        buttons[1].shouldNotBeNull().onClick()
      }

      awaitBody<AccountAccessMoreOptionsFormBodyModel> {
        onBeTrustedContactClick()
      }

      startLiteAccountCreationCalls.awaitItem()
    }
  }

  test("recover wallet") {
    stateMachine.test(props) {
      awaitBody<ChooseAccountAccessModel> {
        buttons[1].shouldNotBeNull().onClick()
      }

      awaitBody<AccountAccessMoreOptionsFormBodyModel> {
        onRestoreYourWalletClick()
      }

      startRecoveryCalls.awaitItem()
    }
  }

  context("software wallet flag is on") {
    softwareWalletIsEnabledFeatureFlag.setFlagValue(true)

    test("create hardware and software wallet options are shown") {
      stateMachine.test(props) {
        awaitBody<ChooseAccountAccessModel> {
          buttons[0].shouldNotBeNull().onClick()
        }

        awaitBody<CreateAccountOptionsModel>()
      }
    }
  }

  test("Emergency Exit Kit recovery button shows in EEK builds") {
    emergencyExitKitDataProvider.eekAssociation = EmergencyExitKitAssociation.EekBuild

    stateMachine.test(props) {
      awaitBody<ChooseAccountAccessModel> {
        buttons[1].shouldNotBeNull().onClick()
      }

      awaitBody<EmergencyAccountAccessMoreOptionsFormBodyModel> {
        onRestoreEmergencyExitKit()
      }

      startEmergencyExitRecoveryCalls.awaitItem()
    }
  }

  test("shows debug menu in development build") {
    stateMachine.test(props) {
      awaitBody<ChooseAccountAccessModel> {
        onLogoClick()
      }
      awaitBody<NavigatorModelFake> {
        initialScreen.shouldBe(DebugMenuScreen)
        onExit()
      }

      awaitBody<ChooseAccountAccessModel>()
    }
  }

  test("shows demo mode in customer build") {
    val customerStateMachine = buildStateMachine(AppVariant.Customer)
    customerStateMachine.test(props) {
      awaitBody<ChooseAccountAccessModel> {
        onLogoClick()
      }
      awaitBody<NavigatorModelFake> {
        initialScreen.shouldBe(DemoModeDisabledScreen)
        onExit()
      }
      awaitBody<ChooseAccountAccessModel>()
    }
  }

  test("EEK disabled paths") {
    emergencyExitKitDataProvider.eekAssociation = EmergencyExitKitAssociation.EekBuild

    stateMachine.test(props) {
      awaitBody<ChooseAccountAccessModel> {
        buttons[0].shouldNotBeNull().onClick()
      }
      awaitItem().should {
        it.alertModel.shouldNotBeNull()
        it.body.shouldBeTypeOf<ChooseAccountAccessModel>()
          .buttons[1].onClick()
      }
      awaitBody<EmergencyAccountAccessMoreOptionsFormBodyModel> {
        onRestoreEmergencyExitKit()
      }
      startEmergencyExitRecoveryCalls.awaitItem()
    }
  }
})
