package build.wallet.statemachine.recovery.cloud

import app.cash.turbine.plusAssign
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.cloud.backup.AllFullAccountBackupMocks
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupError.UnrectifiableCloudBackupError
import build.wallet.cloud.backup.CloudBackupRepositoryFake
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.CloudBackupV2WithLiteAccountMock
import build.wallet.cloud.backup.CloudBackupV3
import build.wallet.cloud.backup.CloudBackupV3WithLiteAccountMock
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.cloud.CloudSignInFailedScreenModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.StartIntent
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class AccessCloudBackupStateMachineImplTests : FunSpec({

  context("parameterized tests for all backup versions") {
    AllFullAccountBackupMocks.forEach { backup ->
      val backupVersion = when (backup) {
        is CloudBackupV2 -> "v2"
        is CloudBackupV3 -> "v3"
      }

      context("backup $backupVersion") {
        val accountId = FullAccountIdMock
        val fakeCloudAccount = CloudAccountMock(instanceId = "1")
        val fakeBackup = backup
        val cloudBackupRepository = CloudBackupRepositoryFake()
        val selectCloudBackupUiStateMachine = object : SelectCloudBackupUiStateMachine,
          ScreenStateMachineMock<SelectCloudBackupUiProps>("select-cloud-backup") {}
        val stateMachine =
          AccessCloudBackupUiStateMachineImpl(
            cloudSignInUiStateMachine = CloudSignInUiStateMachineMock(),
            cloudBackupRepository = cloudBackupRepository,
            rectifiableErrorHandlingUiStateMachine = RectifiableErrorHandlingUiStateMachineMock(),
            deviceInfoProvider = DeviceInfoProviderMock(),
            inAppBrowserNavigator = InAppBrowserNavigatorMock { name ->
              turbines.create("$backupVersion-$name")
            },
            selectCloudBackupUiStateMachine = selectCloudBackupUiStateMachine
          )

        val exitCalls = turbines.create<Unit>("$backupVersion-exit calls")
        val importEmergencyExitKitCalls =
          turbines.create<Unit>("$backupVersion-import Emergency Exit Kit calls")
        val onStartCloudRecoveryCalls =
          turbines.create<List<CloudBackup>>("$backupVersion-start cloud recovery calls")
        val onStartLiteAccountRecoveryCalls =
          turbines.create<CloudBackup>("$backupVersion-start lite account recovery calls")
        val onStartLostAppRecoveryCalls =
          turbines.create<Unit>("$backupVersion-start lost app recovery calls")
        val onStartLiteAccountCreationCalls =
          turbines.create<Unit>("$backupVersion-start lite account creation calls")

        val props = AccessCloudBackupUiProps(
          onExit = {
            exitCalls += Unit
          },
          onImportEmergencyExitKit = {
            importEmergencyExitKitCalls += Unit
          },
          startIntent = StartIntent.BeTrustedContact,
          inviteCode = "inviteCode",
          onStartCloudRecovery = { _, backups ->
            onStartCloudRecoveryCalls += backups
          },
          onStartLiteAccountRecovery = {
            onStartLiteAccountRecoveryCalls += it
          },
          onStartLostAppRecovery = {
            onStartLostAppRecoveryCalls += Unit
          },
          onStartLiteAccountCreation = { _, _ ->
            onStartLiteAccountCreationCalls += Unit
          },
          showErrorOnBackupMissing = true
        )

        afterTest {
          cloudBackupRepository.reset()
        }

        test("successfully find backup and restore it") {
          cloudBackupRepository.writeBackup(accountId, fakeCloudAccount, fakeBackup, true)

          stateMachine.test(props) {
            awaitBodyMock<CloudSignInUiProps> {
              onSignedIn(fakeCloudAccount)
            }

            awaitBody<LoadingSuccessBodyModel> {
              state.shouldBe(LoadingSuccessBodyModel.State.Loading)
            }

            onStartCloudRecoveryCalls.awaitItem().shouldBe(listOf(fakeBackup))
          }
        }

        test("cloud account signed in but cloud backup not found") {
          stateMachine.test(props) {
            awaitBodyMock<CloudSignInUiProps> {
              onSignedIn(fakeCloudAccount)
            }

            awaitBody<LoadingSuccessBodyModel> {
              state.shouldBe(LoadingSuccessBodyModel.State.Loading)
            }
            awaitBody<CloudWarningBodyModel>()
          }
        }

        test("cloud account signed in but failure when trying to access cloud backup") {
          cloudBackupRepository.returnReadError = UnrectifiableCloudBackupError(Exception("oops"))

          stateMachine.test(props) {
            awaitBodyMock<CloudSignInUiProps> {
              onSignedIn(fakeCloudAccount)
            }

            awaitBody<LoadingSuccessBodyModel> {
              state.shouldBe(LoadingSuccessBodyModel.State.Loading)
            }
            awaitBody<CloudWarningBodyModel>()
          }
        }

        test("cloud account signed in but cloud backup not found - exit") {
          stateMachine.test(props) {
            awaitBodyMock<CloudSignInUiProps> {
              onSignedIn(fakeCloudAccount)
            }

            awaitBody<LoadingSuccessBodyModel> {
              state.shouldBe(LoadingSuccessBodyModel.State.Loading)
            }
            awaitBody<CloudWarningBodyModel> {
              onBack()
            }

            exitCalls.awaitItem().shouldBe(Unit)
          }
        }

        // See AccessCloudBackupStateMachineImplTests{Android,IOS} for the "check again" tests.
        // The behavior on the two platforms is slightly different.

        test("cloud account signed in but cloud backup not found - cannot access cloud option") {
          stateMachine.test(props) {
            awaitBodyMock<CloudSignInUiProps> {
              onSignedIn(fakeCloudAccount)
            }

            awaitBody<LoadingSuccessBodyModel> {
              state.shouldBe(LoadingSuccessBodyModel.State.Loading)
            }

            awaitBody<CloudWarningBodyModel> {
              cloudBackupRepository.writeBackup(accountId, fakeCloudAccount, fakeBackup, true)
              onCannotAccessCloud()
            }

            onStartLiteAccountCreationCalls.awaitItem()
          }
        }

        /*
         The "Be a Trusted Contact" flow uses this state machine to check for cloud sign in
         and a possible backup before continuing the invite flow.
         If sign in fails, it should not show the rest of the recovery options, but instead the generic
         cloud sign in failure screen.
         If cloud sign in succeeds should proceed as if a backup was found.
         */
        test("cloud account sign in failed from trusted contact flow - does not show recovery options") {
          stateMachine.test(props.copy(showErrorOnBackupMissing = false)) {
            awaitBodyMock<CloudSignInUiProps> {
              onSignInFailure(Error())
            }

            awaitBody<CloudSignInFailedScreenModel>()
          }
        }

        test("cloud account signed in but cloud backup not found from trusted contact flow - proceeds as if found") {
          stateMachine.test(props.copy(showErrorOnBackupMissing = false)) {
            awaitBodyMock<CloudSignInUiProps> {
              onSignedIn(fakeCloudAccount)
            }

            awaitBody<LoadingSuccessBodyModel> {
              state.shouldBe(LoadingSuccessBodyModel.State.Loading)
            }

            onStartLiteAccountCreationCalls.awaitItem()
          }
        }

        test("cloud account sign in failed - start Emergency Exit Kit recovery") {
          stateMachine.test(props) {
            awaitBodyMock<CloudSignInUiProps> {
              onSignInFailure(Error())
            }

            awaitBody<CloudWarningBodyModel> {
              onImportEmergencyExitKit.shouldNotBeNull().invoke()
            }

            importEmergencyExitKitCalls.awaitItem().shouldBe(Unit)
          }
        }

        test("shows backup selection screen when multiple backups with at least one lite account exist") {
          val liteBackup = when (fakeBackup) {
            is CloudBackupV2 -> CloudBackupV2WithLiteAccountMock as CloudBackup
            is CloudBackupV3 -> CloudBackupV3WithLiteAccountMock as CloudBackup
            else -> error("Unsupported backup type")
          }
          val fullBackup1 = fakeBackup
          val fullBackup2 = when (fakeBackup) {
            is CloudBackupV2 -> (fakeBackup as CloudBackupV2).copy(
              accountId = "different-account-id"
            ) as CloudBackup
            is CloudBackupV3 -> (fakeBackup as CloudBackupV3).copy(
              accountId = "different-account-id"
            ) as CloudBackup
            else -> error("Unsupported backup type")
          }

          cloudBackupRepository.writeBackup(accountId, fakeCloudAccount, liteBackup, true)
          cloudBackupRepository.writeBackup(accountId, fakeCloudAccount, fullBackup1, true)
          cloudBackupRepository.writeBackup(accountId, fakeCloudAccount, fullBackup2, true)

          stateMachine.test(props) {
            awaitBodyMock<CloudSignInUiProps> {
              onSignedIn(fakeCloudAccount)
            }

            awaitBody<LoadingSuccessBodyModel> {
              state.shouldBe(LoadingSuccessBodyModel.State.Loading)
            }

            // Should show selection screen
            awaitBodyMock<SelectCloudBackupUiProps> {
              backups.size.shouldBe(3)
            }
          }
        }

        test("passes all full account backups when multiple full accounts exist") {
          val fullBackup1 = fakeBackup
          val fullBackup2 = when (fakeBackup) {
            is CloudBackupV2 -> (fakeBackup as CloudBackupV2).copy(
              accountId = "different-account-id"
            ) as CloudBackup
            is CloudBackupV3 -> (fakeBackup as CloudBackupV3).copy(
              accountId = "different-account-id"
            ) as CloudBackup
            else -> error("Unsupported backup type")
          }

          cloudBackupRepository.writeBackup(accountId, fakeCloudAccount, fullBackup1, true)
          cloudBackupRepository.writeBackup(accountId, fakeCloudAccount, fullBackup2, true)

          stateMachine.test(props) {
            awaitBodyMock<CloudSignInUiProps> {
              onSignedIn(fakeCloudAccount)
            }

            awaitBody<LoadingSuccessBodyModel> {
              state.shouldBe(LoadingSuccessBodyModel.State.Loading)
            }

            // Should pass all backups to recovery callback
            val backups = onStartCloudRecoveryCalls.awaitItem()
            backups.size.shouldBe(2)
            backups.shouldContain(fullBackup1)
            backups.shouldContain(fullBackup2)
          }
        }

        test("single full account backup proceeds directly without selection") {
          cloudBackupRepository.writeBackup(accountId, fakeCloudAccount, fakeBackup, true)

          stateMachine.test(props) {
            awaitBodyMock<CloudSignInUiProps> {
              onSignedIn(fakeCloudAccount)
            }

            awaitBody<LoadingSuccessBodyModel> {
              state.shouldBe(LoadingSuccessBodyModel.State.Loading)
            }

            // Should pass single backup in a list
            onStartCloudRecoveryCalls.awaitItem().shouldBe(listOf(fakeBackup))
          }
        }
      }
    }
  }
})
