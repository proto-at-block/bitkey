package build.wallet.statemachine.cloud

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.cloud.backup.CloudBackupError
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.coroutines.turbine.turbines
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.clickPrimaryButton
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Exercises [RectifiableErrorHandlingUiStateMachineImpl] with the real iOS
 * [CloudBackupRectificationNavigatorImpl]. On iOS, tapping "Try again" must retry the backup via
 * `onReturn` rather than crashing (BKW-573).
 */
class CloudBackupRectificationNavigatorImplTestsIOS : FunSpec({

  val stateMachine =
    RectifiableErrorHandlingUiStateMachineImpl(
      CloudBackupRectificationNavigatorImpl()
    )
  val cloudStoreAccount = object : CloudStoreAccount {}
  val failureCalls = turbines.create<Unit>("failure calls")
  val returnCalls = turbines.create<Unit>("return calls")
  val props =
    RectifiableErrorHandlingProps(
      messages =
        RectifiableErrorMessages(
          title = "foo",
          subline = "bar"
        ),
      rectifiableError =
        CloudBackupError.RectifiableCloudBackupError(
          cause = Throwable("foo"),
          // On iOS the rectification data is the underlying NSError, represented here as an
          // arbitrary non-null value.
          data = "nserror"
        ),
      cloudStoreAccount = cloudStoreAccount,
      onFailure = {
        failureCalls += Unit
      },
      onReturn = {
        returnCalls += Unit
      },
      screenId = CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_FAILURE_NEW_ACCOUNT_RECTIFIABLE,
      presentationStyle = ScreenPresentationStyle.Root,
      errorData = ErrorData(
        segment = RecoverySegment.CloudBackup.FullAccount.Upload,
        actionDescription = "Testing CloudKit rectification retry",
        cause = Throwable("foo")
      )
    )

  test("Pressing Try Again retries via onReturn instead of crashing") {
    stateMachine.test(props = props) {
      awaitBody<FormBodyModel>(props.screenId) {
        clickPrimaryButton()
      }

      awaitBody<LoadingSuccessBodyModel>(CloudEventTrackerScreenId.RECTIFYING_CLOUD_ERROR) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      returnCalls.awaitItem()
    }
  }
})
