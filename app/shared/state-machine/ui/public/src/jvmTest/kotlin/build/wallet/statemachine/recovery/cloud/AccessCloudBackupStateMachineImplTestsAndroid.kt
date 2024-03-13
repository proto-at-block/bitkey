package build.wallet.statemachine.recovery.cloud

import app.cash.turbine.plusAssign
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupRepositoryFake
import build.wallet.cloud.backup.CloudBackupV2WithFullAccountMock
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.emergencyaccesskit.EakDataFake
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
  val importEmergencyAccessKitCalls = turbines.create<Unit>("import emergency access kit calls")

  val props =
    AccessCloudBackupUiProps(
      eakAssociation = EakDataFake,
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

  test("android - cloud account signed in but cloud backup not found - check cloud again and fail") {
    stateMachine.test(props) {
      awaitScreenWithBodyModelMock<CloudSignInUiProps> {
        onSignedIn(fakeCloudAccount)
      }

      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitScreenWithBody<FormBodyModel> {
        mainContentList
          .first()
          .shouldNotBeNull()
          .shouldBeTypeOf<FormMainContentModel.ListGroup>()
          .listGroupModel
          .items[0]
          .onClick.shouldNotBeNull().invoke()
      }

      awaitScreenWithBodyModelMock<CloudSignInUiProps> {
        onSignedIn(fakeCloudAccount)
      }

      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitScreenWithBody<FormBodyModel>()
    }
  }

  test("android - cloud account signed in but cloud backup not found - check cloud again and find backup") {
    stateMachine.test(props) {
      awaitScreenWithBodyModelMock<CloudSignInUiProps> {
        onSignedIn(fakeCloudAccount)
      }

      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitScreenWithBody<FormBodyModel> {
        cloudBackupRepository.writeBackup(accountId, fakeCloudAccount, fakeBackup, requireAuthRefresh = true)
        mainContentList
          .first()
          .shouldNotBeNull()
          .shouldBeTypeOf<FormMainContentModel.ListGroup>()
          .listGroupModel
          .items[0]
          .onClick.shouldNotBeNull().invoke()
      }

      awaitScreenWithBodyModelMock<CloudSignInUiProps> {
        onSignedIn(fakeCloudAccount)
      }

      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      backupFoundCalls.awaitItem().shouldBe(fakeBackup)
    }
  }
})
