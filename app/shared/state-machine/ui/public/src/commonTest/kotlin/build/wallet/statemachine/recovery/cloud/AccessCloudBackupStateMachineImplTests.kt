package build.wallet.statemachine.recovery.cloud

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_NOT_SIGNED_IN
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupError.UnrectifiableCloudBackupError
import build.wallet.cloud.backup.CloudBackupRepositoryFake
import build.wallet.cloud.backup.CloudBackupV2WithFullAccountMock
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class AccessCloudBackupStateMachineImplTests : FunSpec({

  val accountId = FullAccountIdMock
  val fakeCloudAccount = CloudAccountMock(instanceId = "1")
  val fakeBackup = CloudBackupV2WithFullAccountMock
  val cloudBackupRepository = CloudBackupRepositoryFake()
  val stateMachine =
    AccessCloudBackupUiStateMachineImpl(
      cloudSignInUiStateMachine = CloudSignInUiStateMachineMock(),
      cloudBackupRepository = cloudBackupRepository,
      rectifiableErrorHandlingUiStateMachine = RectifiableErrorHandlingUiStateMachineMock(),
      deviceInfoProvider = DeviceInfoProviderMock(),
      inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)
    )

  val exitCalls = turbines.create<Unit>("exit calls")
  val backupFoundCalls = turbines.create<CloudBackup>("backup found calls")
  val cannotAccessCloudCalls = turbines.create<Unit>("cannot access cloud calls")
  val importEmergencyAccessKitCalls = turbines.create<Unit>("import emergency access kit calls")

  val props =
    AccessCloudBackupUiProps(
      forceSignOutFromCloud = false,
      onExit = {
        exitCalls += Unit
      },
      onBackupFound = { backup ->
        backupFoundCalls += backup
      },
      onCannotAccessCloudBackup = {
        cannotAccessCloudCalls += Unit
      },
      onImportEmergencyAccessKit = {
        importEmergencyAccessKitCalls += Unit
      }
    )

  afterTest {
    cloudBackupRepository.reset()
  }

  test("successfully find backup and restore it") {
    cloudBackupRepository.writeBackup(accountId, fakeCloudAccount, fakeBackup, true)

    stateMachine.test(props) {
      awaitScreenWithBodyModelMock<CloudSignInUiProps> {
        onSignedIn(fakeCloudAccount)
      }

      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      backupFoundCalls.awaitItem().shouldBe(fakeBackup)
    }
  }

  test("cloud account signed in but cloud backup not found") {
    stateMachine.test(props) {
      awaitScreenWithBodyModelMock<CloudSignInUiProps> {
        onSignedIn(fakeCloudAccount)
      }

      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitScreenWithBody<FormBodyModel>()
    }
  }

  test("cloud account signed in but failure when trying to access cloud backup") {
    cloudBackupRepository.returnReadError = UnrectifiableCloudBackupError(Exception("oops"))

    stateMachine.test(props) {
      awaitScreenWithBodyModelMock<CloudSignInUiProps> {
        onSignedIn(fakeCloudAccount)
      }

      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitScreenWithBody<FormBodyModel>()
    }
  }

  test("cloud account signed in but cloud backup not found - exit") {
    stateMachine.test(props) {
      awaitScreenWithBodyModelMock<CloudSignInUiProps> {
        onSignedIn(fakeCloudAccount)
      }

      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitScreenWithBody<FormBodyModel> {
        onBack?.invoke()
      }

      exitCalls.awaitItem().shouldBe(Unit)
    }
  }

  // See AccessCloudBackupStateMachineImplTests{Android,IOS} for the "check again" tests.
  // The behavior on the two platforms is slightly different.

  test("cloud account signed in but cloud backup not found - cannot access cloud option") {
    stateMachine.test(props) {
      awaitScreenWithBodyModelMock<CloudSignInUiProps> {
        onSignedIn(fakeCloudAccount)
      }

      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitScreenWithBody<FormBodyModel> {
        cloudBackupRepository.writeBackup(accountId, fakeCloudAccount, fakeBackup, true)
        mainContentList
          .first()
          .shouldNotBeNull()
          .shouldBeTypeOf<FormMainContentModel.ListGroup>()
          .listGroupModel
          .items[1]
          .onClick.shouldNotBeNull().invoke()
      }

      cannotAccessCloudCalls.awaitItem().shouldBe(Unit)
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
      awaitScreenWithBodyModelMock<CloudSignInUiProps> {
        onSignInFailure(Error())
      }

      awaitScreenWithBody<FormBodyModel>(SAVE_CLOUD_BACKUP_NOT_SIGNED_IN)
    }
  }

  test("cloud account signed in but cloud backup not found from trusted contact flow - proceeds as if found") {
    stateMachine.test(props.copy(showErrorOnBackupMissing = false)) {
      awaitScreenWithBodyModelMock<CloudSignInUiProps> {
        onSignedIn(fakeCloudAccount)
      }

      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      cannotAccessCloudCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("cloud account sign in failed - start emergency access recovery") {
    stateMachine.test(props) {
      awaitScreenWithBodyModelMock<CloudSignInUiProps> {
        onSignInFailure(Error())
      }

      awaitScreenWithBody<FormBodyModel> {
        mainContentList
          .first()
          .shouldNotBeNull()
          .shouldBeTypeOf<FormMainContentModel.ListGroup>()
          .listGroupModel
          .items[2]
          .onClick.shouldNotBeNull().invoke()
      }

      importEmergencyAccessKitCalls.awaitItem().shouldBe(Unit)
    }
  }
})
