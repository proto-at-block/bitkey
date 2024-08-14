package build.wallet.statemachine.account.create.full

import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.create.full.keybox.create.CreateKeyboxUiProps
import build.wallet.statemachine.account.create.full.keybox.create.CreateKeyboxUiStateMachine
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.account.CreateFullAccountData
import build.wallet.statemachine.data.account.CreateFullAccountData.ActivateKeyboxDataFull.ActivatingKeyboxDataFull
import build.wallet.statemachine.data.account.create.onboard.SettingNotificationsPreferencesDataMock
import build.wallet.statemachine.ui.robots.awaitLoadingScreen
import io.kotest.core.spec.style.FunSpec

class CreateFullAccountUiStateMachineImplTests : FunSpec({

  val stateMachine =
    CreateAccountUiStateMachineImpl(
      createKeyboxUiStateMachine =
        object : CreateKeyboxUiStateMachine, ScreenStateMachineMock<CreateKeyboxUiProps>(
          "creating-keybox"
        ) {},
      onboardKeyboxUiStateMachine =
        object : OnboardKeyboxUiStateMachine, ScreenStateMachineMock<OnboardKeyboxUiProps>(
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
    createFullAccountData =
      CreateFullAccountData.CreateKeyboxData.CreatingAppKeysData(
        rollback = {}
      )
  )

  test("CreateKeyboxData screen") {
    stateMachine.test(props) {
      awaitScreenWithBodyModelMock<CreateKeyboxUiProps>()
    }
  }

  test("OnboardKeyboxData screen") {
    stateMachine.test(props.copy(createFullAccountData = SettingNotificationsPreferencesDataMock)) {
      awaitScreenWithBodyModelMock<OnboardKeyboxUiProps>()
    }
  }

  test("ActivateKeyboxData screen") {
    stateMachine.test(props.copy(createFullAccountData = ActivatingKeyboxDataFull)) {
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
