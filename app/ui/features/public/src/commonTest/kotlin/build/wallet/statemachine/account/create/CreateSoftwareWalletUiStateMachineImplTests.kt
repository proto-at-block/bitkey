package build.wallet.statemachine.account.create

import build.wallet.bitkey.keybox.SoftwareAccountMock
import build.wallet.onboarding.OnboardSoftwareAccountServiceFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiProps
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachine
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.notifications.NotificationPreferencesProps
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class CreateSoftwareWalletUiStateMachineImplTests : FunSpec({
  val createSoftwareWalletService = OnboardSoftwareAccountServiceFake()
  val notificationPreferencesSetupUiStateMachine =
    object : NotificationPreferencesSetupUiStateMachine,
      ScreenStateMachineMock<NotificationPreferencesSetupUiProps>(
        id = "notification-preferences"
      ) {}

  val stateMachine = CreateSoftwareWalletUiStateMachineImpl(
    onboardSoftwareAccountService = createSoftwareWalletService,
    notificationPreferencesSetupUiStateMachine = notificationPreferencesSetupUiStateMachine
  )

  val props = CreateSoftwareWalletProps(
    onExit = {},
    onSuccess = {}
  )

  test("happy path") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBodyMock<NotificationPreferencesSetupUiProps> {
        accountId.shouldBe(SoftwareAccountMock.accountId)
        source.shouldBe(NotificationPreferencesProps.Source.Onboarding)
        onComplete()
      }
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Success)
        message.shouldNotBeNull().shouldBe("Software Wallet Created")
      }
    }
  }
})
