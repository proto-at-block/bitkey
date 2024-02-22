package build.wallet.statemachine.recovery.cloud

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.FAILURE_RESTORE_FROM_CLOUD_BACKUP
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.LOADING_RESTORING_FROM_CLOUD_BACKUP
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.cloud.backup.CloudBackupV2WithLiteAccountMock
import build.wallet.cloud.backup.LiteAccountCloudBackupRestorerFake
import build.wallet.cloud.backup.RestoreFromBackupError.AccountBackupRestorationError
import build.wallet.coroutines.turbine.turbines
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class LiteAccountCloudBackupRestorationUiStateMachineImplTests : FunSpec({

  val liteAccountCloudBackupRestorer = LiteAccountCloudBackupRestorerFake(turbines::create)
  val onExitCalls = turbines.create<Unit>("on exit calls")
  val onLiteAccountRestoredCalls = turbines.create<LiteAccount>("on lite account restored calls")
  val props =
    LiteAccountCloudBackupRestorationUiProps(
      cloudBackup = CloudBackupV2WithLiteAccountMock,
      onLiteAccountRestored = { onLiteAccountRestoredCalls += it },
      onExit = { onExitCalls += Unit }
    )

  val stateMachine =
    LiteAccountCloudBackupRestorationUiStateMachineImpl(
      liteAccountCloudBackupRestorer = liteAccountCloudBackupRestorer
    )

  beforeTest {
    liteAccountCloudBackupRestorer.reset()
  }

  test("success") {
    stateMachine.test(props = props) {
      awaitScreenWithBody<LoadingBodyModel>(LOADING_RESTORING_FROM_CLOUD_BACKUP)
      liteAccountCloudBackupRestorer
        .restoreFromBackupCalls
        .awaitItem()
        .shouldBe(CloudBackupV2WithLiteAccountMock)
      onLiteAccountRestoredCalls.awaitItem().shouldBeEqual(LiteAccountMock)
    }
  }

  test("failure") {
    val throwable = Throwable("foo")
    liteAccountCloudBackupRestorer.returnError = Err(AccountBackupRestorationError(throwable))
    stateMachine.test(props = props) {
      awaitScreenWithBody<LoadingBodyModel>(LOADING_RESTORING_FROM_CLOUD_BACKUP)
      liteAccountCloudBackupRestorer
        .restoreFromBackupCalls
        .awaitItem()
        .shouldBe(CloudBackupV2WithLiteAccountMock)
      awaitScreenWithBody<FormBodyModel>(FAILURE_RESTORE_FROM_CLOUD_BACKUP) {
        primaryButton.shouldNotBeNull().onClick()
      }
      onExitCalls.awaitItem()
    }
  }
})
