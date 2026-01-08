package build.wallet.statemachine.account.create.full

import bitkey.onboarding.DeleteFullAccountServiceMock
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.cloud.backup.AllLiteAccountBackupMocks
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.CloudBackupV3
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.onboarding.LiteAccountBackupToFullAccountUpgrader
import build.wallet.onboarding.LiteAccountBackupToFullAccountUpgraderMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class ReplaceWithLiteAccountRestoreUiStateMachineImplTests : FunSpec({

  context("parameterized tests for all backup versions") {
    AllLiteAccountBackupMocks.forEach { backup ->
      val backupVersion = when (backup) {
        is CloudBackupV2 -> "v2"
        is CloudBackupV3 -> "v3"
        else -> "unknown"
      }

      context("backup $backupVersion") {
        val deleteFullAccountService =
          DeleteFullAccountServiceMock { name -> turbines.create("$backupVersion-$name") }
        val liteAccountBackupToFullAccountUpgrader =
          LiteAccountBackupToFullAccountUpgraderMock {
              name ->
            turbines.create("$backupVersion-$name")
          }
        val stateMachine =
          ReplaceWithLiteAccountRestoreUiStateMachineImpl(
            proofOfPossessionNfcStateMachine =
              object : ProofOfPossessionNfcStateMachine,
                ScreenStateMachineMock<ProofOfPossessionNfcProps>(
                  id = "pop-nfc"
                ) {},
            deleteFullAccountService = deleteFullAccountService,
            liteAccountBackupToFullAccountUpgrader = liteAccountBackupToFullAccountUpgrader
          )

        val onAccountUpgradedCalls =
          turbines.create<FullAccount>("$backupVersion-onAccountUpgraded calls")

        val fullAccountIdToReplace = FullAccountId("full-account-id-to-replace")
        val keybox = KeyboxMock.copy(fullAccountId = fullAccountIdToReplace)

        val props = ReplaceWithLiteAccountRestoreUiProps(
          keyboxToReplace = keybox,
          liteAccountCloudBackup = backup as CloudBackup,
          onAccountUpgraded = onAccountUpgradedCalls::add,
          onBack = {}
        )

        test("happy path") {
          stateMachine.test(props) {
            awaitBodyMock<ProofOfPossessionNfcProps> {
              request.shouldBeTypeOf<Request.HwKeyProof>()
                .onSuccess(HwFactorProofOfPossession("fake"))
            }
            awaitBody<LoadingSuccessBodyModel>(
              CloudEventTrackerScreenId.LOADING_RESTORING_FROM_LITE_ACCOUNT_CLOUD_BACKUP_DURING_FULL_ACCOUNT_ONBOARDING
            )

            deleteFullAccountService.deleteAccountCalls
              .awaitItem()
              .shouldBe(fullAccountIdToReplace)
            liteAccountBackupToFullAccountUpgrader.upgradeAccountCalls
              .awaitItem()
              .shouldBe(backup as CloudBackup to keybox)
            onAccountUpgradedCalls.awaitItem().shouldBe(FullAccountMock)
          }
        }

        test("upgrade failure and retry") {
          stateMachine.test(props) {
            liteAccountBackupToFullAccountUpgrader.result =
              Err(LiteAccountBackupToFullAccountUpgrader.UpgradeError("boom"))
            awaitBodyMock<ProofOfPossessionNfcProps> {
              request.shouldBeTypeOf<Request.HwKeyProof>()
                .onSuccess(HwFactorProofOfPossession("fake"))
            }
            awaitBody<LoadingSuccessBodyModel>(
              CloudEventTrackerScreenId.LOADING_RESTORING_FROM_LITE_ACCOUNT_CLOUD_BACKUP_DURING_FULL_ACCOUNT_ONBOARDING
            ) {
              state.shouldBe(LoadingSuccessBodyModel.State.Loading)
            }

            deleteFullAccountService.deleteAccountCalls
              .awaitItem()
              .shouldBe(fullAccountIdToReplace)
            liteAccountBackupToFullAccountUpgrader.upgradeAccountCalls
              .awaitItem()
              .shouldBe(backup as CloudBackup to keybox)

            // Retry
            awaitBody<FormBodyModel>(
              CloudEventTrackerScreenId.FAILURE_RESTORE_FROM_LITE_ACCOUNT_CLOUD_BACKUP_AFTER_ONBOARDING
            ) {
              liteAccountBackupToFullAccountUpgrader.reset()
              primaryButton!!.onClick()
            }

            awaitBodyMock<ProofOfPossessionNfcProps> {
              request.shouldBeTypeOf<Request.HwKeyProof>()
                .onSuccess(HwFactorProofOfPossession("fake"))
            }
            awaitBody<LoadingSuccessBodyModel>(
              CloudEventTrackerScreenId.LOADING_RESTORING_FROM_LITE_ACCOUNT_CLOUD_BACKUP_DURING_FULL_ACCOUNT_ONBOARDING
            ) {
              state.shouldBe(LoadingSuccessBodyModel.State.Loading)
            }
            deleteFullAccountService.deleteAccountCalls
              .awaitItem()
              .shouldBe(fullAccountIdToReplace)
            liteAccountBackupToFullAccountUpgrader.upgradeAccountCalls
              .awaitItem()
              .shouldBe(backup as CloudBackup to keybox)
            onAccountUpgradedCalls.awaitItem().shouldBe(FullAccountMock)
          }
        }
      }
    }
  }
})
