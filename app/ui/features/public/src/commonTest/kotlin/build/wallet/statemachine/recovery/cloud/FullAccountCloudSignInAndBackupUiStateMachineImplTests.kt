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
import build.wallet.cloud.backup.*
import build.wallet.cloud.backup.CloudBackupError.UnrectifiableCloudBackupError
import build.wallet.cloud.backup.FullAccountCloudBackupCreator.FullAccountCloudBackupCreatorError.FullAccountFieldsCreationError
import build.wallet.cloud.backup.FullAccountCloudBackupCreatorMock
import build.wallet.cloud.backup.csek.CsekDaoFake
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.cloud.backup.csek.SekGeneratorMock
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.emergencyexitkit.EmergencyExitKitPdfGeneratorFake
import build.wallet.emergencyexitkit.EmergencyExitKitRepositoryFake
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
    sekGenerator = SekGeneratorMock(),
    nfcSessionUIStateMachine =
      object : NfcSessionUIStateMachine, ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(
        "nfc-session-mock"
      ) {},
    csekDao = CsekDaoFake(),
    inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create),
    emergencyExitKitPdfGenerator = EmergencyExitKitPdfGeneratorFake(),
    emergencyExitKitRepository = EmergencyExitKitRepositoryFake()
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
    cloudBackupCreator.backupResult = Ok(CloudBackupV3WithFullAccountMock)
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
      ).shouldBe(CloudBackupV3WithFullAccountMock)

      onBackupSavedCalls.awaitItem()
    }
  }

  test("initiate backup - failed to upload backup") {
    cloudBackupCreator.backupResult = Ok(CloudBackupV3WithFullAccountMock)
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

  test("cloud sign in failure - try again and success") {
    cloudBackupCreator.backupResult = Ok(CloudBackupV3WithFullAccountMock)
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
      ).shouldBe(CloudBackupV3WithFullAccountMock)

      onBackupSavedCalls.awaitItem()
    }
  }

  test("find existing V3 backup and proceed automatically without callback") {
    cloudBackupCreator.backupResult = Ok(CloudBackupV3WithFullAccountMock)
    cloudBackupRepository.writeBackup(
      FullAccountIdMock,
      cloudAccount,
      CloudBackupV3WithFullAccountMock,
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
      ).shouldBe(CloudBackupV3WithFullAccountMock)

      onBackupSavedCalls.awaitItem()
    }
  }

  test("find existing V3 backup and proceed with callback") {
    cloudBackupCreator.backupResult = Ok(CloudBackupV3WithFullAccountMock)
    cloudBackupRepository.writeBackup(
      FullAccountIdMock,
      cloudAccount,
      CloudBackupV3WithFullAccountMock,
      requireAuthRefresh = true
    )
    val onExistingAppDataFoundCalls =
      turbines.create<CloudBackup?>("on existing cloud backup found")

    val adjustedProps =
      props.copy(onExistingAppDataFound = { existingBackup, proceed ->
        onExistingAppDataFoundCalls += existingBackup
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
        .shouldNotBeNull().shouldBeEqual(CloudBackupV3WithFullAccountMock)

      cloudBackupRepository.awaitBackup(
        cloudAccount
      ).shouldBe(CloudBackupV3WithFullAccountMock)

      onBackupSavedCalls.awaitItem()
    }
  }

  test("upgrades from V2 to V3 when existing V2 backup found") {
    // Set up existing V2 backup
    cloudBackupRepository.writeBackup(
      FullAccountIdMock,
      cloudAccount,
      CloudBackupV2WithFullAccountMock,
      requireAuthRefresh = true
    )

    // System should create V3 backup
    cloudBackupCreator.backupResult = Ok(CloudBackupV3WithFullAccountMock)

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

      // Verify V3 backup was created despite V2 existing
      cloudBackupRepository.awaitBackup(
        cloudAccount
      ).shouldBe(CloudBackupV3WithFullAccountMock)

      onBackupSavedCalls.awaitItem()
    }
  }

  test("skip cloud backup instructions") {
    cloudBackupCreator.backupResult = Ok(CloudBackupV3WithFullAccountMock)
    stateMachine.test(props.copy(isSkipCloudBackupInstructions = true)) {
      awaitBodyMock<CloudSignInUiProps> {
        forceSignOut.shouldBeFalse()
        onSignedIn(cloudAccount)
        awaitStartCloudBackupEvent()
      }

      awaitLoadingScreens()

      cloudBackupCreator.createCalls.awaitItem()

      cloudBackupRepository.awaitBackup(
        cloudAccount
      ).shouldBe(CloudBackupV3WithFullAccountMock)

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
})
