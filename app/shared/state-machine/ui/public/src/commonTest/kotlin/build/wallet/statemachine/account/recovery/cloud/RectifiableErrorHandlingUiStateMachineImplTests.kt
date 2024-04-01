package build.wallet.statemachine.account.recovery.cloud

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.cloud.backup.CloudBackupError
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.coroutines.turbine.turbines
import build.wallet.statemachine.cloud.RectifiableErrorHandlingProps
import build.wallet.statemachine.cloud.RectifiableErrorHandlingUiStateMachineImpl
import build.wallet.statemachine.cloud.RectifiableErrorMessages
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.clickPrimaryButton
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class RectifiableErrorHandlingUiStateMachineImplTests : FunSpec({

  val stateMachine =
    RectifiableErrorHandlingUiStateMachineImpl(
      CloudBackupRectificationNavigatorMock()
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
          data = "data"
        ),
      cloudStoreAccount = cloudStoreAccount,
      onFailure = {
        failureCalls += Unit
      },
      onReturn = {
        returnCalls += Unit
      },
      screenId = CloudEventTrackerScreenId.ACCESS_CLOUD_BACKUP_FAILURE_RECTIFIABLE,
      presentationStyle = ScreenPresentationStyle.Root,
      errorData = null
    )

  test("Pressing Back calls onFailure") {
    stateMachine.test(props = props) {
      awaitScreenWithBody<FormBodyModel>(props.screenId) {
        onBack.shouldNotBeNull().invoke()
        failureCalls.awaitItem()
      }
    }
  }

  test("Pressing Cancel calls onFailure") {
    stateMachine.test(props = props) {
      awaitScreenWithBody<FormBodyModel>(props.screenId) {
        secondaryButton.shouldNotBeNull().onClick.invoke()
        failureCalls.awaitItem()
      }
    }
  }

  test("Has expected messaging") {
    stateMachine.test(props = props) {
      awaitScreenWithBody<FormBodyModel>(props.screenId) {
        header.shouldNotBeNull().let { head ->
          head.headline.shouldNotBeNull().shouldBeEqual(props.messages.title)
          head.sublineModel.shouldNotBeNull().string.shouldBeEqual(props.messages.subline)
        }
      }
    }
  }

  test("Pressing Try Again shows loading screen and returns") {
    stateMachine.test(props = props) {
      awaitScreenWithBody<FormBodyModel>(props.screenId) {
        clickPrimaryButton()
      }

      awaitScreenWithBody<LoadingSuccessBodyModel>(CloudEventTrackerScreenId.RECTIFYING_CLOUD_ERROR) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      returnCalls.awaitItem()
    }
  }
})
