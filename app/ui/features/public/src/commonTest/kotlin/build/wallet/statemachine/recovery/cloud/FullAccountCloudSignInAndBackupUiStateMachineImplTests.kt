package build.wallet.statemachine.recovery.cloud

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.plusAssign
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.analytics.v1.Action.ACTION_APP_CLOUD_BACKUP_INITIALIZE
import build.wallet.analytics.v1.Action.ACTION_APP_CLOUD_BACKUP_MISSING
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupError.UnrectifiableCloudBackupError
import build.wallet.cloud.backup.CloudBackupRepositoryFake
import build.wallet.cloud.backup.CloudBackupV2WithFullAccountMock
import build.wallet.cloud.backup.FullAccountCloudBackupCreator.FullAccountCloudBackupCreatorError.FullAccountFieldsCreationError
import build.wallet.cloud.backup.FullAccountCloudBackupCreatorMock
import build.wallet.cloud.backup.awaitBackup
import build.wallet.cloud.backup.csek.CsekDaoFake
import build.wallet.cloud.backup.csek.CsekGeneratorMock
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.emergencyaccesskit.EmergencyAccessKitPdfGeneratorFake
import build.wallet.emergencyaccesskit.EmergencyAccessKitRepositoryFake
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupProps
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupUiStateMachineImpl
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.matchers.shouldBeLoading
import build.wallet.statemachine.ui.matchers.shouldNotBeLoading
import build.wallet.ui.model.button.ButtonModel
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class FullAccountCloudSignInAndBackupUiStateMachineImplTests : FunSpec({
  val cloudBackupRepository = CloudBackupRepositoryFake()
  val cloudSignInStateMachine = CloudSignInUiStateMachineMock()
  val cloudBackupCreator = FullAccountCloudBackupCreatorMock(turbines::create)
  val eventTracker = EventTrackerMock(turbines::create)
  val stateMachine = FullAccountCloudSignInAndBackupUiStateMachineImpl(
    cloudBackupRepository = cloudBackupRepository,
    cloudSignInUiStateMachine = cloudSignInStateMachine,
    fullAccountCloudBackupCreator = cloudBackupCreator,
    eventTracker = eventTracker,
    rectifiableErrorHandlingUiStateMachine = RectifiableErrorHandlingUiStateMachineMock(),
    deviceInfoProvider = DeviceInfoProviderMock(),
    csekGenerator = CsekGeneratorMock(),
    nfcSessionUIStateMachine =
      object : NfcSessionUIStateMachine, ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(
        "nfc-session-mock"
      ) {},
    csekDao = CsekDaoFake(),
    inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create),
    emergencyAccessKitPdfGenerator = EmergencyAccessKitPdfGeneratorFake(),
    emergencyAccessKitRepository = EmergencyAccessKitRepositoryFake()
  )

  val onBackupSavedCalls = turbines.create<Unit>("backup saved")
  val onBackupFailedCalls = turbines.create<Unit>("backup failed")

  val cloudAccount = CloudAccountMock(instanceId = "fake-account")

  val props = FullAccountCloudSignInAndBackupProps(
    sealedCsek = SealedCsekFake,
    keybox = KeyboxMock,
    onBackupSaved = {
      onBackupSavedCalls += Unit
    },
    onBackupFailed = {
      onBackupFailedCalls += Unit
    },
    presentationStyle = ScreenPresentationStyle.Root,
    requireAuthRefreshForCloudBackup = false
  )

  suspend fun awaitStartCloudBackupEvent() {
    eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_CLOUD_BACKUP_INITIALIZE))
  }

  suspend fun awaitCloudBackupMissingEvent() {
    eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_CLOUD_BACKUP_MISSING))
  }

  suspend fun ReceiveTurbine<ScreenModel>.awaitLoadingScreens() {
    awaitBody<LoadingSuccessBodyModel>(
      CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_CHECK_FOR_EXISTING
    ) {
      state.shouldBe(LoadingSuccessBodyModel.State.Loading)
    }
    awaitBody<LoadingSuccessBodyModel>(CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_LOADING) {
      state.shouldBe(LoadingSuccessBodyModel.State.Loading)
    }
  }

  afterTest {
    cloudBackupRepository.reset()
  }

  test("show backup instructions by default") {
    stateMachine.test(props) {
      // save backup instructions
      awaitBody<FormBodyModel>()
    }
  }

  test("initiate backup - success") {
    cloudBackupCreator.backupResult = Ok(CloudBackupV2WithFullAccountMock)
    stateMachine.testWithVirtualTime(props) {
      // save backup instructions
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
      }

      awaitBodyMock<CloudSignInUiProps> {
        onSignedIn(cloudAccount)
        awaitStartCloudBackupEvent()
      }

      awaitLoadingScreens()

      cloudBackupCreator.createCalls.awaitItem()

      cloudBackupRepository.awaitBackup(
        cloudAccount
      ).shouldBe(CloudBackupV2WithFullAccountMock)

      onBackupSavedCalls.awaitItem()
    }
  }

  test("initiate backup - failed to create backup") {
    cloudBackupCreator.backupResult = Err(FullAccountFieldsCreationError())
    stateMachine.test(props) {
      // save backup instructions
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
      }

      awaitBodyMock<CloudSignInUiProps> {
        onSignedIn(cloudAccount)
        awaitStartCloudBackupEvent()
      }

      awaitLoadingScreens()

      cloudBackupCreator.createCalls.awaitItem()

      // Error screen
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
      }

      onBackupFailedCalls.awaitItem()
    }
  }

  test("initiate backup - failed to upload backup") {
    cloudBackupCreator.backupResult = Ok(CloudBackupV2WithFullAccountMock)
    cloudBackupRepository.returnWriteError = UnrectifiableCloudBackupError(Exception("foo"))
    stateMachine.test(props) {
      // save backup instructions
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
      }

      awaitBodyMock<CloudSignInUiProps> {
        onSignedIn(cloudAccount)
        awaitStartCloudBackupEvent()
      }

      awaitLoadingScreens()

      cloudBackupCreator.createCalls.awaitItem()

      // Error screen
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
      }

      onBackupFailedCalls.awaitItem()
    }
  }

  test("cloud sign in failure - go back") {
    stateMachine.test(props) {
      // save backup instructions
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
      }

      awaitBodyMock<CloudSignInUiProps> {
        onSignInFailure(Error())
        awaitCloudBackupMissingEvent()
      }

      awaitBody<FormBodyModel> { // failed to sign in
        onBack?.invoke()
      }

      awaitBody<FormBodyModel>() // save backup instructions
    }
  }

  test("cloud sign in failure - try again and fail") {
    stateMachine.test(props) {
      // save backup instructions
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
      }

      awaitBodyMock<CloudSignInUiProps> {
        onSignInFailure(Error())
        awaitCloudBackupMissingEvent()
      }

      awaitBody<FormBodyModel> { // failed to sign in
        clickPrimaryButton()
      }

      awaitBodyMock<CloudSignInUiProps> {
        onSignInFailure(Error())
        awaitCloudBackupMissingEvent()
      }

      awaitBody<FormBodyModel>() // failed to sign in
    }
  }

  test("cloud sign in failure - try again and success") {
    cloudBackupCreator.backupResult = Ok(CloudBackupV2WithFullAccountMock)
    stateMachine.testWithVirtualTime(props) {
      // save backup instructions
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
      }

      awaitBodyMock<CloudSignInUiProps> {
        onSignInFailure(Error())
        awaitCloudBackupMissingEvent()
      }

      awaitBody<FormBodyModel> { // failed to sign in
        clickPrimaryButton()
      }

      awaitBodyMock<CloudSignInUiProps> {
        onSignedIn(cloudAccount)
        awaitStartCloudBackupEvent()
      }

      awaitLoadingScreens()

      cloudBackupCreator.createCalls.awaitItem()

      cloudBackupRepository.awaitBackup(
        cloudAccount
      ).shouldBe(CloudBackupV2WithFullAccountMock)

      onBackupSavedCalls.awaitItem()
    }
  }

  test("when there is no csek available, a nfc prompt is shown and goes to cloud backup") {
    stateMachine.test(props = props.copy(sealedCsek = null)) {
      // generating csek, loading button on save backup instructions
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().shouldBeLoading()
      }

      // save backup instructions
      awaitBody<FormBodyModel> {
        primaryButton
          .shouldNotBeNull()
          .shouldNotBeLoading()
          .also { it.treatment.shouldBe(ButtonModel.Treatment.BitkeyInteraction) }
          .onClick()
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<*>>()
    }
  }

  test("find existing backup and proceed automatically without callback") {
    cloudBackupCreator.backupResult = Ok(CloudBackupV2WithFullAccountMock)
    cloudBackupRepository.writeBackup(
      FullAccountIdMock,
      cloudAccount,
      CloudBackupV2WithFullAccountMock,
      requireAuthRefresh = true
    )
    stateMachine.test(props) {
      // save backup instructions
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
      }

      awaitBodyMock<CloudSignInUiProps> {
        onSignedIn(cloudAccount)
        awaitStartCloudBackupEvent()
      }

      awaitLoadingScreens()

      cloudBackupCreator.createCalls.awaitItem()

      cloudBackupRepository.awaitBackup(
        cloudAccount
      ).shouldBe(CloudBackupV2WithFullAccountMock)

      onBackupSavedCalls.awaitItem()
    }
  }

  test("find existing backup and proceed with callback") {
    cloudBackupCreator.backupResult = Ok(CloudBackupV2WithFullAccountMock)
    cloudBackupRepository.writeBackup(
      FullAccountIdMock,
      cloudAccount,
      CloudBackupV2WithFullAccountMock,
      requireAuthRefresh = true
    )
    val onExistingAppDataFoundCalls =
      turbines.create<CloudBackup?>(
        "on existing cloud backup found"
      )

    val adjustedProps =
      props.copy(onExistingAppDataFound = { cloudBackup, proceed ->
        onExistingAppDataFoundCalls += cloudBackup
        proceed()
      })

    stateMachine.test(adjustedProps) {
      // save backup instructions
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
      }

      awaitBodyMock<CloudSignInUiProps> {
        onSignedIn(cloudAccount)
        awaitStartCloudBackupEvent()
      }

      awaitLoadingScreens()

      cloudBackupCreator.createCalls.awaitItem()
      onExistingAppDataFoundCalls.awaitItem()
        .shouldNotBeNull().shouldBeEqual(CloudBackupV2WithFullAccountMock)

      cloudBackupRepository.awaitBackup(
        cloudAccount
      ).shouldBe(CloudBackupV2WithFullAccountMock)

      onBackupSavedCalls.awaitItem()
    }
  }

  test("skip cloud backup instructions") {
    stateMachine.test(props.copy(isSkipCloudBackupInstructions = true)) {
      cloudBackupCreator.backupResult = Ok(CloudBackupV2WithFullAccountMock)
      awaitBodyMock<CloudSignInUiProps> {
        forceSignOut.shouldBeFalse()
        onSignedIn(cloudAccount)
        awaitStartCloudBackupEvent()
      }

      awaitLoadingScreens()

      cloudBackupCreator.createCalls.awaitItem()

      cloudBackupRepository.awaitBackup(
        cloudAccount
      ).shouldBe(CloudBackupV2WithFullAccountMock)

      onBackupSavedCalls.awaitItem()
    }
  }
})
