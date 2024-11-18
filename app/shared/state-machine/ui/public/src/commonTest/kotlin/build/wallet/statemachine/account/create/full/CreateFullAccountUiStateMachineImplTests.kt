package build.wallet.statemachine.account.create.full

import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.onboarding.CreateFullAccountContext.NewFullAccount
import build.wallet.onboarding.CreateFullAccountServiceFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.create.full.keybox.create.CreateKeyboxUiProps
import build.wallet.statemachine.account.create.full.keybox.create.CreateKeyboxUiStateMachine
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.account.CreateFullAccountData
import build.wallet.statemachine.data.account.CreateFullAccountData.ActivatingAccountData
import build.wallet.statemachine.data.account.CreateFullAccountData.CreatingAccountData
import build.wallet.statemachine.ui.robots.awaitLoadingScreen
import io.kotest.core.spec.style.FunSpec

class CreateFullAccountUiStateMachineImplTests : FunSpec({

  val createFullAccountService = CreateFullAccountServiceFake()

  val stateMachine =
    CreateAccountUiStateMachineImpl(
      createFullAccountService = createFullAccountService,
      createKeyboxUiStateMachine =
        object : CreateKeyboxUiStateMachine, ScreenStateMachineMock<CreateKeyboxUiProps>(
          "creating-keybox"
        ) {},
      onboardFullAccountUiStateMachine =
        object : OnboardFullAccountUiStateMachine, ScreenStateMachineMock<OnboardFullAccountUiProps>(
          "onboarding-keybox"
        ) {},
      replaceWithLiteAccountRestoreUiStateMachine =
        object : ReplaceWithLiteAccountRestoreUiStateMachine,
          ScreenStateMachineMock<ReplaceWithLiteAccountRestoreUiProps>(
            "replace-with-lite-account-restore"
          ) {},
      overwriteFullAccountCloudBackupUiStateMachine =
        object : OverwriteFullAccountCloudBackupUiStateMachine,
          ScreenStateMachineMock<OverwriteFullAccountCloudBackupUiProps>(
            "overwrite-full-account-cloud-backup"
          ) {}
    )

  val props = CreateAccountUiProps(
    createFullAccountData = CreatingAccountData(
      context = NewFullAccount,
      rollback = {}
    )
  )

  test("CreateKeyboxData screen") {
    stateMachine.test(props) {
      awaitScreenWithBodyModelMock<CreateKeyboxUiProps>()
    }
  }

  test("OnboardKeyboxData screen") {
    stateMachine.test(
      props.copy(
        createFullAccountData = CreateFullAccountData.OnboardingAccountData(
          keybox = KeyboxMock,
          isSkipCloudBackupInstructions = false,
          onFoundLiteAccountWithDifferentId = {},
          onOverwriteFullAccountCloudBackupWarning = {},
          onOnboardingComplete = {}
        )
      )
    ) {
      awaitScreenWithBodyModelMock<OnboardFullAccountUiProps>()
    }
  }

  test("ActivatingAccountData screen") {
    stateMachine.test(props.copy(createFullAccountData = ActivatingAccountData(KeyboxMock))) {
      awaitLoadingScreen(id = null)
    }
  }

  test("OverwriteFullAccountCloudBackupData screen") {
    stateMachine.test(
      props.copy(
        createFullAccountData =
          CreateFullAccountData.OverwriteFullAccountCloudBackupData(
            keybox = KeyboxMock,
            onOverwrite = {},
            rollback = {}
          )
      )
    ) {
      awaitScreenWithBodyModelMock<OverwriteFullAccountCloudBackupUiProps>()
    }
  }
})
