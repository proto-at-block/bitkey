package build.wallet.statemachine.recovery.cloud

import app.cash.turbine.plusAssign
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupRepositoryFake
import build.wallet.cloud.backup.CloudBackupV2WithFullAccountMock
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class AccessCloudBackupStateMachineImplTestsAndroid : FunSpec({

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
  val importEmergencyExitKitCalls = turbines.create<Unit>("import Emergency Exit Kit calls")

  val props = AccessCloudBackupUiProps(
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
    onImportEmergencyExitKit = {
      importEmergencyExitKitCalls += Unit
    }
  )

  afterTest {
    cloudBackupRepository.reset()
  }

  test("android - cloud account signed in but cloud backup not found - check cloud again and fail") {
    stateMachine.test(props) {
      awaitBodyMock<CloudSignInUiProps> {
        onSignedIn(fakeCloudAccount)
      }

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<FormBodyModel> {
        mainContentList
          .first()
          .shouldNotBeNull()
          .shouldBeTypeOf<FormMainContentModel.ListGroup>()
          .listGroupModel
          .items[0]
          .onClick.shouldNotBeNull().invoke()
      }

      awaitBodyMock<CloudSignInUiProps> {
        onSignedIn(fakeCloudAccount)
      }

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<FormBodyModel>()
    }
  }

  test("android - cloud account signed in but cloud backup not found - check cloud again and find backup") {
    stateMachine.test(props) {
      awaitBodyMock<CloudSignInUiProps> {
        onSignedIn(fakeCloudAccount)
      }

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<CloudWarningBodyModel> {
        cloudBackupRepository.writeBackup(accountId, fakeCloudAccount, fakeBackup, requireAuthRefresh = true)
        onCheckCloudAgain()
      }

      awaitBodyMock<CloudSignInUiProps> {
        onSignedIn(fakeCloudAccount)
      }

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      backupFoundCalls.awaitItem().shouldBe(fakeBackup)
    }
  }
})
