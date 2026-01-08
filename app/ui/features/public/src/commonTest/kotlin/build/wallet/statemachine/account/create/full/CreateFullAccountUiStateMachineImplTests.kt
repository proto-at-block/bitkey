package build.wallet.statemachine.account.create.full

import app.cash.turbine.plusAssign
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.cloud.backup.AllLiteAccountBackupMocks
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.CloudBackupV3
import build.wallet.coroutines.turbine.turbines
import build.wallet.onboarding.CreateFullAccountContext.NewFullAccount
import build.wallet.onboarding.OnboardFullAccountServiceFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.create.full.keybox.create.CreateKeyboxUiProps
import build.wallet.statemachine.account.create.full.keybox.create.CreateKeyboxUiStateMachine
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import io.kotest.core.spec.style.FunSpec

class CreateFullAccountUiStateMachineImplTests : FunSpec({

  val createFullAccountService = OnboardFullAccountServiceFake()
  val rollbackCalls = turbines.create<Unit>("rollback calls")
  val onboardingCompleteCalls = turbines.create<Unit>("onboarding complete calls")

  val stateMachine =
    CreateAccountUiStateMachineImpl(
      onboardFullAccountService = createFullAccountService,
      createKeyboxUiStateMachine =
        object : CreateKeyboxUiStateMachine, ScreenStateMachineMock<CreateKeyboxUiProps>(
          "creating-keybox"
        ) {},
      onboardFullAccountUiStateMachine =
        object : OnboardFullAccountUiStateMachine,
          ScreenStateMachineMock<OnboardFullAccountUiProps>(
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
    context = NewFullAccount,
    rollback = {
      rollbackCalls += Unit
    },
    fullAccount = FullAccountMock,
    onOnboardingComplete = {
      onboardingCompleteCalls += Unit
    }
  )

  test("start with existing full account") {
    stateMachine.test(props) {
      awaitBodyMock<OnboardFullAccountUiProps> {
        onOnboardingComplete()
      }

      onboardingCompleteCalls.awaitItem()

      awaitBody<LoadingSuccessBodyModel>()
    }
  }

  test("start with no account") {
    stateMachine.test(props.copy(fullAccount = null)) {
      awaitBodyMock<CreateKeyboxUiProps> {
        onAccountCreated(FullAccountMock)
      }

      awaitBodyMock<OnboardFullAccountUiProps> {
        onOnboardingComplete()
      }

      onboardingCompleteCalls.awaitItem()

      awaitBody<LoadingSuccessBodyModel>()
    }
  }

  context("onboarding failure - found lite account with different ID") {
    AllLiteAccountBackupMocks.forEach { backup ->
      val backupVersion = when (backup) {
        is CloudBackupV2 -> "v2"
        is CloudBackupV3 -> "v3"
        else -> "unknown"
      }

      context("backup $backupVersion") {
        test("replaces different lite account and upgrades to full account") {
          stateMachine.test(props) {
            awaitBodyMock<OnboardFullAccountUiProps> {
              onFoundLiteAccountWithDifferentId(backup as CloudBackup)
            }

            awaitBodyMock<ReplaceWithLiteAccountRestoreUiProps> {
              onAccountUpgraded(FullAccountMock)
            }

            awaitBodyMock<OnboardFullAccountUiProps> {
              onOnboardingComplete()
            }

            onboardingCompleteCalls.awaitItem()

            awaitBody<LoadingSuccessBodyModel>()
          }
        }
      }
    }
  }

  context("start with existing lite account backup") {
    AllLiteAccountBackupMocks.forEach { backup ->
      val backupVersion = when (backup) {
        is CloudBackupV2 -> "v2"
        is CloudBackupV3 -> "v3"
        else -> "unknown"
      }

      context("backup $backupVersion") {
        test("upgrades existing lite account backup to full account") {
          stateMachine.test(props) {
            awaitBodyMock<OnboardFullAccountUiProps> {
              onFoundLiteAccountWithDifferentId(backup as CloudBackup)
            }

            awaitBodyMock<ReplaceWithLiteAccountRestoreUiProps> {
              onAccountUpgraded(FullAccountMock)
            }

            awaitBodyMock<OnboardFullAccountUiProps> {
              onOnboardingComplete()
            }

            onboardingCompleteCalls.awaitItem()

            awaitBody<LoadingSuccessBodyModel>()
          }
        }
      }
    }
  }

  test("start with existing full account backup") {
    stateMachine.test(props) {
      awaitBodyMock<OnboardFullAccountUiProps> {
        onOverwriteFullAccountCloudBackupWarning()
      }

      awaitBodyMock<OverwriteFullAccountCloudBackupUiProps> {
        onOverwrite()
      }

      awaitBodyMock<OnboardFullAccountUiProps> {
        onOnboardingComplete()
      }

      onboardingCompleteCalls.awaitItem()

      awaitBody<LoadingSuccessBodyModel>()
    }
  }
})
