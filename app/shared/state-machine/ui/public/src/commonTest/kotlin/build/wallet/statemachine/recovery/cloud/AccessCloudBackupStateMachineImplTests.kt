package build.wallet.statemachine.recovery.cloud

import app.cash.turbine.plusAssign
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupError.UnrectifiableCloudBackupError
import build.wallet.cloud.backup.CloudBackupRepositoryFake
import build.wallet.cloud.backup.CloudBackupV2WithFullAccountMock
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.cloud.CloudSignInFailedScreenModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

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

    stateMachine.testWithVirtualTime(props) {
      awaitBodyMock<CloudSignInUiProps> {
        onSignedIn(fakeCloudAccount)
      }

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      backupFoundCalls.awaitItem().shouldBe(fakeBackup)
    }
  }

  test("cloud account signed in but cloud backup not found") {
    stateMachine.testWithVirtualTime(props) {
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

    stateMachine.testWithVirtualTime(props) {
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
    stateMachine.testWithVirtualTime(props) {
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
    stateMachine.testWithVirtualTime(props) {
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
    stateMachine.testWithVirtualTime(props.copy(showErrorOnBackupMissing = false)) {
      awaitBodyMock<CloudSignInUiProps> {
        onSignInFailure(Error())
      }

      awaitBody<CloudSignInFailedScreenModel>()
    }
  }

  test("cloud account signed in but cloud backup not found from trusted contact flow - proceeds as if found") {
    stateMachine.testWithVirtualTime(props.copy(showErrorOnBackupMissing = false)) {
      awaitBodyMock<CloudSignInUiProps> {
        onSignedIn(fakeCloudAccount)
      }

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      cannotAccessCloudCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("cloud account sign in failed - start emergency access recovery") {
    stateMachine.testWithVirtualTime(props) {
      awaitBodyMock<CloudSignInUiProps> {
        onSignInFailure(Error())
      }

      awaitBody<CloudWarningBodyModel> {
        onImportEmergencyAccessKit.shouldNotBeNull().invoke()
      }

      importEmergencyAccessKitCalls.awaitItem().shouldBe(Unit)
    }
  }
})
