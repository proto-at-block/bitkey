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
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.AccountData
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
  val importEmergencyExitKitCalls = turbines.create<Unit>("import Emergency Exit Kit calls")
  val onStartCloudRecoveryCalls = turbines.create<CloudBackup>("start cloud recovery calls")
  val onStartLiteAccountRecoveryCalls = turbines.create<CloudBackup>("start lite account recovery calls")
  val onStartLostAppRecoveryCalls = turbines.create<Unit>("start lost app recovery calls")
  val onStartLiteAccountCreationCalls = turbines.create<Unit>("start lite account creation calls")

  val props = AccessCloudBackupUiProps(
    onExit = {
      exitCalls += Unit
    },
    onImportEmergencyExitKit = {
      importEmergencyExitKitCalls += Unit
    },
    startIntent = AccountData.StartIntent.BeTrustedContact,
    inviteCode = "inviteCode",
    onStartCloudRecovery = { _, backup ->
      onStartCloudRecoveryCalls += backup
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

      onStartCloudRecoveryCalls.awaitItem().shouldBe(fakeBackup)
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
})
