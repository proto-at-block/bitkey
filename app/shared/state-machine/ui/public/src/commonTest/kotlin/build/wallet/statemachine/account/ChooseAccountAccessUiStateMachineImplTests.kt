package build.wallet.statemachine.account

import bitkey.ui.framework.NavigatorModelFake
import bitkey.ui.framework.NavigatorPresenterFake
import bitkey.ui.screens.demo.DemoModeDisabledScreen
import build.wallet.coroutines.turbine.turbines
import build.wallet.emergencyaccesskit.EmergencyAccessKitAssociation
import build.wallet.emergencyaccesskit.EmergencyAccessKitDataProviderFake
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.InheritanceFeatureFlag
import build.wallet.feature.flags.SoftwareWalletIsEnabledFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.platform.config.AppVariant
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.create.CreateAccountOptionsModel
import build.wallet.statemachine.account.create.CreateSoftwareWalletProps
import build.wallet.statemachine.account.create.CreateSoftwareWalletUiStateMachine
import build.wallet.statemachine.account.create.full.CreateAccountUiProps
import build.wallet.statemachine.account.create.full.CreateAccountUiStateMachine
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.GettingStartedData
import build.wallet.statemachine.dev.DebugMenuProps
import build.wallet.statemachine.dev.DebugMenuStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class ChooseAccountAccessUiStateMachineImplTests : FunSpec({

  val emergencyAccessKitDataProvider = EmergencyAccessKitDataProviderFake()
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
      debugMenuStateMachine = object : DebugMenuStateMachine,
        ScreenStateMachineMock<DebugMenuProps>(
          id = "debug-menu"
        ) {},
      navigatorPresenter = navigatorPresenter,
      deviceInfoProvider = DeviceInfoProviderMock(),
      emergencyAccessKitDataProvider = emergencyAccessKitDataProvider,
      softwareWalletIsEnabledFeatureFlag = softwareWalletIsEnabledFeatureFlag,
      createSoftwareWalletUiStateMachine = createSoftwareWalletUiStateMachine,
      createAccountUiStateMachine = object : CreateAccountUiStateMachine,
        ScreenStateMachineMock<CreateAccountUiProps>(
          id = "create-account"
        ) {},
      inheritanceFeatureFlag = InheritanceFeatureFlag(featureFlagDao = FeatureFlagDaoFake())
    )

  val stateMachine = buildStateMachine(appVariant = AppVariant.Development)

  val startRecoveryCalls = turbines.create<Unit>("startRecovery calls")
  val startLiteAccountCreationCalls = turbines.create<Unit>("startLiteAccountCreation calls")
  val startEmergencyAccessRecoveryCalls =
    turbines.create<Unit>("startEmergencyAccessRecovery calls")
  val wipeExistingDeviceCalls = turbines.create<Unit>("wipeExistingDevice calls")

  val props = ChooseAccountAccessUiProps(
    chooseAccountAccessData = GettingStartedData(
      startRecovery = { startRecoveryCalls.add(Unit) },
      startLiteAccountCreation = { startLiteAccountCreationCalls.add(Unit) },
      startEmergencyAccessRecovery = { startEmergencyAccessRecoveryCalls.add(Unit) },
      wipeExistingDevice = { wipeExistingDeviceCalls.add(Unit) },
      isNavigatingBack = false
    ),
    onSoftwareWalletCreated = {}
  )

  beforeTest {
    emergencyAccessKitDataProvider.reset()
  }

  test("initial state") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<ChooseAccountAccessModel>()
    }
  }

  test("create full account") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<ChooseAccountAccessModel> {
        buttons.first().shouldNotBeNull().onClick()
      }

      awaitBodyMock<CreateAccountUiProps>("create-account")
    }
  }

  test("create full account - is navigating back") {
    stateMachine.testWithVirtualTime(props.copy(props.chooseAccountAccessData.copy(isNavigatingBack = true))) {
      awaitBody<ChooseAccountAccessModel>()
    }
  }

  test("create lite account") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<ChooseAccountAccessModel> {
        buttons[1].shouldNotBeNull().onClick()
      }

      awaitBody<AccountAccessMoreOptionsFormBodyModel> {
        onBeTrustedContactClick()
      }

      awaitBody<BeTrustedContactIntroductionModel> {
        onContinue()
      }

      startLiteAccountCreationCalls.awaitItem()
    }
  }

  test("recover wallet") {
    stateMachine.testWithVirtualTime(props) {
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
      stateMachine.testWithVirtualTime(props) {
        awaitBody<ChooseAccountAccessModel> {
          buttons[0].shouldNotBeNull().onClick()
        }

        awaitBody<CreateAccountOptionsModel>()
      }
    }
  }

  test("wipe existing device") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<ChooseAccountAccessModel> {
        buttons[1].shouldNotBeNull().onClick()
      }

      awaitBody<AccountAccessMoreOptionsFormBodyModel> {
        onResetExistingDevice.shouldNotBeNull().invoke()
      }

      wipeExistingDeviceCalls.awaitItem()
    }
  }

  test("emergency access recovery button shows in eak builds") {
    emergencyAccessKitDataProvider.eakAssociation = EmergencyAccessKitAssociation.EakBuild

    stateMachine.testWithVirtualTime(props) {
      awaitBody<ChooseAccountAccessModel> {
        buttons[1].shouldNotBeNull().onClick()
      }

      awaitBody<EmergencyAccountAccessMoreOptionsFormBodyModel> {
        onRestoreEmergencyAccessKit()
      }

      startEmergencyAccessRecoveryCalls.awaitItem()
    }
  }

  test("shows debug menu in development build") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<ChooseAccountAccessModel> {
        onLogoClick()
      }
      awaitBodyMock<DebugMenuProps> {
        onClose()
      }
      awaitBody<ChooseAccountAccessModel>()
    }
  }

  test("shows demo mode in customer build") {
    val customerStateMachine = buildStateMachine(AppVariant.Customer)
    customerStateMachine.testWithVirtualTime(props) {
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

  test("EAK disabled paths") {
    emergencyAccessKitDataProvider.eakAssociation = EmergencyAccessKitAssociation.EakBuild

    stateMachine.testWithVirtualTime(props) {
      awaitBody<ChooseAccountAccessModel> {
        buttons[0].shouldNotBeNull().onClick()
      }
      awaitItem().should {
        it.alertModel.shouldNotBeNull()
        it.body.shouldBeTypeOf<ChooseAccountAccessModel>()
          .buttons[1].onClick()
      }
      awaitBody<EmergencyAccountAccessMoreOptionsFormBodyModel> {
        onRestoreEmergencyAccessKit()
      }
      startEmergencyAccessRecoveryCalls.awaitItem()
    }
  }
})
