package build.wallet.statemachine.recovery.lostapp.initiate

import androidx.compose.runtime.Composable
import app.cash.turbine.ReceiveTurbine
import bitkey.account.HardwareType
import bitkey.backup.DescriptorBackup
import bitkey.f8e.error.F8eError.ConnectivityError
import bitkey.privilegedactions.ActionProofService
import bitkey.privilegedactions.ActionProofServiceFake
import bitkey.recovery.InitiateDelayNotifyRecoveryError.*
import build.wallet.account.analytics.AppInstallationDaoMock
import build.wallet.auth.AccountAuthTokensMock
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitkey.auth.HwAuthSecp256k1PublicKeyMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.AppKeyBundleMock
import build.wallet.cloud.backup.AllFullAccountBackupMocks
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.csek.SsekDaoFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.crypto.SymmetricKeyImpl
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.nfc.NfcCommandsMock
import build.wallet.nfc.NfcSessionFake
import build.wallet.nfc.platform.ActionProofAction
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.nfc.platform.LostAppRecoveryCompositeResult
import build.wallet.nfc.platform.LostAppRecoveryContinueParams
import build.wallet.recovery.CancelDelayNotifyRecoveryError.F8eCancelDelayNotifyError
import build.wallet.recovery.DescriptorBackupServiceFake
import build.wallet.recovery.DescriptorBackupServiceFake.Companion.HW_DESCRIPTOR_PUBKEY
import build.wallet.recovery.LostAppAndCloudRecoveryService.CompletedAuth
import build.wallet.recovery.LostAppAndCloudRecoveryServiceFake
import build.wallet.statemachine.BodyModelMock
import build.wallet.statemachine.BodyStateMachineMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcConfirmableSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcConfirmableSessionUiStateMachineMock
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.platform.permissions.EnableNotificationsUiProps
import build.wallet.statemachine.platform.permissions.EnableNotificationsUiStateMachine
import build.wallet.statemachine.recovery.cloud.FullAccountCloudBackupRestorationUiProps
import build.wallet.statemachine.recovery.cloud.FullAccountCloudBackupRestorationUiStateMachine
import build.wallet.statemachine.recovery.inprogress.RecoverYourAppKeyBodyModel
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiProps
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilBodyMock
import build.wallet.time.MinimumLoadingDuration
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import okio.ByteString.Companion.encodeUtf8
import kotlin.time.Duration.Companion.milliseconds
import build.wallet.recovery.CancelDelayNotifyRecoveryError.CommsVerificationRequiredError as CancelCommsVerificationRequiredError

class InitiatingLostAppRecoveryUiStateMachineImplTests : FunSpec({
  // Custom NFC mock that captures the latest props so tests can simulate success/cancel.
  var latestNfcProps: NfcSessionUIStateMachineProps<*>? = null
  val nfcSessionUIStateMachine = object : NfcSessionUIStateMachine {
    @Composable
    override fun model(props: NfcSessionUIStateMachineProps<*>): ScreenModel {
      latestNfcProps = props
      return BodyModelMock(id = "nfc-session", latestProps = props).asRootScreen()
    }
  }

  val enableNotificationsUiStateMachine =
    object : EnableNotificationsUiStateMachine,
      BodyStateMachineMock<EnableNotificationsUiProps>(id = "enable-notifications") {}

  val recoveryNotificationVerificationUiStateMachine =
    object : RecoveryNotificationVerificationUiStateMachine,
      ScreenStateMachineMock<RecoveryNotificationVerificationUiProps>(
        id = "recovery-notification-verification"
      ) {}

  val fullAccountCloudBackupRestorationUiStateMachine =
    object : FullAccountCloudBackupRestorationUiStateMachine,
      ScreenStateMachineMock<FullAccountCloudBackupRestorationUiProps>(
        id = "full-account-cloud-backup-restoration"
      ) {}

  val ssekDao = SsekDaoFake()
  val descriptorBackupService = DescriptorBackupServiceFake()
  val nfcCommandsMock = NfcCommandsMock(turbines::create)
  val lostAppAndCloudRecoveryService = LostAppAndCloudRecoveryServiceFake()
  val nfcConfirmableSessionUiStateMachine =
    NfcConfirmableSessionUiStateMachineMock(id = "nfc-confirmable-session")
  val actionProofService = ActionProofServiceFake()
  val appInstallationDao = AppInstallationDaoMock()

  val stateMachine = InitiatingLostAppRecoveryUiStateMachineImpl(
    nfcSessionUIStateMachine = nfcSessionUIStateMachine,
    enableNotificationsUiStateMachine = enableNotificationsUiStateMachine,
    recoveryNotificationVerificationUiStateMachine = recoveryNotificationVerificationUiStateMachine,
    fullAccountCloudBackupRestorationUiStateMachine = fullAccountCloudBackupRestorationUiStateMachine,
    ssekDao = ssekDao,
    descriptorBackupService = descriptorBackupService,
    lostAppAndCloudRecoveryService = lostAppAndCloudRecoveryService,
    minimumLoadingDuration = MinimumLoadingDuration(0.milliseconds),
    nfcConfirmableSessionUiStateMachine = nfcConfirmableSessionUiStateMachine,
    actionProofService = actionProofService,
    appInstallationDao = appInstallationDao
  )

  fun props(cloudBackups: List<CloudBackup> = emptyList()) =
    InitiatingLostAppRecoveryUiProps(
      cloudBackups = cloudBackups,
      onRollback = {},
      goToLiteAccountCreation = {}
    )

  fun hardwareAuthResult(hardwareType: HardwareType) =
    InitiatingLostAppRecoveryUiStateMachineImpl.HardwareAuthResult(
      hardwareAuthKey = HwAuthSecp256k1PublicKeyMock,
      hardwareType = hardwareType
    )

  beforeTest {
    ssekDao.reset()
    descriptorBackupService.reset()
    nfcCommandsMock.reset()
    lostAppAndCloudRecoveryService.reset()
    actionProofService.reset()
    appInstallationDao.reset()
    latestNfcProps = null
  }

  @Suppress("UNCHECKED_CAST")
  suspend fun <T> simulateNfcSuccess(value: T) {
    (latestNfcProps as NfcSessionUIStateMachineProps<T>).onSuccess(value)
  }

  fun simulateNfcCancel() {
    latestNfcProps?.onCancel?.invoke()
  }

  /**
   * Advances the turbine from the instructions screen through the auth flow:
   * instructions -> hardware-key NFC -> auth-challenge loading -> sign-challenge NFC
   * -> authentication loading.
   *
   * For W1, the sign-challenge step uses [nfcSessionUIStateMachine] (simple signChallenge).
   * For W3, it uses [nfcConfirmableSessionUiStateMachine] (confirmable lostAppRecoverySignChallenge).
   *
   * On return, the turbine is positioned immediately before the proof-of-possession NFC screen
   * for W1 or the confirmable composite recovery screen for W3.
   */
  suspend fun ReceiveTurbine<ScreenModel>.advanceThroughAuthSteps(
    hardwareType: HardwareType = HardwareType.W1,
  ) {
    awaitBody<RecoverYourAppKeyBodyModel> { onStartRecovery.shouldNotBeNull().invoke() }
    awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") { // get hardware auth key
      shouldLock.shouldBeFalse()
      showDeviceConfirmation.shouldBeTrue()
    }
    simulateNfcSuccess(hardwareAuthResult(hardwareType))
    awaitBody<LoadingSuccessBodyModel>() // "Authenticating with server..."
    when (hardwareType) {
      HardwareType.W1 -> {
        awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") { // sign challenge
          shouldLock.shouldBeFalse()
        }
        simulateNfcSuccess("signed-challenge")
      }
      HardwareType.W3 -> {
        awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<String>>(
          id = nfcConfirmableSessionUiStateMachine.id
        ) {
          onSuccess("signed-challenge")
        }
      }
    }
    awaitBody<LoadingSuccessBodyModel>() // "Authenticating with hardware..."
  }

  /**
   * Awaits the W1 proof-of-possession NFC screen, runs the session, invokes onSuccess with the
   * result, and drains the corresponding NFC command call from [nfcCommandsMock].
   *
   * Assumes the turbine is positioned immediately before the proof-of-possession NFC screen
   * (i.e. [advanceThroughAuthSteps] has already been called for a W1 flow).
   */
  suspend fun ReceiveTurbine<ScreenModel>.completeProofOfPossessionNfc() {
    awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {
      shouldLock.shouldBeTrue()
      val result = session(NfcSessionFake(), nfcCommandsMock)!!
      @Suppress("UNCHECKED_CAST")
      (this as NfcSessionUIStateMachineProps<Any>).onSuccess(result)
    }
    nfcCommandsMock.getNextSpendingKeyCalls.awaitItem()
  }

  test("cloud-backup journey: shows backup restoration screen, recover-app-key reaches instructions") {
    val backup = AllFullAccountBackupMocks.first()
    stateMachine.test(props(cloudBackups = listOf(backup))) {
      awaitBodyMock<FullAccountCloudBackupRestorationUiProps>(
        id = fullAccountCloudBackupRestorationUiStateMachine.id
      ) {
        backups.shouldBe(listOf(backup))
        onRecoverAppKey()
      }
      awaitBody<RecoverYourAppKeyBodyModel>()
    }
  }

  test("happy-path journey: instructions -> NFC auth -> proof-of-possession -> notifications -> initiating recovery") {
    stateMachine.test(props()) {
      advanceThroughAuthSteps()
      completeProofOfPossessionNfc()
      awaitBodyMock<EnableNotificationsUiProps>(id = enableNotificationsUiStateMachine.id) {
        onComplete()
      }
      awaitBody<LoadingSuccessBodyModel> { message.shouldBe("Initiating recovery...") }
    }
  }

  test("direct-recovery journey: hardware-key NFC tap persists serial number for analytics") {
    stateMachine.test(props()) {
      awaitBody<RecoverYourAppKeyBodyModel> { onStartRecovery.shouldNotBeNull().invoke() }
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {
        // Run the actual NFC session lambda to exercise persistence
        val result = session(NfcSessionFake(), nfcCommandsMock)!!
        @Suppress("UNCHECKED_CAST")
        (this as NfcSessionUIStateMachineProps<Any>).onSuccess(result)
      }
      nfcCommandsMock.getAuthenticationKeyCalls.awaitItem()
      nfcCommandsMock.getDeviceInfoCalls.awaitItem()
      appInstallationDao.updateSerialNumberCalls.shouldContainExactly("fakeS203serial")
      awaitUntilBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {} // sign-challenge NFC
    }
  }

  test("direct-recovery journey: cancel hardware-keys NFC returns to instructions") {
    stateMachine.test(props()) {
      awaitBody<RecoverYourAppKeyBodyModel> { onStartRecovery.shouldNotBeNull().invoke() }
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session")
      simulateNfcCancel()
      awaitBody<RecoverYourAppKeyBodyModel>()
    }
  }

  test("direct-recovery journey: cancel sign-challenge NFC returns to instructions") {
    stateMachine.test(props()) {
      awaitBody<RecoverYourAppKeyBodyModel> { onStartRecovery.shouldNotBeNull().invoke() }
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") // hardware-key NFC
      simulateNfcSuccess(hardwareAuthResult(HardwareType.W1))
      awaitBody<LoadingSuccessBodyModel>() // "Authenticating with server..."
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") // sign-challenge NFC
      simulateNfcCancel()
      awaitBody<RecoverYourAppKeyBodyModel>()
    }
  }

  test("w3 direct-recovery journey: cancel sign-challenge confirmable NFC returns to instructions") {
    stateMachine.test(props()) {
      awaitBody<RecoverYourAppKeyBodyModel> { onStartRecovery.shouldNotBeNull().invoke() }
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") // hardware-key NFC
      simulateNfcSuccess(hardwareAuthResult(HardwareType.W3))
      awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<*>>(
        id = nfcConfirmableSessionUiStateMachine.id
      ) {
        onCancel()
      }
      awaitBody<RecoverYourAppKeyBodyModel>()
    }
  }

  test("direct-recovery journey: cancel proof-of-possession NFC returns to instructions") {
    stateMachine.test(props()) {
      advanceThroughAuthSteps()
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") // proof-of-possession NFC
      simulateNfcCancel()
      awaitBody<RecoverYourAppKeyBodyModel>()
    }
  }

  test("direct-recovery journey: auth initiation fails -> shows error") {
    lostAppAndCloudRecoveryService.initiateAuthResult = Err(Error("server unavailable"))
    stateMachine.test(props()) {
      awaitBody<RecoverYourAppKeyBodyModel> { onStartRecovery.shouldNotBeNull().invoke() }
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session")
      simulateNfcSuccess(hardwareAuthResult(HardwareType.W1))
      awaitBody<LoadingSuccessBodyModel>() // "Authenticating with server..."
      awaitBody<FormBodyModel> {
        header?.headline.shouldBe("We couldn't initiate recovery process.")
      }
    }
  }

  test("direct-recovery journey: authentication fails -> shows error") {
    lostAppAndCloudRecoveryService.completeAuthResult = Err(Error("auth failed"))
    stateMachine.test(props()) {
      awaitBody<RecoverYourAppKeyBodyModel> { onStartRecovery.shouldNotBeNull().invoke() }
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") // hardware-key NFC
      simulateNfcSuccess(hardwareAuthResult(HardwareType.W1))
      awaitBody<LoadingSuccessBodyModel>() // "Authenticating with server..."
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") // sign-challenge NFC
      simulateNfcSuccess("signed-challenge")
      awaitBody<LoadingSuccessBodyModel>() // "Authenticating with hardware..."
      awaitBody<FormBodyModel> {
        header?.headline.shouldBe("We couldn't initiate recovery process.")
      }
    }
  }

  test("direct-recovery journey: initiate recovery fails -> shows error") {
    lostAppAndCloudRecoveryService.initiateRecoveryResult = Err(OtherError(Error("server error")))
    stateMachine.test(props()) {
      advanceThroughAuthSteps()
      completeProofOfPossessionNfc()
      awaitBodyMock<EnableNotificationsUiProps>(id = enableNotificationsUiStateMachine.id) {
        onComplete()
      }
      awaitBody<LoadingSuccessBodyModel>() // "Initiating recovery..."
      awaitBody<FormBodyModel> {
        header?.headline.shouldBe("We couldn't initiate recovery process.")
      }
    }
  }

  test("direct-recovery journey: notifications retreat returns to instructions") {
    stateMachine.test(props()) {
      advanceThroughAuthSteps()
      completeProofOfPossessionNfc()
      awaitBodyMock<EnableNotificationsUiProps>(id = enableNotificationsUiStateMachine.id) {
        retreat.onRetreat()
      }
      awaitBody<RecoverYourAppKeyBodyModel>()
    }
  }

  test("comms-verification journey: initiate requires comms -> verify -> retry -> initiating recovery") {
    lostAppAndCloudRecoveryService.initiateRecoveryResult =
      Err(CommsVerificationRequiredError(Error()))
    stateMachine.test(props()) {
      advanceThroughAuthSteps()
      completeProofOfPossessionNfc()
      awaitBodyMock<EnableNotificationsUiProps>(id = enableNotificationsUiStateMachine.id) {
        onComplete()
      }
      awaitBody<LoadingSuccessBodyModel>() // "Initiating recovery..."
      awaitBodyMock<RecoveryNotificationVerificationUiProps>(
        id = recoveryNotificationVerificationUiStateMachine.id
      ) {
        fullAccountId.shouldBe(FullAccountIdMock)
        lostAppAndCloudRecoveryService.initiateRecoveryResult = Ok(Unit)
        onComplete()
      }
      awaitBody<LoadingSuccessBodyModel> { message.shouldBe("Initiating recovery...") }
    }
  }

  test("conflict journey: cancel existing recovery then initiate new recovery") {
    lostAppAndCloudRecoveryService.initiateRecoveryResult =
      Err(RecoveryAlreadyExistsError(Error()))
    stateMachine.test(props()) {
      advanceThroughAuthSteps()
      completeProofOfPossessionNfc()
      awaitBodyMock<EnableNotificationsUiProps>(id = enableNotificationsUiStateMachine.id) {
        onComplete()
      }
      awaitBody<LoadingSuccessBodyModel>() // "Initiating recovery..."
      awaitBody<RecoveryConflictBodyModel> {
        lostAppAndCloudRecoveryService.initiateRecoveryResult = Ok(Unit)
        onCancelRecovery.shouldNotBeNull().invoke()
      }
      // W1 cancel proof NFC tap
      awaitItem()
      simulateNfcSuccess(HwFactorProofOfPossession(""))
      awaitBody<LoadingSuccessBodyModel> { message.shouldBe("Cancelling Existing Recovery") }
      awaitBody<LoadingSuccessBodyModel> { message.shouldBe("Initiating recovery...") }
    }
  }

  test("w3 conflict journey: cancel conflicting recovery shows device confirmation") {
    val descriptorBackup = DescriptorBackup(
      keysetId = "test-keyset-123",
      sealedDescriptor = XCiphertext("fake-sealed-descriptor"),
      privateWalletRootXpub = null
    )
    lostAppAndCloudRecoveryService.completeAuthResult = Ok(
      CompletedAuth.WithDescriptorBackups(
        accountId = FullAccountIdMock,
        authTokens = AccountAuthTokensMock,
        hwAuthKey = HwAuthSecp256k1PublicKeyMock,
        destinationAppKeys = AppKeyBundleMock,
        bitcoinNetworkType = BitcoinNetworkType.SIGNET,
        descriptorBackups = listOf(descriptorBackup),
        wrappedSsek = "fake-sealed-ssek".encodeUtf8()
      )
    )
    lostAppAndCloudRecoveryService.initiateRecoveryResult =
      Err(RecoveryAlreadyExistsError(Error()))

    stateMachine.test(props()) {
      advanceThroughAuthSteps(HardwareType.W3)
      awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<LostAppRecoveryCompositeResult>>(
        id = nfcConfirmableSessionUiStateMachine.id
      ) {
        val result = session(NfcSessionFake(), nfcCommandsMock)
          .shouldBeInstanceOf<HardwareInteraction.Completed<LostAppRecoveryCompositeResult>>()
          .result

        this.onSuccess(result)
      }
      nfcCommandsMock.lostAppRecoveryCalls.awaitItem().shouldBe("fake-sealed-ssek".encodeUtf8())
      awaitBodyMock<EnableNotificationsUiProps>(id = enableNotificationsUiStateMachine.id) {
        onComplete()
      }
      awaitUntilBody<RecoveryConflictBodyModel> {
        onCancelRecovery.shouldNotBeNull().invoke()
      }
      awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<*>>(
        id = nfcConfirmableSessionUiStateMachine.id
      ) {
        config.hardwareTypeOverride.shouldBe(HardwareType.W3)
        config.showNativeSheetOnIos.shouldBeFalse()
        config.showDeviceConfirmation.shouldBeTrue()
      }
    }
  }

  test("conflict journey: cancel requires comms verification -> verify -> cancel -> initiate") {
    lostAppAndCloudRecoveryService.initiateRecoveryResult =
      Err(RecoveryAlreadyExistsError(Error()))
    lostAppAndCloudRecoveryService.cancelResult =
      Err(CancelCommsVerificationRequiredError(Error("comms required")))
    stateMachine.test(props()) {
      advanceThroughAuthSteps()
      completeProofOfPossessionNfc()
      awaitBodyMock<EnableNotificationsUiProps>(id = enableNotificationsUiStateMachine.id) {
        onComplete()
      }
      awaitBody<LoadingSuccessBodyModel>() // "Initiating recovery..."
      awaitBody<RecoveryConflictBodyModel> {
        lostAppAndCloudRecoveryService.initiateRecoveryResult = Ok(Unit)
        onCancelRecovery.shouldNotBeNull().invoke()
      }
      // W1 cancel proof NFC tap
      awaitItem()
      simulateNfcSuccess(HwFactorProofOfPossession(""))
      awaitBody<LoadingSuccessBodyModel> { message.shouldBe("Cancelling Existing Recovery") }
      awaitBodyMock<RecoveryNotificationVerificationUiProps>(
        id = recoveryNotificationVerificationUiStateMachine.id
      ) {
        lostAppAndCloudRecoveryService.cancelResult = Ok(Unit)
        onComplete()
      }
      awaitBody<LoadingSuccessBodyModel> { message.shouldBe("Cancelling Existing Recovery") }
      awaitBody<LoadingSuccessBodyModel> { message.shouldBe("Initiating recovery...") }
    }
  }

  test("conflict journey: cancel fails -> error screen, dismiss returns to instructions") {
    lostAppAndCloudRecoveryService.initiateRecoveryResult =
      Err(RecoveryAlreadyExistsError(Error()))
    lostAppAndCloudRecoveryService.cancelResult =
      Err(F8eCancelDelayNotifyError(ConnectivityError(NetworkError(Error()))))
    stateMachine.test(props()) {
      advanceThroughAuthSteps()
      completeProofOfPossessionNfc()
      awaitBodyMock<EnableNotificationsUiProps>(id = enableNotificationsUiStateMachine.id) {
        onComplete()
      }
      awaitBody<LoadingSuccessBodyModel>() // "Initiating recovery..."
      awaitBody<RecoveryConflictBodyModel> {
        onCancelRecovery.shouldNotBeNull().invoke()
      }
      // W1 cancel proof NFC tap
      awaitItem()
      simulateNfcSuccess(HwFactorProofOfPossession(""))
      awaitBody<LoadingSuccessBodyModel>() // "Cancelling Existing Recovery"
      awaitBody<FormBodyModel> {
        header?.headline.shouldBe(
          "We couldn't cancel the existing recovery. Please try your recovery again."
        )
        primaryButton?.onClick?.invoke()
      }
      awaitBody<RecoverYourAppKeyBodyModel>()
    }
  }

  test("w3 descriptor-backups journey uses confirmable recovery flow and action proof header") {
    val descriptorBackup = DescriptorBackup(
      keysetId = "test-keyset-123",
      sealedDescriptor = XCiphertext("fake-sealed-descriptor"),
      privateWalletRootXpub = null
    )
    val sealedSsek = "fake-sealed-ssek".encodeUtf8()
    val expectedBindings = "n=ab,tb=fake-token-binding-abc123"
    val expectedHwSignature = "a".repeat(128)
    val expectedSpendingKey = DescriptorPublicKey(
      "[34eae6a8/84'/0'/0']xpubDDj952KUFGTDcNV1qY5Tuevm6vnBWK8NSpTTkCz1XTApv2SeDaqcrUTBgDdCRF9KmtxV33R8E9NtSi9VSBUPj4M3fKr4uk3kRy8Vbo1LbAv/*"
    )
    actionProofService.generateNonceResult = "ab"
    actionProofService.buildBindingsResult = Ok(expectedBindings)
    lostAppAndCloudRecoveryService.completeAuthResult = Ok(
      CompletedAuth.WithDescriptorBackups(
        accountId = FullAccountIdMock,
        authTokens = AccountAuthTokensMock,
        hwAuthKey = HwAuthSecp256k1PublicKeyMock,
        destinationAppKeys = AppKeyBundleMock,
        bitcoinNetworkType = BitcoinNetworkType.SIGNET,
        descriptorBackups = listOf(descriptorBackup),
        wrappedSsek = sealedSsek
      )
    )

    nfcCommandsMock.shouldInvokeLostAppRecoveryContinue = true
    nfcCommandsMock.lostAppRecoveryUnsealedSsek = SymmetricKeyImpl(raw = "unsealed-ssek".encodeUtf8())
    nfcCommandsMock.lostAppRecoveryResult = HardwareInteraction.Completed(
      LostAppRecoveryCompositeResult(
        actionProofSignature = expectedHwSignature,
        spendingKeyDpub = expectedSpendingKey,
        appAuthKeySignature = "b".repeat(128)
      )
    )

    stateMachine.test(props()) {
      advanceThroughAuthSteps(HardwareType.W3)
      awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<*>>(
        id = nfcConfirmableSessionUiStateMachine.id
      ) {
        // The confirmable session runs the composite lost-app-recovery command, which
        // unseals the SSEK, derives the existing hardware spending keys, and returns
        // the action-proof signature plus the new spending key in one flow.
        val result = session(NfcSessionFake(), nfcCommandsMock)
          .shouldBeInstanceOf<HardwareInteraction.Completed<LostAppRecoveryCompositeResult>>()
          .result

        @Suppress("UNCHECKED_CAST")
        (this as NfcConfirmableSessionUIStateMachineProps<LostAppRecoveryCompositeResult>).onSuccess(result)
      }

      awaitBodyMock<EnableNotificationsUiProps>(id = enableNotificationsUiStateMachine.id)
    }

    actionProofService.buildBindingsCalls.shouldContainExactly(
      ActionProofServiceFake.BuildBindingsCall(
        extra = emptyMap(),
        nonce = "ab",
        accountId = FullAccountIdMock
      )
    )
    actionProofService.createActionProofHeaderCalls.shouldContainExactly(
      listOf(expectedHwSignature) to "ab"
    )
    nfcCommandsMock.lostAppRecoveryCalls.awaitItem().shouldBe(sealedSsek)
    // The continue phase should receive the descriptor-derived spending keys and
    // action-proof context needed to complete the composite hardware command.
    nfcCommandsMock.lostAppRecoveryContinueParamsCalls.awaitItem()
      .shouldBeInstanceOf<LostAppRecoveryContinueParams>()
      .apply {
        actionProofVersion shouldBe ActionProofService.ACTION_PROOF_VERSION
        actionProofAction shouldBe ActionProofAction.CREATE_LOST_APP_RECOVERY
        actionProofBindings shouldBe expectedBindings
        existingHwSpendingKeys.shouldContainExactly(HwSpendingPublicKey(HW_DESCRIPTOR_PUBKEY))
        network shouldBe BitcoinNetworkType.SIGNET
        appGlobalAuthKey shouldBe AppKeyBundleMock.authKey
      }
    ssekDao.get(sealedSsek).shouldNotBeNull()
  }

  test("w3 descriptor-backups journey: cancel confirmable NFC returns to instructions") {
    lostAppAndCloudRecoveryService.completeAuthResult = Ok(
      CompletedAuth.WithDescriptorBackups(
        accountId = FullAccountIdMock,
        authTokens = AccountAuthTokensMock,
        hwAuthKey = HwAuthSecp256k1PublicKeyMock,
        destinationAppKeys = AppKeyBundleMock,
        bitcoinNetworkType = BitcoinNetworkType.SIGNET,
        descriptorBackups = emptyList(),
        wrappedSsek = "fake-sealed-ssek".encodeUtf8()
      )
    )

    stateMachine.test(props()) {
      advanceThroughAuthSteps(HardwareType.W3)
      awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<*>>(
        id = nfcConfirmableSessionUiStateMachine.id
      ) {
        onCancel()
      }
      awaitBody<RecoverYourAppKeyBodyModel>()
    }
  }
})
