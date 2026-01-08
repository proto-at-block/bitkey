package build.wallet.statemachine.recovery.cloud

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_BACKUP_NOT_FOUND_TROUBLESHOOTING
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.cloud.backup.AllFullAccountBackupMocks
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupRepositoryFake
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.data.keybox.StartIntent
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class AccessCloudBackupStateMachineImplTestsIOS : FunSpec({

  val accountId = FullAccountIdMock
  val fakeCloudAccount = CloudAccountMock(instanceId = "1")
  val fakeBackup = AllFullAccountBackupMocks[0] as CloudBackup
  val cloudBackupRepository = CloudBackupRepositoryFake()
  val selectCloudBackupUiStateMachine = object : SelectCloudBackupUiStateMachine,
    ScreenStateMachineMock<SelectCloudBackupUiProps>("select-cloud-backup") {}
  val stateMachine =
    AccessCloudBackupUiStateMachineImpl(
      cloudSignInUiStateMachine = CloudSignInUiStateMachineMock(),
      cloudBackupRepository = cloudBackupRepository,
      rectifiableErrorHandlingUiStateMachine = RectifiableErrorHandlingUiStateMachineMock(),
      deviceInfoProvider = DeviceInfoProviderMock(),
      inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create),
      selectCloudBackupUiStateMachine = selectCloudBackupUiStateMachine
    )

  val exitCalls = turbines.create<Unit>("exit calls")
  val importEmergencyExitKitCalls = turbines.create<Unit>("import Emergency Exit Kit calls")
  val onStartCloudRecoveryCalls = turbines.create<List<CloudBackup>>("start cloud recovery calls")
  val onStartLiteAccountRecoveryCalls = turbines.create<CloudBackup>("start lite account recovery calls")
  val onStartLostAppRecoveryCalls = turbines.create<Unit>("start lost app recovery calls")
  val onStartLiteAccountCreationCalls = turbines.create<Unit>("start lite account creation calls")

  val props =
    AccessCloudBackupUiProps(
      onExit = {
        exitCalls += Unit
      },
      onImportEmergencyExitKit = {
        importEmergencyExitKitCalls += Unit
      },
      startIntent = StartIntent.BeTrustedContact,
      inviteCode = "inviteCode",
      onStartCloudRecovery = { _, backups ->
        onStartCloudRecoveryCalls += backups
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

  test("cloud account signed in but cloud backup not found - check cloud again and fail") {
    stateMachine.testWithVirtualTime(props) {
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

      awaitBody<FormBodyModel>(id = CLOUD_BACKUP_NOT_FOUND_TROUBLESHOOTING) {
        primaryButton.shouldNotBeNull().onClick.invoke()
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

  test("cloud account signed in but cloud backup not found - check cloud again and find backup") {
    stateMachine.testWithVirtualTime(props) {
      awaitBodyMock<CloudSignInUiProps> {
        onSignedIn(fakeCloudAccount)
      }

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<FormBodyModel> {
        cloudBackupRepository.writeBackup(accountId, fakeCloudAccount, fakeBackup, requireAuthRefresh = true)
        mainContentList
          .first()
          .shouldNotBeNull()
          .shouldBeTypeOf<FormMainContentModel.ListGroup>()
          .listGroupModel
          .items[0]
          .onClick.shouldNotBeNull().invoke()
      }

      awaitBody<FormBodyModel>(id = CLOUD_BACKUP_NOT_FOUND_TROUBLESHOOTING) {
        primaryButton.shouldNotBeNull().onClick.invoke()
      }

      awaitBodyMock<CloudSignInUiProps> {
        onSignedIn(fakeCloudAccount)
      }

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      onStartCloudRecoveryCalls.awaitItem().shouldBe(listOf(fakeBackup))
    }
  }
})
