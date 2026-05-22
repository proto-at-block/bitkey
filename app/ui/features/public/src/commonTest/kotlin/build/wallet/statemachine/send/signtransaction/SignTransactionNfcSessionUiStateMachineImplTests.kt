package build.wallet.statemachine.send.signtransaction

import app.cash.turbine.Turbine
import bitkey.account.HardwareType
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock2
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.spending.AppSpendingPublicKeyMock
import build.wallet.bitkey.spending.HwSpendingPublicKeyMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.encrypt.SignatureVerifierMock
import build.wallet.encrypt.SignatureVerifierMock.VerifyEcdsaCall
import build.wallet.f8e.recovery.LostHardwareServerRecoveryMock
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.NfcSessionRetryAttemptsFeatureFlag
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.bitkey.keybox.FullAccountW3Mock
import build.wallet.money.display.BitcoinDisplayPreferenceRepositoryFake
import build.wallet.nfc.*
import build.wallet.nfc.platform.ConfirmationHandlesFake
import build.wallet.nfc.platform.ConfirmationResult
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.confirmationResultMapper
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.recovery.Recovery
import build.wallet.recovery.Recovery.StillRecovering.ServerDependentRecovery.InitiatedRecovery
import build.wallet.recovery.RecoveryStatusServiceMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.nfc.DescriptorRepairUiProps
import build.wallet.statemachine.nfc.DescriptorRepairUiStateMachine
import build.wallet.statemachine.core.test
import build.wallet.statemachine.platform.nfc.EnableNfcNavigatorMock
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationUiProps
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationUiStateMachine
import build.wallet.statemachine.send.signtransaction.SignTransactionNfcBodyModel.Status.*
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.core.form.FormBodyModel
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import okio.ByteString.Companion.encodeUtf8

/**
 * Tests for [SignTransactionNfcSessionUiStateMachineImpl].
 *
 * Note: Due to [SignTransactionResult] being private to the implementation,
 * these tests focus on error handling and NFC session parameter validation.
 * Full W1/W3 flow testing would require either making SignTransactionResult public
 * or integration testing with real NFC commands.
 */
class SignTransactionNfcSessionUiStateMachineImplTests : FunSpec({

  val eventTracker = EventTrackerMock(turbines::create)
  val nfcTransactor = NfcTransactorMock(turbines::create)
  val deviceInfoProvider = DeviceInfoProviderMock()
  val keyboxDao = KeyboxDaoMock(turbines::create)
  val signatureVerifierTurbine = turbines.create<VerifyEcdsaCall>("verifyEcdsa calls")
  val nfcSessionRetryAttemptsFeatureFlag = NfcSessionRetryAttemptsFeatureFlag(FeatureFlagDaoFake())
  val hardwareConfirmationUiStateMachine =
    object : HardwareConfirmationUiStateMachine,
      ScreenStateMachineMock<HardwareConfirmationUiProps>("hardware-confirmation") {}
  val recoveryStatusService = RecoveryStatusServiceMock(turbine = turbines::create)
  var customStateMachineIndex = 0

  val stateMachine =
    SignTransactionNfcSessionUiStateMachineImpl(
      enableNfcNavigator = EnableNfcNavigatorMock(),
      eventTracker = eventTracker,
      nfcReaderCapability = NfcReaderCapabilityMock(),
      nfcTransactor = nfcTransactor,
      deviceInfoProvider = deviceInfoProvider,
      keyboxDao = keyboxDao,
      signatureVerifier = SignatureVerifierMock(signatureVerifierTurbine),
      nfcSessionRetryAttemptsFeatureFlag = nfcSessionRetryAttemptsFeatureFlag,
      hardwareConfirmationUiStateMachine = hardwareConfirmationUiStateMachine,
      inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create),
      descriptorRepairUiStateMachine = object :
        DescriptorRepairUiStateMachine,
        ScreenStateMachineMock<DescriptorRepairUiProps>("descriptor-repair") {},
      recoveryStatusService = recoveryStatusService,
      bitcoinDisplayPreferenceRepository = BitcoinDisplayPreferenceRepositoryFake(),
    )

  fun createStateMachineWithTransactor(nfcTransactor: NfcTransactor): SignTransactionNfcSessionUiStateMachine {
    val instanceIndex = customStateMachineIndex++
    return SignTransactionNfcSessionUiStateMachineImpl(
      enableNfcNavigator = EnableNfcNavigatorMock(),
      eventTracker = eventTracker,
      nfcReaderCapability = NfcReaderCapabilityMock(),
      nfcTransactor = nfcTransactor,
      deviceInfoProvider = deviceInfoProvider,
      keyboxDao = keyboxDao,
      signatureVerifier = SignatureVerifierMock(signatureVerifierTurbine),
      nfcSessionRetryAttemptsFeatureFlag = nfcSessionRetryAttemptsFeatureFlag,
      hardwareConfirmationUiStateMachine = hardwareConfirmationUiStateMachine,
      inAppBrowserNavigator = InAppBrowserNavigatorMock { name ->
        turbines.create("$name-$instanceIndex")
      },
      descriptorRepairUiStateMachine = object :
        DescriptorRepairUiStateMachine,
        ScreenStateMachineMock<DescriptorRepairUiProps>("descriptor-repair-$instanceIndex") {},
      recoveryStatusService = recoveryStatusService,
      bitcoinDisplayPreferenceRepository = BitcoinDisplayPreferenceRepositoryFake(),
    )
  }

  val onBackCalls = turbines.create<Unit>("onBack calls")
  val onSuccessCalls = turbines.create<Psbt>("onSuccess calls")
  val onErrorCalls = turbines.create<NfcException>("onError calls")

  val props =
    SignTransactionNfcSessionUiProps(
      account = FullAccountMock,
      psbt = PsbtMock,
      onBack = { onBackCalls.add(Unit) },
      onSuccess = { psbt -> onSuccessCalls.add(psbt) },
      onError = { error ->
        onErrorCalls.add(error)
        true // Handled - don't show default error UI
      }
    )

  beforeTest {
    deviceInfoProvider.reset()
    nfcTransactor.reset()
    keyboxDao.reset()
    recoveryStatusService.reset()
  }

  // Basic UI State Tests

  test("initial state shows searching") {
    // Set to never complete so we can just verify initial state
    nfcTransactor.transactResult = Err(NfcException.Timeout())

    stateMachine.test(props) {
      // Initially shows searching state
      awaitBody<SignTransactionNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
        onCancel.shouldNotBeNull()
      }

      nfcTransactor.transactCalls.awaitItem()
      onErrorCalls.awaitItem()
    }
  }

  test("legacy sign transaction screens remain non-platform NFC screens") {
    nfcTransactor.transactResult = Err(NfcException.Timeout())

    stateMachine.test(props) {
      awaitItem().apply {
        platformNfcScreen.shouldBe(false)
        presentationStyle.shouldBe(ScreenPresentationStyle.FullScreen)
        body.shouldBeTypeOf<SignTransactionNfcBodyModel>().status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()
      onErrorCalls.awaitItem()
    }
  }

  test("lost connection remains a non-platform NFC screen") {
    val transactGate = nfcTransactor.pauseNextTransact()
    nfcTransactor.transactResult = Err(NfcException.Timeout())

    stateMachine.test(props) {
      awaitBody<SignTransactionNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      val transactCalls =
        nfcTransactor.transactCalls.awaitItem()
          .shouldBeTypeOf<NfcSession.Parameters>()

      transactCalls.onTagDisconnected()

      awaitItem().apply {
        platformNfcScreen.shouldBe(false)
        presentationStyle.shouldBe(ScreenPresentationStyle.FullScreen)
        body.shouldBeTypeOf<SignTransactionNfcBodyModel>().status.shouldBeTypeOf<LostConnection>()
      }

      transactGate.complete(Unit)
      onErrorCalls.awaitItem().shouldBeTypeOf<NfcException.Timeout>()
    }
  }

  test("cancel button invokes onBack") {
    nfcTransactor.transactResult = Err(NfcException.Timeout())

    stateMachine.test(props) {
      awaitBody<SignTransactionNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
        onCancel.shouldNotBeNull().invoke()
      }

      nfcTransactor.transactCalls.awaitItem()
      onErrorCalls.awaitItem()
      onBackCalls.awaitItem()
    }
  }

  // Error Handling Tests

  test("user cancellation on iOS invokes onBack") {
    nfcTransactor.transactResult = Err(NfcException.IOSOnly.UserCancellation())

    stateMachine.test(props) {
      awaitBody<SignTransactionNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()
      onBackCalls.awaitItem()
    }
  }

  test("command error invokes onError") {
    nfcTransactor.transactResult = Err(NfcException.CommandError("Test error"))

    stateMachine.test(props) {
      awaitBody<SignTransactionNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()
      onErrorCalls.awaitItem().shouldBeTypeOf<NfcException.CommandError>()
    }
  }

  test("tag lost error invokes onError") {
    nfcTransactor.transactResult = Err(NfcException.CanBeRetried.TagLost())

    stateMachine.test(props) {
      awaitBody<SignTransactionNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()
      onErrorCalls.awaitItem().shouldBeTypeOf<NfcException.CanBeRetried.TagLost>()
    }
  }

  test("timeout error invokes onError") {
    nfcTransactor.transactResult = Err(NfcException.Timeout())

    stateMachine.test(props) {
      awaitBody<SignTransactionNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()
      onErrorCalls.awaitItem().shouldBeTypeOf<NfcException.Timeout>()
    }
  }

  test("unauthenticated error invokes onError") {
    nfcTransactor.transactResult = Err(NfcException.CommandErrorUnauthenticated())

    stateMachine.test(props) {
      awaitBody<SignTransactionNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()
      onErrorCalls.awaitItem().shouldBeTypeOf<NfcException.CommandErrorUnauthenticated>()
    }
  }

  // NFC Session Parameters Tests

  test("NFC session parameters are correctly configured for W1") {
    nfcTransactor.transactResult = Err(NfcException.Timeout())

    stateMachine.test(props) {
      awaitBody<SignTransactionNfcBodyModel>()

      val transactCalls = nfcTransactor.transactCalls.awaitItem()
        .shouldBeTypeOf<NfcSession.Parameters>()

      transactCalls.hardwareType.shouldBe(HardwareType.W1)
      transactCalls.needsAuthentication.shouldBe(true)
      transactCalls.shouldLock.shouldBe(true)
      transactCalls.skipFirmwareTelemetry.shouldBe(false)
      transactCalls.nfcFlowName.shouldBe("sign-transaction")
      transactCalls.asyncNfcSigning.shouldBe(false)

      onErrorCalls.awaitItem()
    }
  }

  test("NFC session parameters use the passed account's W3 hardware type") {
    nfcTransactor.transactResult = Err(NfcException.Timeout())

    stateMachine.test(props.copy(account = FullAccountW3Mock)) {
      awaitBody<SignTransactionNfcBodyModel>()

      val transactCalls = nfcTransactor.transactCalls.awaitItem()
        .shouldBeTypeOf<NfcSession.Parameters>()

      transactCalls.hardwareType.shouldBe(HardwareType.W3)
      transactCalls.needsAuthentication.shouldBe(true)
      transactCalls.shouldLock.shouldBe(true)
      transactCalls.skipFirmwareTelemetry.shouldBe(false)
      transactCalls.nfcFlowName.shouldBe("sign-transaction")
      transactCalls.asyncNfcSigning.shouldBe(false)

      onErrorCalls.awaitItem()
    }
  }

  test("W3 confirmation continuation carries resolved device info into second tap parameters") {
    val w3ResolvedDeviceInfo =
      FirmwareDeviceInfoMock.copy(hwRevision = "w3a-core-evt", serial = "w3-confirmation-serial")
    val commands =
      W3NfcCommandsMock(turbines::create).apply {
        deviceInfoResult = w3ResolvedDeviceInfo
        signTransactionResult = HardwareInteraction.RequiresConfirmation(
          handles = ConfirmationHandlesFake,
          mapResult = confirmationResultMapper { result ->
            when (result) {
              is ConfirmationResult.SignTx -> HardwareInteraction.Completed(PsbtMock)
              else -> throw NfcException.CommandError("unexpected result: ${result::class.simpleName}")
            }
          }
        )
        confirmationResult = ConfirmationResult.SignTx(emptyList())
      }
    val executingTransactor =
      RecordingExecutingNfcTransactor(
        commandsPerCall = listOf(commands, commands),
        turbineFactory = turbines::create
      )

    createStateMachineWithTransactor(executingTransactor).test(
      props.copy(account = FullAccountW3Mock)
    ) {
      awaitBody<SignTransactionNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      val initialParams = executingTransactor.transactCalls.awaitItem()
        .shouldBeTypeOf<NfcSession.Parameters>()
      initialParams.nfcFlowName.shouldBe("sign-transaction")
      initialParams.resolvedDeviceInfoOverride.shouldBeNull()

      awaitBodyMock<HardwareConfirmationUiProps>(id = "hardware-confirmation") {
        onConfirm()
      }

      awaitBody<SignTransactionNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      val continuationParams = executingTransactor.transactCalls.awaitItem()
        .shouldBeTypeOf<NfcSession.Parameters>()
      continuationParams.nfcFlowName.shouldBe("sign-transaction-confirmation")
      continuationParams.resolvedDeviceInfoOverride.shouldBe(w3ResolvedDeviceInfo)

      commands.signTransactionCalls.awaitItem().shouldBe(PsbtMock)
      commands.getConfirmationResultCalls.awaitItem().shouldBe(ConfirmationHandlesFake)
      onSuccessCalls.awaitItem().shouldBe(PsbtMock)
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("NFC session can skip firmware telemetry when requested") {
    nfcTransactor.transactResult = Err(NfcException.Timeout())

    stateMachine.test(props.copy(skipFirmwareTelemetry = true)) {
      awaitBody<SignTransactionNfcBodyModel>()

      val transactCalls = nfcTransactor.transactCalls.awaitItem()
        .shouldBeTypeOf<NfcSession.Parameters>()

      transactCalls.skipFirmwareTelemetry.shouldBe(true)

      onErrorCalls.awaitItem()
    }
  }

  test("hardwareTypeOverride takes precedence over the account config") {
    nfcTransactor.transactResult = Err(NfcException.Timeout())

    stateMachine.test(
      props.copy(
        account = FullAccountW3Mock,
        hardwareTypeOverride = HardwareType.W1
      )
    ) {
      awaitBody<SignTransactionNfcBodyModel>()

      val transactCalls = nfcTransactor.transactCalls.awaitItem()
        .shouldBeTypeOf<NfcSession.Parameters>()

      transactCalls.hardwareType.shouldBe(HardwareType.W1)

      onErrorCalls.awaitItem()
    }
  }

  test("default error handling shows NFC error UI") {
    nfcTransactor.transactResult = Err(NfcException.CommandError("Test error"))

    val propsWithDefaultErrorHandler =
      SignTransactionNfcSessionUiProps(
        account = FullAccountMock,
        psbt = PsbtMock,
        onBack = { onBackCalls.add(Unit) },
        onSuccess = { psbt -> onSuccessCalls.add(psbt) }
        // onError not provided - uses default (returns false)
      )

    stateMachine.test(propsWithDefaultErrorHandler) {
      awaitBody<SignTransactionNfcBodyModel>()

      nfcTransactor.transactCalls.awaitItem()

      // Should show error UI (NfcErrorFormBodyModel)
      awaitBody<build.wallet.statemachine.core.form.FormBodyModel>()
    }
  }

  // W3 Two-Tap Flow Error Tests
  // Note: Full two-tap flow testing with UserDenied/ConfirmationPending requires
  // integration testing since these exceptions are caught during the continuation
  // phase which requires internal state transitions. These tests verify the
  // exception types are properly handled as errors when thrown outside continuation context.

  test("UserDenied error invokes onError when no continuation") {
    nfcTransactor.transactResult = Err(NfcException.UserDenied())

    stateMachine.test(props) {
      awaitBody<SignTransactionNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()
      onErrorCalls.awaitItem().shouldBeTypeOf<NfcException.UserDenied>()
    }
  }

  test("ConfirmationPending error invokes onError when no continuation") {
    nfcTransactor.transactResult = Err(NfcException.ConfirmationPending())

    stateMachine.test(props) {
      awaitBody<SignTransactionNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()
      onErrorCalls.awaitItem().shouldBeTypeOf<NfcException.ConfirmationPending>()
    }
  }

  test("UserDenied with default error handler shows NFC error UI") {
    nfcTransactor.transactResult = Err(NfcException.UserDenied())

    val propsWithDefaultErrorHandler =
      SignTransactionNfcSessionUiProps(
        account = FullAccountMock,
        psbt = PsbtMock,
        onBack = { onBackCalls.add(Unit) },
        onSuccess = { psbt -> onSuccessCalls.add(psbt) }
        // onError not provided - uses default (returns false)
      )

    stateMachine.test(propsWithDefaultErrorHandler) {
      awaitBody<SignTransactionNfcBodyModel>()

      nfcTransactor.transactCalls.awaitItem()

      // Should show error UI when no continuation and error not handled
      awaitBody<build.wallet.statemachine.core.form.FormBodyModel>()
    }
  }

  test("ConfirmationPending with default error handler shows NFC error UI") {
    nfcTransactor.transactResult = Err(NfcException.ConfirmationPending())

    val propsWithDefaultErrorHandler =
      SignTransactionNfcSessionUiProps(
        account = FullAccountMock,
        psbt = PsbtMock,
        onBack = { onBackCalls.add(Unit) },
        onSuccess = { psbt -> onSuccessCalls.add(psbt) }
        // onError not provided - uses default (returns false)
      )

    stateMachine.test(propsWithDefaultErrorHandler) {
      awaitBody<SignTransactionNfcBodyModel>()

      nfcTransactor.transactCalls.awaitItem()

      // Should show error UI when no continuation and error not handled
      awaitBody<build.wallet.statemachine.core.form.FormBodyModel>()
    }
  }

  // Recovery Hw Auth Key Tests

  test("useRecoveryHwAuthKey uses recovery hw key when StillRecovering") {
    // Set up recovery status with a distinct hardware key
    val recoveryHwAuthKey = HwAuthPublicKey(Secp256k1PublicKey("hw-auth-dpub-recovery"))
    recoveryStatusService.recoveryStatus.value = InitiatedRecovery(
      fullAccountId = FullAccountIdMock,
      appSpendingKey = AppSpendingPublicKeyMock,
      appGlobalAuthKey = AppGlobalAuthPublicKeyMock,
      appRecoveryAuthKey = AppRecoveryAuthPublicKeyMock,
      hardwareSpendingKey = HwSpendingPublicKeyMock,
      hardwareAuthKey = recoveryHwAuthKey,
      appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
      factorToRecover = Hardware,
      serverRecovery = LostHardwareServerRecoveryMock,
      originalAppGlobalAuthKey = AppGlobalAuthPublicKeyMock2
    )

    nfcTransactor.transactResult = Err(NfcException.Timeout())

    val recoveryProps = SignTransactionNfcSessionUiProps(
      account = FullAccountMock,
      psbt = PsbtMock,
      useRecoveryHwAuthKey = true,
      onBack = { onBackCalls.add(Unit) },
      onSuccess = { psbt -> onSuccessCalls.add(psbt) },
      onError = { error ->
        onErrorCalls.add(error)
        true
      }
    )

    stateMachine.test(recoveryProps) {
      awaitBody<SignTransactionNfcBodyModel>()

      val params = nfcTransactor.transactCalls.awaitItem()
        .shouldBeTypeOf<NfcSession.Parameters>()

      // Should use recovery hw auth key for pairing
      val required = params.requirePairedHardware
        .shouldBeTypeOf<NfcSession.RequirePairedHardware.Required>()
      required.checkHardwareIsPaired("signature", "challenge".encodeUtf8())

      // Verify the signature verifier was called with the recovery key
      signatureVerifierTurbine.awaitItem().publicKey.shouldBe(recoveryHwAuthKey.pubKey)

      onErrorCalls.awaitItem()
    }
  }

  test("useRecoveryHwAuthKey falls back to keybox key when not StillRecovering") {
    // Recovery is not in progress — should fall back to active keybox hw key
    recoveryStatusService.recoveryStatus.value = Recovery.NoActiveRecovery

    // Set up active keybox so its key can be resolved
    keyboxDao.activeKeybox.value = com.github.michaelbull.result.Ok(KeyboxMock)

    nfcTransactor.transactResult = Err(NfcException.Timeout())

    val recoveryProps = SignTransactionNfcSessionUiProps(
      account = FullAccountMock,
      psbt = PsbtMock,
      useRecoveryHwAuthKey = true,
      onBack = { onBackCalls.add(Unit) },
      onSuccess = { psbt -> onSuccessCalls.add(psbt) },
      onError = { error ->
        onErrorCalls.add(error)
        true
      }
    )

    stateMachine.test(recoveryProps) {
      awaitBody<SignTransactionNfcBodyModel>()

      val params = nfcTransactor.transactCalls.awaitItem()
        .shouldBeTypeOf<NfcSession.Parameters>()

      // Should fall back to active keybox hw auth key
      val required = params.requirePairedHardware
        .shouldBeTypeOf<NfcSession.RequirePairedHardware.Required>()
      required.checkHardwareIsPaired("signature", "challenge".encodeUtf8())

      // Verify the signature verifier was called with the keybox key
      signatureVerifierTurbine.awaitItem()
        .publicKey.shouldBe(KeyboxMock.activeHwKeyBundle.authKey.pubKey)

      onErrorCalls.awaitItem()
    }
  }

  test("default useRecoveryHwAuthKey=false uses keybox hw key") {
    // Default props don't set useRecoveryHwAuthKey
    keyboxDao.activeKeybox.value = com.github.michaelbull.result.Ok(KeyboxMock)

    nfcTransactor.transactResult = Err(NfcException.Timeout())

    stateMachine.test(props) {
      awaitBody<SignTransactionNfcBodyModel>()

      val params = nfcTransactor.transactCalls.awaitItem()
        .shouldBeTypeOf<NfcSession.Parameters>()

      // Should use active keybox hw auth key
      val required = params.requirePairedHardware
        .shouldBeTypeOf<NfcSession.RequirePairedHardware.Required>()
      required.checkHardwareIsPaired("signature", "challenge".encodeUtf8())

      // Verify the signature verifier was called with the keybox key
      signatureVerifierTurbine.awaitItem()
        .publicKey.shouldBe(KeyboxMock.activeHwKeyBundle.authKey.pubKey)

      onErrorCalls.awaitItem()
    }
  }
})

private class RecordingExecutingNfcTransactor(
  private val commandsPerCall: List<NfcCommands>,
  turbineFactory: (String) -> Turbine<Any>,
  private val sessionFactory: (NfcSession.Parameters, Int) -> NfcSession =
    { parameters, _ -> SilentNfcSession(parameters) },
) : NfcTransactor {
  override var isTransacting: Boolean = false
  val transactCalls = turbineFactory("executing transact calls")
  private var transactCount = 0

  override suspend fun <T> transact(
    parameters: NfcSession.Parameters,
    transaction: TransactionFn<T>,
  ): Result<T, NfcException> {
    isTransacting = true
    transactCalls.add(parameters)
    val callIndex = transactCount++
    val commands = commandsPerCall.getOrElse(callIndex) { commandsPerCall.last() }
    return try {
      Ok(transaction(sessionFactory(parameters, callIndex), commands))
    } catch (error: NfcException) {
      Err(error)
    } finally {
      isTransacting = false
    }
  }
}

private class SilentNfcSession(
  override val parameters: NfcSession.Parameters,
) : NfcSession {
  override var message: String? = null

  override suspend fun transceive(buffer: List<UByte>): List<UByte> = emptyList()

  override fun close() = Unit
}
