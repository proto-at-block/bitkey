package build.wallet.statemachine.settings.full.device.fingerprints

import app.cash.turbine.plusAssign
import bitkey.f8e.fingerprintreset.FingerprintResetResponse
import bitkey.f8e.privilegedactions.AuthorizationStrategy
import bitkey.f8e.privilegedactions.AuthorizationStrategyType
import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import bitkey.f8e.privilegedactions.PrivilegedActionType
import bitkey.metrics.MetricTrackerServiceFake
import bitkey.privilegedactions.FingerprintResetF8eClientFake
import bitkey.privilegedactions.FingerprintResetServiceImpl
import bitkey.privilegedactions.GrantDaoFake
import build.wallet.account.AccountServiceFake
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.auth.AppAuthKeyMessageSignerMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.SignatureUtilsMock
import build.wallet.firmware.*
import build.wallet.grants.GrantAction
import build.wallet.grants.GrantRequest
import build.wallet.grants.GrantTestHelpers
import build.wallet.ktor.result.EmptyResponseBody
import build.wallet.nfc.NfcCommandsMock
import build.wallet.nfc.NfcSessionFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachineTester
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.recovery.inprogress.waiting.AppDelayNotifyInProgressBodyModel
import build.wallet.statemachine.root.RemainingRecoveryDelayWordsUpdateFrequency
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.*
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitSheet
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.time.ClockFake
import build.wallet.time.DurationFormatterFake
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.util.encodeBase64
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

class FingerprintResetUiStateMachineImplTests : FunSpec({

  val nfcSessionUiStateMachine =
    object : NfcSessionUIStateMachine, ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(
      id = "nfc-session"
    ) {}
  val accountServiceFake = AccountServiceFake()
  val clock = ClockFake()
  val fingerprintResetF8eClientFake = FingerprintResetF8eClientFake(
    clock
  )
  val signatureUtils = SignatureUtilsMock()
  val nfcCommandsMock = NfcCommandsMock(turbines::create)
  val metricTrackerService = MetricTrackerServiceFake()
  val grantDaoFake = GrantDaoFake()
  val hardwareUnlockInfoService = HardwareUnlockInfoServiceFake()
  val fingerprintResetService = FingerprintResetServiceImpl(
    privilegedActionF8eClient = fingerprintResetF8eClientFake,
    accountService = accountServiceFake,
    signatureUtils = signatureUtils,
    clock = clock,
    grantDao = grantDaoFake,
    hardwareUnlockInfoService = hardwareUnlockInfoService,
    messageSigner = AppAuthKeyMessageSignerMock()
  )

  val enrollingFingerprintUiStateMachine =
    object : EnrollingFingerprintUiStateMachine,
      ScreenStateMachineMock<EnrollingFingerprintProps>(
        id = "enrolling-fingerprint"
      ) {}

  val stateMachine = FingerprintResetUiStateMachineImpl(
    nfcSessionUIStateMachine = nfcSessionUiStateMachine,
    clock = clock,
    durationFormatter = DurationFormatterFake(),
    fingerprintResetService = fingerprintResetService,
    remainingRecoveryDelayWordsUpdateFrequency = RemainingRecoveryDelayWordsUpdateFrequency(1.milliseconds),
    enrollingFingerprintUiStateMachine = enrollingFingerprintUiStateMachine,
    metricTrackerService = metricTrackerService,
    fingerprintResetGrantNfcHandler = FingerprintResetGrantNfcHandler(
      fingerprintResetService = fingerprintResetService
    )
  )

  val onCompleteCalls = turbines.create<Unit>("onComplete calls")
  val onCancelCalls = turbines.create<Unit>("onCancel calls")
  val onFwUpRequiredCalls = turbines.create<Unit>("onFwUpRequired calls")

  val props = FingerprintResetProps(
    onComplete = { _ -> onCompleteCalls += Unit },
    onCancel = { onCancelCalls += Unit },
    onFwUpRequired = { onFwUpRequiredCalls += Unit },
    account = FullAccountMock
  )

  beforeTest {
    clock.reset()
    metricTrackerService.reset()
    accountServiceFake.setActiveAccount(FullAccountMock)
    grantDaoFake.reset()
    fingerprintResetF8eClientFake.reset()
    fingerprintResetService.deleteFingerprintResetGrant()
    nfcCommandsMock.setFirmwareFeatureFlags(
      listOf(
        FirmwareFeatureFlagCfg(
          flag = FirmwareFeatureFlag.FINGERPRINT_RESET,
          enabled = true
        )
      )
    )
  }

  test("initial state shows confirmation body") {
    fingerprintResetF8eClientFake.getPrivilegedActionInstancesResult = Ok(emptyList())

    stateMachine.test(props) {
      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()

      awaitBody<FingerprintResetConfirmationBodyModel> {
        header
          .shouldNotBeNull()
          .headline.shouldBe("Start fingerprint reset")
      }
    }
  }

  test("confirm reset shows tap device sheet") {
    fingerprintResetF8eClientFake.getPrivilegedActionInstancesResult = Ok(emptyList())

    stateMachine.test(props) {
      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()

      awaitBody<FingerprintResetConfirmationBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitSheet<FingerprintResetConfirmationSheetModel> {
        header.shouldNotBeNull()
        header.headline.shouldBe("Wake your Bitkey device")
      }
    }
  }

  test("dismissing tap device sheet returns to confirmation") {
    fingerprintResetF8eClientFake.getPrivilegedActionInstancesResult = Ok(emptyList())

    stateMachine.test(props) {
      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()

      awaitBody<FingerprintResetConfirmationBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitSheet<FingerprintResetConfirmationSheetModel> {
        onDismiss()
      }

      awaitItem().bottomSheetModel.shouldBeNull()
    }
  }

  test("confirming tap device sheet transitions to NFC state") {
    fingerprintResetF8eClientFake.getPrivilegedActionInstancesResult = Ok(emptyList())

    stateMachine.test(props) {
      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()

      awaitBody<FingerprintResetConfirmationBodyModel> { primaryButton!!.onClick() }
      awaitSheet<FingerprintResetConfirmationSheetModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<FingerprintResetGrantRequestResult>>(id = nfcSessionUiStateMachine.id) {
        val mockGrantRequest = GrantRequest(
          version = 1.toByte(),
          deviceId = ByteArray(8) { 0x01 },
          challenge = ByteArray(16) { 0x02 },
          action = GrantAction.FINGERPRINT_RESET,
          signature = "21a1aa12efc8512727856a9ccc428a511cf08b211f26551781ae0a37661de8060c566ded9486500f6927e9c9df620c65653c68316e61930a49ecab31b3bec498".decodeHex()
            .toByteArray()
        )
        val sessionResult = session(NfcSessionFake(), nfcCommandsMock)
        sessionResult shouldBe FingerprintResetGrantRequestResult.GrantRequestRetrieved(mockGrantRequest)

        onSuccess(sessionResult)
      }

      nfcCommandsMock.getGrantRequestCalls.awaitItem() shouldBe GrantAction.FINGERPRINT_RESET

      awaitBody<AppDelayNotifyInProgressBodyModel> {
        header.shouldNotBeNull()
          .headline.shouldBe("Fingerprint reset in progress...")
      }
    }
  }

  test("shows progress screen if reset is already in progress") {
    val pendingActionInstance = createPendingActionInstance(clock = clock)

    fingerprintResetF8eClientFake.getPrivilegedActionInstancesResult =
      Ok(listOf(pendingActionInstance))

    stateMachine.test(props) {
      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()

      awaitBody<AppDelayNotifyInProgressBodyModel> {
        header.shouldNotBeNull()
          .headline.shouldBe("Fingerprint reset in progress...")

        durationTitle.shouldBe("3d")
      }
    }
  }

  test("clicking close on confirmation body calls onCancel") {
    fingerprintResetF8eClientFake.getPrivilegedActionInstancesResult = Ok(emptyList())

    stateMachine.test(props) {
      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()

      awaitBody<FingerprintResetConfirmationBodyModel> {
        val accessory =
          toolbar?.leadingAccessory.shouldBeInstanceOf<ToolbarAccessoryModel.IconAccessory>()
        accessory.model.onClick?.invoke()
      }

      onCancelCalls.awaitItem()
    }
  }

  test("closing D+N progress screen calls onCancel") {
    val pendingActionInstance = createPendingActionInstance(clock = clock)

    fingerprintResetF8eClientFake.getPrivilegedActionInstancesResult =
      Ok(listOf(pendingActionInstance))

    stateMachine.test(props) {
      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()

      awaitBody<AppDelayNotifyInProgressBodyModel> {
        onExit.shouldNotBeNull().invoke()
      }

      onCancelCalls.awaitItem()
    }
  }

  test("cancelling reset from progress screen calls onCancel") {
    val pendingActionInstance = createPendingActionInstance(clock = clock)

    fingerprintResetF8eClientFake.getPrivilegedActionInstancesResult =
      Ok(listOf(pendingActionInstance))
    fingerprintResetF8eClientFake.cancelFingerprintResetResult = Ok(EmptyResponseBody)

    stateMachine.test(props) {
      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()

      awaitBody<AppDelayNotifyInProgressBodyModel> {
        onStopRecovery.shouldNotBeNull().invoke()
      }

      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()

      onCancelCalls.awaitItem()
    }
  }

  test("cancelling reset from finish screen calls onCancel") {
    val delayStartTime = clock.now - 3.days
    val delayEndTime = clock.now - 1.days
    val cancellationToken = "test-cancel-token"
    val completionToken = "test-complete-token"

    val pendingActionInstance = PrivilegedActionInstance(
      id = "pending-action-123",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayStartTime = delayStartTime,
        delayEndTime = delayEndTime,
        cancellationToken = cancellationToken,
        completionToken = completionToken
      )
    )

    fingerprintResetF8eClientFake.getPrivilegedActionInstancesResult =
      Ok(listOf(pendingActionInstance))
    fingerprintResetF8eClientFake.cancelFingerprintResetResult = Ok(EmptyResponseBody)

    stateMachine.test(props) {
      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()

      awaitUntilBody<FinishFingerprintResetBodyModel> {
        onCancelReset()
      }

      awaitUntilBody<LoadingSuccessBodyModel>()

      onCancelCalls.awaitItem()
    }
  }

  test("completing reset from finish screen calls onComplete") {
    val pendingActionInstance = createPendingActionInstance(
      clock = clock,
      delayStartTime = clock.now - 3.days,
      delayEndTime = clock.now - 1.days
    )

    fingerprintResetF8eClientFake.getPrivilegedActionInstancesResult =
      Ok(listOf(pendingActionInstance))
    fingerprintResetF8eClientFake.continuePrivilegedActionResult = Ok(createFingerprintResetResponse())

    stateMachine.test(props) {
      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()

      awaitBody<FinishFingerprintResetBodyModel> {
        header.shouldNotBeNull()
          .headline.shouldBe("Finish fingerprint reset")
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitSheet<FingerprintResetConfirmationSheetModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitUntilBody<LoadingSuccessBodyModel> {
        id.shouldBe(FingerprintResetEventTrackerScreenId.LOADING_GRANT)
      }

      completeNfcGrantSuccessfully(nfcSessionUiStateMachine)
      completeFingerprintEnrollment()

      awaitBody<FingerprintResetSuccessBodyModel> {
        onDone()
      }

      onCompleteCalls.awaitItem()
    }
  }

  test("firmware update required when FINGERPRINT_RESET feature is disabled") {
    fingerprintResetF8eClientFake.getPrivilegedActionInstancesResult = Ok(emptyList())

    // Set up firmware feature flags without FINGERPRINT_RESET
    nfcCommandsMock.setFirmwareFeatureFlags(
      listOf(
        FirmwareFeatureFlagCfg(
          flag = FirmwareFeatureFlag.DEVICE_INFO_FLAG,
          enabled = true
        ),
        FirmwareFeatureFlagCfg(
          flag = FirmwareFeatureFlag.MULTIPLE_FINGERPRINTS,
          enabled = true
        )
      )
    )

    stateMachine.test(props) {
      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()

      awaitBody<FingerprintResetConfirmationBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitSheet<FingerprintResetConfirmationSheetModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      // NFC session should return FwUpRequired and immediately call onFwUpRequired
      awaitBodyMock<NfcSessionUIStateMachineProps<FingerprintResetGrantRequestResult>>(id = nfcSessionUiStateMachine.id) {
        val sessionResult = session(NfcSessionFake(), nfcCommandsMock)
        sessionResult shouldBe FingerprintResetGrantRequestResult.FwUpRequired

        onSuccess(sessionResult)
      }

      onFwUpRequiredCalls.awaitItem()
    }
  }

  test("NFC cancellation during grant provision preserves grant and allows retry") {
    val delayStartTime = clock.now - 3.days
    val delayEndTime = clock.now - 1.days
    val actionId = "pending-action-123"
    val completionToken = "test-complete-token"

    val pendingActionInstance = PrivilegedActionInstance(
      id = actionId,
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayStartTime = delayStartTime,
        delayEndTime = delayEndTime,
        cancellationToken = "test-cancel-token",
        completionToken = completionToken
      )
    )

    fingerprintResetF8eClientFake.getPrivilegedActionInstancesResult =
      Ok(listOf(pendingActionInstance))
    fingerprintResetF8eClientFake.continuePrivilegedActionResult = Ok(createFingerprintResetResponse())

    stateMachine.test(props) {
      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()

      progressThroughFinishFlowToGrantLoading()

      // First attempt: User cancels NFC session
      awaitBodyMock<NfcSessionUIStateMachineProps<FingerprintResetGrantProvisionResult>>(id = nfcSessionUiStateMachine.id) {
        eventTrackerContext.shouldBe(NfcEventTrackerScreenIdContext.RESET_FINGERPRINTS_PROVIDE_SIGNED_GRANT)
        // Simulate NFC cancellation
        onCancel()
      }

      // Should show error screen with retry option (not exit the flow)
      val errorScreen = awaitItem()
      val errorBody = errorScreen.body.shouldBeInstanceOf<FormBodyModel>()
      errorBody.header.shouldNotBeNull().apply {
        headline.shouldBe("NFC Error")
        sublineModel.shouldNotBeNull().string.shouldBe("There was an issue communicating with your hardware. Please try again.")
      }

      // Click retry - should not call server again since we preserved the grant
      errorBody.primaryButton.shouldNotBeNull().onClick()

      // Second attempt: NFC succeeds
      awaitBodyMock<NfcSessionUIStateMachineProps<FingerprintResetGrantProvisionResult>>(id = nfcSessionUiStateMachine.id) {
        eventTrackerContext.shouldBe(NfcEventTrackerScreenIdContext.RESET_FINGERPRINTS_PROVIDE_SIGNED_GRANT)
        onSuccess(FingerprintResetGrantProvisionResult.ProvideGrantSuccess)
      }

      // Should proceed to fingerprint enrollment
      awaitBodyMock<EnrollingFingerprintProps> {
        context.shouldBeInstanceOf<EnrollmentContext.FingerprintReset>()
      }
    }
  }

  test("NFC error during grant provision preserves grant and allows retry") {
    val delayStartTime = clock.now - 3.days
    val delayEndTime = clock.now - 1.days
    val actionId = "pending-action-123"
    val completionToken = "test-complete-token"

    val pendingActionInstance = PrivilegedActionInstance(
      id = actionId,
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayStartTime = delayStartTime,
        delayEndTime = delayEndTime,
        cancellationToken = "test-cancel-token",
        completionToken = completionToken
      )
    )

    fingerprintResetF8eClientFake.getPrivilegedActionInstancesResult =
      Ok(listOf(pendingActionInstance))
    fingerprintResetF8eClientFake.continuePrivilegedActionResult = Ok(createFingerprintResetResponse())

    stateMachine.test(props) {
      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()

      progressThroughFinishFlowToGrantLoading()

      // First attempt: NFC error occurs
      awaitBodyMock<NfcSessionUIStateMachineProps<FingerprintResetGrantProvisionResult>>(id = nfcSessionUiStateMachine.id) {
        eventTrackerContext.shouldBe(NfcEventTrackerScreenIdContext.RESET_FINGERPRINTS_PROVIDE_SIGNED_GRANT)
        // Simulate NFC error being handled by our onError callback
        val nfcError = build.wallet.nfc.NfcException.CommandError()
        val handled = onError(nfcError)
        handled.shouldBe(true) // Verify our handler claimed the error
      }

      // Should show error screen with retry option (not exit the flow)
      val errorScreen = awaitItem()
      val errorBody = errorScreen.body.shouldBeInstanceOf<FormBodyModel>()
      errorBody.header.shouldNotBeNull().apply {
        headline.shouldBe("NFC Error")
        sublineModel.shouldNotBeNull().string.shouldBe("There was an issue communicating with your hardware. Please try again.")
      }

      // Click retry - should not call server again since we preserved the grant
      errorBody.primaryButton.shouldNotBeNull().onClick()

      // Second attempt: NFC succeeds
      awaitBodyMock<NfcSessionUIStateMachineProps<FingerprintResetGrantProvisionResult>>(id = nfcSessionUiStateMachine.id) {
        eventTrackerContext.shouldBe(NfcEventTrackerScreenIdContext.RESET_FINGERPRINTS_PROVIDE_SIGNED_GRANT)
        onSuccess(FingerprintResetGrantProvisionResult.ProvideGrantSuccess)
      }

      // Should proceed to fingerprint enrollment
      awaitBodyMock<EnrollingFingerprintProps> {
        context.shouldBeInstanceOf<EnrollmentContext.FingerprintReset>()
      }
    }
  }

  test("NFC error cancel button returns to grant delivery screen") {
    val pendingActionInstance = createPendingActionInstance(
      clock = clock,
      delayStartTime = clock.now - 3.days,
      delayEndTime = clock.now - 1.days
    )

    fingerprintResetF8eClientFake.getPrivilegedActionInstancesResult =
      Ok(listOf(pendingActionInstance))
    fingerprintResetF8eClientFake.continuePrivilegedActionResult = Ok(createFingerprintResetResponse())

    stateMachine.test(props) {
      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()

      awaitBody<FinishFingerprintResetBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitSheet<FingerprintResetConfirmationSheetModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitUntilBody<LoadingSuccessBodyModel> {
        id.shouldBe(FingerprintResetEventTrackerScreenId.LOADING_GRANT)
      }

      handleNfcCancellation(nfcSessionUiStateMachine)

      verifyErrorScreenAndCancel()

      awaitBody<FinishFingerprintResetBodyModel>()
      onCancelCalls.expectNoEvents()
    }
  }

  test("server error during grant completion shows error, cancel returns to finish screen") {
    val pendingActionInstance = createPendingActionInstance(
      clock = clock,
      delayStartTime = clock.now - 3.days,
      delayEndTime = clock.now - 1.days
    )

    fingerprintResetF8eClientFake.getPrivilegedActionInstancesResult =
      Ok(listOf(pendingActionInstance))

    fingerprintResetF8eClientFake.continuePrivilegedActionResult =
      Err(RuntimeException("Network error"))

    stateMachine.test(props) {
      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()

      awaitBody<FinishFingerprintResetBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitSheet<FingerprintResetConfirmationSheetModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitUntilBody<LoadingSuccessBodyModel> {
        id.shouldBe(FingerprintResetEventTrackerScreenId.LOADING_GRANT)
      }

      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("Error Completing Reset")

        secondaryButton.shouldNotBeNull().onClick()
      }

      awaitBody<FinishFingerprintResetBodyModel>()
      onCancelCalls.expectNoEvents()
    }
  }

  test("server error during grant completion shows error, retry succeeds") {
    val pendingActionInstance = createPendingActionInstance(
      clock = clock,
      delayStartTime = clock.now - 3.days,
      delayEndTime = clock.now - 1.days
    )

    fingerprintResetF8eClientFake.getPrivilegedActionInstancesResult =
      Ok(listOf(pendingActionInstance))

    // Make the grant request fail initially
    fingerprintResetF8eClientFake.continuePrivilegedActionResult =
      Err(RuntimeException("Network error"))

    stateMachine.test(props) {
      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()

      awaitBody<FinishFingerprintResetBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitSheet<FingerprintResetConfirmationSheetModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitUntilBody<LoadingSuccessBodyModel> {
        id.shouldBe(FingerprintResetEventTrackerScreenId.LOADING_GRANT)
      }

      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("Error Completing Reset")

        // Make the retry succeed
        fingerprintResetF8eClientFake.continuePrivilegedActionResult = Ok(createFingerprintResetResponse())

        // Retry button should retry the server request
        primaryButton.shouldNotBeNull().onClick()
      }

      // Should return to loading grant state
      awaitUntilBody<LoadingSuccessBodyModel> {
        id.shouldBe(FingerprintResetEventTrackerScreenId.LOADING_GRANT)
      }

      completeNfcGrantSuccessfully(nfcSessionUiStateMachine)
      completeFingerprintEnrollment()

      awaitBody<FingerprintResetSuccessBodyModel> {
        onDone()
      }

      onCompleteCalls.awaitItem()
      onCancelCalls.expectNoEvents()
    }
  }

  test("cancelling with persisted grant calls onCancel") {
    // Create a grant and persist it to simulate having retrieved it from the server
    val mockGrant = build.wallet.grants.Grant(
      version = 1,
      serializedRequest = GrantTestHelpers.createMockSerializedGrantRequest(GrantAction.FINGERPRINT_RESET),
      appSignature = ByteArray(64) { it.toByte() },
      wsmSignature = ByteArray(64) { it.toByte() }
    )

    grantDaoFake.saveGrant(mockGrant)

    // Start fresh with no server-side action
    fingerprintResetF8eClientFake.getPrivilegedActionInstancesResult = Ok(emptyList())

    stateMachine.test(props) {
      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()

      // Should recognize persisted grant and go to finish screen
      awaitBody<FinishFingerprintResetBodyModel> {
        header.shouldNotBeNull()
          .headline.shouldBe("Finish fingerprint reset")

        onCancelReset()
      }

      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()

      // Should call onCancel
      onCancelCalls.awaitItem()
    }
  }

  test("grant provision fails but grant already delivered - recovery path succeeds") {
    val pendingActionInstance = createPendingActionInstance(
      clock = clock,
      delayStartTime = clock.now - 3.days,
      delayEndTime = clock.now - 1.days
    )

    fingerprintResetF8eClientFake.getPrivilegedActionInstancesResult =
      Ok(listOf(pendingActionInstance))
    fingerprintResetF8eClientFake.continuePrivilegedActionResult = Ok(createFingerprintResetResponse())

    // Set up mock enrolled fingerprints with multiple fingerprints to test cleanup
    val enrolledFingerprints = EnrolledFingerprints(
      fingerprintHandles = immutableListOf(
        FingerprintHandle(index = 0, label = "Primary"),
        FingerprintHandle(index = 1, label = "Secondary"),
        FingerprintHandle(index = 2, label = "Backup")
      )
    )
    nfcCommandsMock.setEnrolledFingerprints(enrolledFingerprints)

    stateMachine.test(props) {
      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()

      progressThroughFinishFlowToGrantLoading()

      // Configure NFC mock to fail grant provision but return the enrolled fingerprints
      nfcCommandsMock.setProvideGrantResult(false)

      val nfcSession = NfcSessionFake()
      awaitBodyMock<NfcSessionUIStateMachineProps<FingerprintResetGrantProvisionResult>>(id = nfcSessionUiStateMachine.id) {
        eventTrackerContext.shouldBe(NfcEventTrackerScreenIdContext.RESET_FINGERPRINTS_PROVIDE_SIGNED_GRANT)

        // This will simulate the scenario: provideGrant fails but isGrantDelivered returns true
        // The DAO will have the grant from the continuePrivilegedActionResult, and during the
        // session execution, we'll mark it as delivered before checking
        grantDaoFake.markAsDelivered(GrantAction.FINGERPRINT_RESET)

        val sessionResult = session(nfcSession, nfcCommandsMock)
        val newEnrolledFingerprints = enrolledFingerprints.copy(
          fingerprintHandles = enrolledFingerprints.fingerprintHandles.filter { it.index == 0 }
        )
        sessionResult shouldBe FingerprintResetGrantProvisionResult.FingerprintResetComplete(newEnrolledFingerprints)

        onSuccess(sessionResult)
      }

      nfcCommandsMock.provideGrantCalls.awaitItem()
      nfcCommandsMock.getEnrolledFingerprintsCalls.awaitItem()

      // Verify fingerprint deletion calls were made for non-index-0 fingerprints
      nfcCommandsMock.deleteFingerprintCalls.awaitItem() shouldBe 1
      nfcCommandsMock.deleteFingerprintCalls.awaitItem() shouldBe 2

      awaitBody<FingerprintResetSuccessBodyModel> {
        header.shouldNotBeNull()
          .headline.shouldBe("Fingerprint successfully saved")
        onDone()
      }

      onCompleteCalls.awaitItem()
    }
  }

  test("genuine grant provision failure shows retryable error") {
    val pendingActionInstance = createPendingActionInstance(
      clock = clock,
      delayStartTime = clock.now - 3.days,
      delayEndTime = clock.now - 1.days
    )

    fingerprintResetF8eClientFake.getPrivilegedActionInstancesResult =
      Ok(listOf(pendingActionInstance))
    fingerprintResetF8eClientFake.continuePrivilegedActionResult = Ok(createFingerprintResetResponse())

    stateMachine.test(props) {
      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()

      progressThroughFinishFlowToGrantLoading()

      // Configure NFC mock to fail grant provision and no grant was previously delivered
      nfcCommandsMock.setProvideGrantResult(false)

      awaitBodyMock<NfcSessionUIStateMachineProps<FingerprintResetGrantProvisionResult>>(id = nfcSessionUiStateMachine.id) {
        eventTrackerContext.shouldBe(NfcEventTrackerScreenIdContext.RESET_FINGERPRINTS_PROVIDE_SIGNED_GRANT)

        val sessionResult = session(NfcSessionFake(), nfcCommandsMock)
        sessionResult shouldBe FingerprintResetGrantProvisionResult.ProvideGrantFailed

        onSuccess(sessionResult)
      }

      nfcCommandsMock.provideGrantCalls.awaitItem()

      // Should show error screen with retry option
      val errorScreen = awaitItem()
      val errorBody = errorScreen.body.shouldBeInstanceOf<FormBodyModel>()
      errorBody.header.shouldNotBeNull().apply {
        headline.shouldBe("NFC Error")
        sublineModel.shouldNotBeNull().string.shouldBe("There was an issue communicating with your hardware. Please try again.")
      }

      errorBody.primaryButton.shouldNotBeNull().text.shouldBe("Retry")
      errorBody.secondaryButton.shouldNotBeNull().text.shouldBe("Cancel")
    }
  }
})

private fun createPendingActionInstance(
  clock: ClockFake,
  id: String = "pending-action-123",
  delayStartTime: kotlinx.datetime.Instant = clock.now - 1.days,
  delayEndTime: kotlinx.datetime.Instant = clock.now + 3.days,
  cancellationToken: String = "test-cancel-token",
  completionToken: String = "test-complete-token",
) = PrivilegedActionInstance(
  id = id,
  privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
  authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
    authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
    delayStartTime = delayStartTime,
    delayEndTime = delayEndTime,
    cancellationToken = cancellationToken,
    completionToken = completionToken
  )
)

private fun createFingerprintResetResponse(): FingerprintResetResponse {
  val mockSerializedRequest = GrantTestHelpers.createMockSerializedGrantRequest(GrantAction.FINGERPRINT_RESET)

  return FingerprintResetResponse(
    version = 1,
    serializedRequest = mockSerializedRequest.encodeBase64(),
    appSignature = ByteArray(64) { 0x04 }.toByteString().hex(),
    wsmSignature = ByteArray(64) { 0x04 }.toByteString().hex()
  )
}

private suspend fun StateMachineTester<FingerprintResetProps, ScreenModel>.progressThroughFinishFlowToGrantLoading() {
  awaitBody<FinishFingerprintResetBodyModel> {
    primaryButton.shouldNotBeNull().onClick()
  }
  awaitSheet<FingerprintResetConfirmationSheetModel> {
    primaryButton.shouldNotBeNull().onClick()
  }
  awaitUntilBody<LoadingSuccessBodyModel> {
    id.shouldBe(FingerprintResetEventTrackerScreenId.LOADING_GRANT)
  }
}

private suspend fun StateMachineTester<FingerprintResetProps, ScreenModel>.handleNfcCancellation(
  nfcSessionUiStateMachine: ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>,
) {
  awaitBodyMock<NfcSessionUIStateMachineProps<FingerprintResetGrantProvisionResult>>(id = nfcSessionUiStateMachine.id) {
    eventTrackerContext.shouldBe(NfcEventTrackerScreenIdContext.RESET_FINGERPRINTS_PROVIDE_SIGNED_GRANT)
    onCancel()
  }
}

private suspend fun StateMachineTester<FingerprintResetProps, ScreenModel>.verifyErrorScreenAndCancel() {
  val errorScreen = awaitItem()
  val errorBody = errorScreen.body.shouldBeInstanceOf<FormBodyModel>()
  errorBody.secondaryButton.shouldNotBeNull().onClick()
}

private suspend fun StateMachineTester<FingerprintResetProps, ScreenModel>.completeNfcGrantSuccessfully(
  nfcSessionUiStateMachine: ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>,
) {
  awaitBodyMock<NfcSessionUIStateMachineProps<FingerprintResetGrantProvisionResult>>(id = nfcSessionUiStateMachine.id) {
    eventTrackerContext.shouldBe(NfcEventTrackerScreenIdContext.RESET_FINGERPRINTS_PROVIDE_SIGNED_GRANT)
    onSuccess(FingerprintResetGrantProvisionResult.ProvideGrantSuccess)
  }
}

private suspend fun StateMachineTester<FingerprintResetProps, ScreenModel>.completeFingerprintEnrollment() {
  awaitBodyMock<EnrollingFingerprintProps> {
    fingerprintHandle.shouldBe(FingerprintHandle(index = 0, label = ""))
    context.shouldBeInstanceOf<EnrollmentContext.FingerprintReset>()
    onSuccess(
      EnrolledFingerprints(
        fingerprintHandles = immutableListOf(FingerprintHandle(index = 0, label = ""))
      )
    )
  }
}
