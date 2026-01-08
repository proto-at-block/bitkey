package build.wallet.statemachine.recovery.cloud

import app.cash.turbine.plusAssign
import build.wallet.cloud.backup.AllFullAccountBackupMocks
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupRepositoryFake
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.CloudBackupV3
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.cloud.store.CloudStoreAccountError
import build.wallet.cloud.store.CloudStoreAccountRepositoryMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitUntilBody
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ExistingFullAccountUiStateMachineImplTests : FunSpec({

  context("parameterized tests for all backup versions") {
    AllFullAccountBackupMocks.forEach { backup ->
      val backupVersion = when (backup) {
        is CloudBackupV2 -> "v2"
        is CloudBackupV3 -> "v3"
        else -> "unknown"
      }

      context("backup $backupVersion") {
        val cloudBackupRepository = CloudBackupRepositoryFake()
        val cloudStoreAccountRepository = CloudStoreAccountRepositoryMock()
        val onBackCalls = turbines.create<Unit>("$backupVersion-on back calls")
        val onRestoreCalls = turbines.create<Unit>("$backupVersion-on restore calls")
        val onBackupArchiveCalls = turbines.create<Unit>("$backupVersion-on backup archive calls")

        val props = ExistingFullAccountUiProps(
          cloudBackup = backup as CloudBackup,
          devicePlatform = DevicePlatform.Android,
          onBack = { onBackCalls += Unit },
          onRestore = { onRestoreCalls += Unit },
          onBackupArchive = { onBackupArchiveCalls += Unit }
        )

        val stateMachine =
          ExistingFullAccountUiStateMachineImpl(
            cloudStoreAccountRepository = cloudStoreAccountRepository,
            cloudBackupRepository = cloudBackupRepository
          )

        beforeTest {
          cloudBackupRepository.reset()
          cloudStoreAccountRepository.reset()
        }

        test("initial screen allows restore and back") {
          stateMachine.test(props) {
            awaitBody<ExistingFullAccountFoundBodyModel> {
              onRestore()
            }
            onRestoreCalls.awaitItem().shouldBe(Unit)
          }
        }

        test("warning and back") {
          stateMachine.test(props) {
            awaitBody<ExistingFullAccountFoundBodyModel> {
              onDeleteBackupAndCreateNew()
            }

            awaitBody<WarningAboutDeletingBackupBodyModel> {
              onBack()
            }

            awaitBody<ExistingFullAccountFoundBodyModel>()
          }
        }

        test("confirm toggles and archive success") {
          val cloudAccount = CloudAccountMock(instanceId = "acct-1")
          cloudStoreAccountRepository.currentAccountResult = Ok(cloudAccount)

          stateMachine.test(props) {
            awaitBody<ExistingFullAccountFoundBodyModel> {
              onDeleteBackupAndCreateNew()
            }

            awaitBody<WarningAboutDeletingBackupBodyModel> {
              onContinue()
            }

            awaitBody<ConfirmingDeleteBackupBodyModel> {
              firstOptionIsConfirmed.shouldBe(false)
              secondOptionIsConfirmed.shouldBe(false)
              onClickFirstOption()
            }

            awaitBody<ConfirmingDeleteBackupBodyModel> {
              firstOptionIsConfirmed.shouldBe(true)
              secondOptionIsConfirmed.shouldBe(false)
              onClickSecondOption()
            }

            awaitBody<ConfirmingDeleteBackupBodyModel> {
              firstOptionIsConfirmed.shouldBe(true)
              secondOptionIsConfirmed.shouldBe(true)
              onConfirmDelete()
            }

            awaitUntilBody<LoadingSuccessBodyModel> {
              state.shouldBe(LoadingSuccessBodyModel.State.Loading)
            }

            onBackupArchiveCalls.awaitItem().shouldBe(Unit)
          }
        }

        test("archiving error returns to first screen when no cloud account") {
          cloudStoreAccountRepository.currentAccountResult = Err(CloudStoreAccountError())

          stateMachine.test(props) {
            awaitBody<ExistingFullAccountFoundBodyModel> {
              onDeleteBackupAndCreateNew()
            }

            awaitBody<WarningAboutDeletingBackupBodyModel> {
              onContinue()
            }

            awaitBody<ConfirmingDeleteBackupBodyModel> {
              onClickFirstOption()
              onClickSecondOption()
              onConfirmDelete()
            }

            // When there's no cloud account, the error happens immediately, and we transition back
            // to the first screen. The intermediate loading state may not be observed due to the
            // fast state transition, so we skip checking it.
            awaitUntilBody<ExistingFullAccountFoundBodyModel>()

            onBackupArchiveCalls.expectNoEvents()
          }
        }
      }
    }
  }
})
