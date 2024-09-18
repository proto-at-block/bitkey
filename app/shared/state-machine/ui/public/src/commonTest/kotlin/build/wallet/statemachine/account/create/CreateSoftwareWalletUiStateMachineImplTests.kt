package build.wallet.statemachine.account.create

import build.wallet.bitkey.keybox.SoftwareAccountMock
import build.wallet.onboarding.CreateSoftwareWalletServiceFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiProps
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachine
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.notifications.NotificationPreferencesProps
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class CreateSoftwareWalletUiStateMachineImplTests : FunSpec({
  val createSoftwareWalletService = CreateSoftwareWalletServiceFake()
  val notificationPreferencesSetupUiStateMachine = object : NotificationPreferencesSetupUiStateMachine,
    ScreenStateMachineMock<NotificationPreferencesSetupUiProps>(
      id = "notification-preferences"
    ) {}

  val stateMachine = CreateSoftwareWalletUiStateMachineImpl(
    createSoftwareWalletService = createSoftwareWalletService,
    notificationPreferencesSetupUiStateMachine = notificationPreferencesSetupUiStateMachine
  )

  val props = CreateSoftwareWalletProps(
    onExit = {},
    onSuccess = {}
  )

  test("happy path") {
    stateMachine.test(props) {
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitScreenWithBodyModelMock<NotificationPreferencesSetupUiProps> {
        accountId.shouldBe(SoftwareAccountMock.accountId)
        accountConfig.shouldBe(SoftwareAccountMock.config)
        source.shouldBe(NotificationPreferencesProps.Source.Onboarding)
        onComplete()
      }
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Success)
        message.shouldNotBeNull().shouldBe("Software Wallet Created")
      }
    }
  }
})
