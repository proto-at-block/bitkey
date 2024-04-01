package build.wallet.statemachine.account.recovery.cloud

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_FAILURE_NEW_ACCOUNT
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_LOADING
import build.wallet.analytics.v1.Action.ACTION_APP_CLOUD_BACKUP_INITIALIZE
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.cloud.backup.CloudBackupError.RectifiableCloudBackupError
import build.wallet.cloud.backup.CloudBackupError.UnrectifiableCloudBackupError
import build.wallet.cloud.backup.CloudBackupRepositoryFake
import build.wallet.cloud.backup.LiteAccountCloudBackupCreator.LiteAccountCloudBackupCreatorError.SocRecKeysRetrievalError
import build.wallet.cloud.backup.LiteAccountCloudBackupCreatorMock
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.cloud.LiteAccountCloudSignInAndBackupProps
import build.wallet.statemachine.cloud.LiteAccountCloudSignInAndBackupUiStateMachineImpl
import build.wallet.statemachine.cloud.RectifiableErrorHandlingProps
import build.wallet.statemachine.cloud.RectifiableErrorMessages.Companion.RectifiableErrorCreateLiteMessages
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.recovery.cloud.CloudSignInUiProps
import build.wallet.statemachine.recovery.cloud.CloudSignInUiStateMachineMock
import build.wallet.statemachine.recovery.cloud.RectifiableErrorHandlingUiStateMachineMock
import build.wallet.statemachine.ui.clickPrimaryButton
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe

class LiteAccountCloudSignInAndBackupUiStateMachineImplTests : FunSpec({

  val eventTracker = EventTrackerMock(turbines::create)
  val liteAccountCloudBackupCreator = LiteAccountCloudBackupCreatorMock()
  val cloudBackupRepository = CloudBackupRepositoryFake()
  val stateMachine =
    LiteAccountCloudSignInAndBackupUiStateMachineImpl(
      cloudBackupRepository = cloudBackupRepository,
      cloudSignInUiStateMachine = CloudSignInUiStateMachineMock(),
      liteAccountCloudBackupCreator = liteAccountCloudBackupCreator,
      deviceInfoProvider = DeviceInfoProviderMock(),
      eventTracker = eventTracker,
      rectifiableErrorHandlingUiStateMachine = RectifiableErrorHandlingUiStateMachineMock(),
      inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)
    )

  val onBackupFailedCalls = turbines.create<Unit>("onBackupFailed calls")
  val onBackupSavedCalls = turbines.create<Unit>("onBackupSaved calls")
  val props =
    LiteAccountCloudSignInAndBackupProps(
      liteAccount = LiteAccountMock,
      onBackupFailed = {
        onBackupFailedCalls += Unit
      },
      onBackupSaved = {
        onBackupSavedCalls += Unit
      },
      presentationStyle = Root
    )

  afterTest {
    liteAccountCloudBackupCreator.reset()
  }

  test("Uploads backup") {
    stateMachine.test(props = props) {
      liteAccountCloudBackupCreator.createResultCreator = ::Ok
      awaitScreenWithBodyModelMock<CloudSignInUiProps> {
        forceSignOut.shouldBeFalse()
        onSignedIn(CloudAccountMock("foo"))
      }
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_CLOUD_BACKUP_INITIALIZE)
      )
      awaitScreenWithBody<LoadingSuccessBodyModel>(SAVE_CLOUD_BACKUP_LOADING)
      onBackupSavedCalls.awaitItem()
    }
  }

  test("Encounters unrectifiable error") {
    stateMachine.test(props = props) {
      liteAccountCloudBackupCreator.createResultCreator = ::Ok
      cloudBackupRepository.returnWriteError = UnrectifiableCloudBackupError(Throwable("bar"))
      awaitScreenWithBodyModelMock<CloudSignInUiProps> {
        forceSignOut.shouldBeFalse()
        onSignedIn(CloudAccountMock("foo"))
      }
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_CLOUD_BACKUP_INITIALIZE)
      )
      awaitScreenWithBody<LoadingSuccessBodyModel>(SAVE_CLOUD_BACKUP_LOADING)
      awaitScreenWithBody<FormBodyModel>(SAVE_CLOUD_BACKUP_FAILURE_NEW_ACCOUNT) {
        clickPrimaryButton()
      }
      onBackupFailedCalls.awaitItem()
    }
  }

  test("Handles rectifiable error") {
    stateMachine.test(props = props) {
      liteAccountCloudBackupCreator.createResultCreator = ::Ok
      cloudBackupRepository.returnWriteError = RectifiableCloudBackupError(Throwable("bar"), "bar")
      awaitScreenWithBodyModelMock<CloudSignInUiProps> {
        forceSignOut.shouldBeFalse()
        onSignedIn(CloudAccountMock("foo"))
      }
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_CLOUD_BACKUP_INITIALIZE)
      )
      awaitScreenWithBody<LoadingSuccessBodyModel>(SAVE_CLOUD_BACKUP_LOADING) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitScreenWithBodyModelMock<RectifiableErrorHandlingProps> {
        messages.shouldBeEqual(RectifiableErrorCreateLiteMessages)
        // Unset this error, so we don't loop back.
        cloudBackupRepository.returnWriteError = null
        onReturn()
      }
      awaitScreenWithBody<LoadingSuccessBodyModel>(SAVE_CLOUD_BACKUP_LOADING) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      onBackupSavedCalls.awaitItem()
    }
  }

  test("Cannot handle rectifiable error") {
    stateMachine.test(props = props) {
      liteAccountCloudBackupCreator.createResultCreator = ::Ok
      cloudBackupRepository.returnWriteError = RectifiableCloudBackupError(Throwable("bar"), "bar")
      awaitScreenWithBodyModelMock<CloudSignInUiProps> {
        forceSignOut.shouldBeFalse()
        onSignedIn(CloudAccountMock("foo"))
      }
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_CLOUD_BACKUP_INITIALIZE)
      )
      awaitScreenWithBody<LoadingSuccessBodyModel>(SAVE_CLOUD_BACKUP_LOADING) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitScreenWithBodyModelMock<RectifiableErrorHandlingProps> {
        messages.shouldBeEqual(RectifiableErrorCreateLiteMessages)
        onFailure(null)
      }
      onBackupFailedCalls.awaitItem()
    }
  }

  test("Cannot create backup") {
    stateMachine.test(props = props) {
      liteAccountCloudBackupCreator.createResultCreator = {
        Err(SocRecKeysRetrievalError(Throwable("bar")))
      }
      awaitScreenWithBodyModelMock<CloudSignInUiProps> {
        forceSignOut.shouldBeFalse()
        onSignedIn(CloudAccountMock("foo"))
      }
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_CLOUD_BACKUP_INITIALIZE)
      )
      awaitScreenWithBody<LoadingSuccessBodyModel>(SAVE_CLOUD_BACKUP_LOADING) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitScreenWithBody<FormBodyModel>(SAVE_CLOUD_BACKUP_FAILURE_NEW_ACCOUNT) {
        clickPrimaryButton()
      }
      onBackupFailedCalls.awaitItem()
    }
  }
})
