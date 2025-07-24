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
import build.wallet.account.AccountServiceFake
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.SignatureUtilsMock
import build.wallet.f8e.F8eEnvironment
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.FingerprintHandle
import build.wallet.firmware.FirmwareFeatureFlag
import build.wallet.firmware.FirmwareFeatureFlagCfg
import build.wallet.firmware.HardwareUnlockInfoServiceFake
import build.wallet.grants.GrantAction
import build.wallet.grants.GrantRequest
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
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetConfirmationBodyModel
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetConfirmationSheetModel
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetEventTrackerScreenId
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetNfcResult
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetProps
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetUiStateMachineImpl
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FinishFingerprintResetBodyModel
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitSheet
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.time.ClockFake
import build.wallet.time.DurationFormatterFake
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.util.encodeBase64
import io.ktor.utils.io.core.toByteArray
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
    turbines::create,
    clock
  )
  val signatureUtils = SignatureUtilsMock()
  val nfcCommandsMock = NfcCommandsMock(turbines::create)
  val metricTrackerService = MetricTrackerServiceFake()
  val fingerprintResetService = FingerprintResetServiceImpl(
    privilegedActionF8eClient = fingerprintResetF8eClientFake,
    accountService = accountServiceFake,
    signatureUtils = signatureUtils,
    clock = clock
  )

  val enrollingFingerprintUiStateMachine =
    object : EnrollingFingerprintUiStateMachine,
      ScreenStateMachineMock<EnrollingFingerprintProps>(
        id = "enrolling-fingerprint"
      ) {}

  val hardwareUnlockInfoService = HardwareUnlockInfoServiceFake()

  val stateMachine = FingerprintResetUiStateMachineImpl(
    nfcSessionUIStateMachine = nfcSessionUiStateMachine,
    clock = clock,
    durationFormatter = DurationFormatterFake(),
    fingerprintResetService = fingerprintResetService,
    remainingRecoveryDelayWordsUpdateFrequency = RemainingRecoveryDelayWordsUpdateFrequency(1.milliseconds),
    enrollingFingerprintUiStateMachine = enrollingFingerprintUiStateMachine,
    metricTrackerService = metricTrackerService,
    hardwareUnlockInfoService = hardwareUnlockInfoService
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

  val expectedEnv = FullAccountMock.config.f8eEnvironment
  val expectedAccountId = FullAccountMock.accountId

  beforeTest {
    clock.reset()
    metricTrackerService.reset()
    accountServiceFake.setActiveAccount(FullAccountMock)

    // Set up firmware feature flags to enable fingerprint reset
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

      fingerprintResetF8eClientFake.getPrivilegedActionInstancesCalls.awaitItem()
        .shouldBe(Pair(expectedEnv, expectedAccountId))
      fingerprintResetF8eClientFake.getPrivilegedActionInstancesCalls.expectNoEvents()

      awaitBody<FingerprintResetConfirmationBodyModel> {
        header
          .shouldNotBeNull()
          .headline.shouldBe("Start fingerprint reset")
      }

      fingerprintResetF8eClientFake.getPrivilegedActionInstancesCalls.expectNoEvents()
    }
  }

  test("confirm reset shows tap device sheet") {
    fingerprintResetF8eClientFake.getPrivilegedActionInstancesResult = Ok(emptyList())

    stateMachine.test(props) {
      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()

      fingerprintResetF8eClientFake.getPrivilegedActionInstancesCalls.awaitItem() shouldBe Pair(
        expectedEnv,
        expectedAccountId
      )
      fingerprintResetF8eClientFake.getPrivilegedActionInstancesCalls.expectNoEvents()

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

      fingerprintResetF8eClientFake.getPrivilegedActionInstancesCalls.awaitItem()
        .shouldBe(Pair(expectedEnv, expectedAccountId))
      fingerprintResetF8eClientFake.getPrivilegedActionInstancesCalls.expectNoEvents()

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

      fingerprintResetF8eClientFake.getPrivilegedActionInstancesCalls.awaitItem()
        .shouldBe(Pair(expectedEnv, expectedAccountId))
      fingerprintResetF8eClientFake.getPrivilegedActionInstancesCalls.expectNoEvents()

      awaitBody<FingerprintResetConfirmationBodyModel> { primaryButton!!.onClick() }
      awaitSheet<FingerprintResetConfirmationSheetModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<FingerprintResetNfcResult>>(id = nfcSessionUiStateMachine.id) {
        val mockGrantRequest = GrantRequest(
          version = 1.toByte(),
          deviceId = "mockDevice".toByteArray(),
          challenge = "mockChallenge".toByteArray(),
          action = GrantAction.FINGERPRINT_RESET,
          signature = "21a1aa12efc8512727856a9ccc428a511cf08b211f26551781ae0a37661de8060c566ded9486500f6927e9c9df620c65653c68316e61930a49ecab31b3bec498".decodeHex()
            .toByteArray()
        )
        val sessionResult = session(NfcSessionFake(), nfcCommandsMock)
        sessionResult shouldBe FingerprintResetNfcResult.GrantRequestRetrieved(mockGrantRequest)

        onSuccess(sessionResult)
      }

      fingerprintResetF8eClientFake.createPrivilegedActionCalls.awaitItem()

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
      awaitInitialLoadingAndApiCall(fingerprintResetF8eClientFake, expectedEnv, expectedAccountId)

      awaitBody<AppDelayNotifyInProgressBodyModel> {
        header.shouldNotBeNull()
          .headline.shouldBe("Fingerprint reset in progress...")

        durationTitle.shouldBe("3d")
      }

      onCompleteCalls.expectNoEvents()
      onCancelCalls.expectNoEvents()
    }
  }

  test("clicking close on confirmation body calls onCancel") {
    fingerprintResetF8eClientFake.getPrivilegedActionInstancesResult = Ok(emptyList())

    stateMachine.test(props) {
      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()

      fingerprintResetF8eClientFake.getPrivilegedActionInstancesCalls.awaitItem()
        .shouldBe(Pair(expectedEnv, expectedAccountId))
      fingerprintResetF8eClientFake.getPrivilegedActionInstancesCalls.expectNoEvents()

      awaitBody<FingerprintResetConfirmationBodyModel> {
        val accessory =
          toolbar?.leadingAccessory.shouldBeInstanceOf<ToolbarAccessoryModel.IconAccessory>()
        accessory.model.onClick?.invoke()
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
      awaitInitialLoadingAndApiCall(fingerprintResetF8eClientFake, expectedEnv, expectedAccountId)

      awaitBody<AppDelayNotifyInProgressBodyModel> {
        onStopRecovery.shouldNotBeNull().invoke()
      }

      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()
      fingerprintResetF8eClientFake.cancelFingerprintResetCalls.awaitItem()

      onCancelCalls.awaitItem()
    }
  }

  test("cancelling reset from finish screen calls onCancel") {
    val delayStartTime = clock.now - 1.days
    val delayEndTime = clock.now + 3.days
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

      fingerprintResetF8eClientFake.getPrivilegedActionInstancesCalls.awaitItem()

      // Advance time past the delay
      clock.now = delayEndTime + 1.days

      awaitUntilBody<FinishFingerprintResetBodyModel> {
        onCancelReset()
      }

      awaitUntilBody<LoadingSuccessBodyModel>()
      fingerprintResetF8eClientFake.cancelFingerprintResetCalls.awaitItem()

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
      awaitInitialLoadingAndApiCall(fingerprintResetF8eClientFake, expectedEnv, expectedAccountId)

      awaitBody<FinishFingerprintResetBodyModel> {
        header.shouldNotBeNull()
          .headline.shouldBe("Finish fingerprint reset")
        primaryButton.shouldNotBeNull().onClick()
      }

      progressThroughFinishFlowToGrantLoading(fingerprintResetF8eClientFake)

      completeNfcGrantSuccessfully(nfcSessionUiStateMachine)
      completeFingerprintEnrollment()

      onCompleteCalls.awaitItem()
      fingerprintResetF8eClientFake.getPrivilegedActionInstancesCalls.awaitItem()
      fingerprintResetF8eClientFake.getPrivilegedActionInstancesCalls.expectNoEvents()
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

      fingerprintResetF8eClientFake.getPrivilegedActionInstancesCalls.awaitItem()
        .shouldBe(Pair(expectedEnv, expectedAccountId))

      awaitBody<FingerprintResetConfirmationBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitSheet<FingerprintResetConfirmationSheetModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      // NFC session should return FwUpRequired and immediately call onFwUpRequired
      awaitBodyMock<NfcSessionUIStateMachineProps<FingerprintResetNfcResult>>(id = nfcSessionUiStateMachine.id) {
        val sessionResult = session(NfcSessionFake(), nfcCommandsMock)
        sessionResult shouldBe FingerprintResetNfcResult.FwUpRequired

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
    fingerprintResetF8eClientFake.continuePrivilegedActionResult = Ok(
      FingerprintResetResponse(
        version = 1,
        serializedRequest = "test".toByteArray().encodeBase64(),
        signature = "test".toByteArray().toByteString().hex()
      )
    )

    stateMachine.test(props) {
      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()
      fingerprintResetF8eClientFake.getPrivilegedActionInstancesCalls.awaitItem()

      progressThroughFinishFlowToGrantLoading(fingerprintResetF8eClientFake)

      // First attempt: User cancels NFC session
      awaitBodyMock<NfcSessionUIStateMachineProps<FingerprintResetNfcResult>>(id = nfcSessionUiStateMachine.id) {
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
      fingerprintResetF8eClientFake.continuePrivilegedActionCalls.expectNoEvents()

      // Second attempt: NFC succeeds
      awaitBodyMock<NfcSessionUIStateMachineProps<FingerprintResetNfcResult>>(id = nfcSessionUiStateMachine.id) {
        eventTrackerContext.shouldBe(NfcEventTrackerScreenIdContext.RESET_FINGERPRINTS_PROVIDE_SIGNED_GRANT)
        onSuccess(FingerprintResetNfcResult.ProvideGrantSuccess)
      }

      // Should proceed to fingerprint enrollment
      awaitBodyMock<EnrollingFingerprintProps> {
        context.shouldBe(EnrollmentContext.FingerprintReset)
      }

      // Verify onCancel was never called
      onCancelCalls.expectNoEvents()

      // Consume the additional getPrivilegedActionInstances call that happens after completion
      fingerprintResetF8eClientFake.getPrivilegedActionInstancesCalls.awaitItem()
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
    fingerprintResetF8eClientFake.continuePrivilegedActionResult = Ok(
      FingerprintResetResponse(
        version = 1,
        serializedRequest = "test".toByteArray().encodeBase64(),
        signature = "test".toByteArray().toByteString().hex()
      )
    )

    stateMachine.test(props) {
      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()
      fingerprintResetF8eClientFake.getPrivilegedActionInstancesCalls.awaitItem()

      progressThroughFinishFlowToGrantLoading(fingerprintResetF8eClientFake)

      // First attempt: NFC error occurs
      awaitBodyMock<NfcSessionUIStateMachineProps<FingerprintResetNfcResult>>(id = nfcSessionUiStateMachine.id) {
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
      fingerprintResetF8eClientFake.continuePrivilegedActionCalls.expectNoEvents()

      // Second attempt: NFC succeeds
      awaitBodyMock<NfcSessionUIStateMachineProps<FingerprintResetNfcResult>>(id = nfcSessionUiStateMachine.id) {
        eventTrackerContext.shouldBe(NfcEventTrackerScreenIdContext.RESET_FINGERPRINTS_PROVIDE_SIGNED_GRANT)
        onSuccess(FingerprintResetNfcResult.ProvideGrantSuccess)
      }

      // Should proceed to fingerprint enrollment
      awaitBodyMock<EnrollingFingerprintProps> {
        context.shouldBe(EnrollmentContext.FingerprintReset)
      }

      // Verify onCancel was never called
      onCancelCalls.expectNoEvents()

      // Consume the additional getPrivilegedActionInstances call that happens after completion
      fingerprintResetF8eClientFake.getPrivilegedActionInstancesCalls.awaitItem()
    }
  }

  test("error screen cancel button exits the entire flow") {
    val pendingActionInstance = createPendingActionInstance(
      clock = clock,
      delayStartTime = clock.now - 3.days,
      delayEndTime = clock.now - 1.days
    )

    fingerprintResetF8eClientFake.getPrivilegedActionInstancesResult =
      Ok(listOf(pendingActionInstance))
    fingerprintResetF8eClientFake.continuePrivilegedActionResult = Ok(createFingerprintResetResponse())

    stateMachine.test(props) {
      awaitInitialLoadingAndApiCall(fingerprintResetF8eClientFake, expectedEnv, expectedAccountId)

      progressThroughFinishFlowToGrantLoading(fingerprintResetF8eClientFake)

      // NFC cancellation creates error state
      handleNfcCancellation(nfcSessionUiStateMachine)

      // Click cancel (secondary button) instead of retry
      verifyErrorScreenAndCancel()

      // Should call onCancel to exit the entire flow
      onCancelCalls.awaitItem()

      // Consume the additional getPrivilegedActionInstances call that happens after completion
      fingerprintResetF8eClientFake.getPrivilegedActionInstancesCalls.awaitItem()
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

private fun createFingerprintResetResponse() =
  FingerprintResetResponse(
    version = 1,
    serializedRequest = "test".toByteArray().encodeBase64(),
    signature = "test".toByteArray().toByteString().hex()
  )

private suspend fun StateMachineTester<FingerprintResetProps, ScreenModel>.awaitInitialLoadingAndApiCall(
  fingerprintResetF8eClientFake: FingerprintResetF8eClientFake,
  expectedEnv: F8eEnvironment,
  expectedAccountId: AccountId,
) {
  awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()
  fingerprintResetF8eClientFake.getPrivilegedActionInstancesCalls.awaitItem()
    .shouldBe(Pair(expectedEnv, expectedAccountId))
  fingerprintResetF8eClientFake.getPrivilegedActionInstancesCalls.expectNoEvents()
}

private suspend fun StateMachineTester<FingerprintResetProps, ScreenModel>.progressThroughConfirmationToNfc() {
  awaitBody<FingerprintResetConfirmationBodyModel> {
    primaryButton.shouldNotBeNull().onClick()
  }
  awaitSheet<FingerprintResetConfirmationSheetModel> {
    primaryButton.shouldNotBeNull().onClick()
  }
}

private suspend fun StateMachineTester<FingerprintResetProps, ScreenModel>.progressThroughFinishFlowToGrantLoading(
  fingerprintResetF8eClientFake: FingerprintResetF8eClientFake,
) {
  awaitBody<FinishFingerprintResetBodyModel> {
    primaryButton.shouldNotBeNull().onClick()
  }
  awaitSheet<FingerprintResetConfirmationSheetModel> {
    primaryButton.shouldNotBeNull().onClick()
  }
  awaitUntilBody<LoadingSuccessBodyModel> {
    id.shouldBe(FingerprintResetEventTrackerScreenId.LOADING_GRANT)
  }
  fingerprintResetF8eClientFake.continuePrivilegedActionCalls.awaitItem()
}

private suspend fun StateMachineTester<FingerprintResetProps, ScreenModel>.handleNfcError(
  nfcSessionUiStateMachine: ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>,
  errorType: FingerprintResetNfcResult = FingerprintResetNfcResult.ProvideGrantFailed,
) {
  awaitBodyMock<NfcSessionUIStateMachineProps<FingerprintResetNfcResult>>(id = nfcSessionUiStateMachine.id) {
    eventTrackerContext.shouldBe(NfcEventTrackerScreenIdContext.RESET_FINGERPRINTS_PROVIDE_SIGNED_GRANT)
    onSuccess(errorType)
  }
}

private suspend fun StateMachineTester<FingerprintResetProps, ScreenModel>.handleNfcCancellation(
  nfcSessionUiStateMachine: ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>,
) {
  awaitBodyMock<NfcSessionUIStateMachineProps<FingerprintResetNfcResult>>(id = nfcSessionUiStateMachine.id) {
    eventTrackerContext.shouldBe(NfcEventTrackerScreenIdContext.RESET_FINGERPRINTS_PROVIDE_SIGNED_GRANT)
    onCancel()
  }
}

private suspend fun StateMachineTester<FingerprintResetProps, ScreenModel>.verifyErrorScreenAndRetry() {
  val errorScreen = awaitItem()
  val errorBody = errorScreen.body.shouldBeInstanceOf<FormBodyModel>()
  errorBody.header.shouldNotBeNull().apply {
    headline.shouldBe("NFC Error")
    sublineModel.shouldNotBeNull().string.shouldBe("There was an issue communicating with your hardware. Please try again.")
  }
  errorBody.primaryButton.shouldNotBeNull().onClick()
}

private suspend fun StateMachineTester<FingerprintResetProps, ScreenModel>.verifyErrorScreenAndCancel() {
  val errorScreen = awaitItem()
  val errorBody = errorScreen.body.shouldBeInstanceOf<FormBodyModel>()
  errorBody.secondaryButton.shouldNotBeNull().onClick()
}

private suspend fun StateMachineTester<FingerprintResetProps, ScreenModel>.completeNfcGrantSuccessfully(
  nfcSessionUiStateMachine: ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>,
) {
  awaitBodyMock<NfcSessionUIStateMachineProps<FingerprintResetNfcResult>>(id = nfcSessionUiStateMachine.id) {
    eventTrackerContext.shouldBe(NfcEventTrackerScreenIdContext.RESET_FINGERPRINTS_PROVIDE_SIGNED_GRANT)
    onSuccess(FingerprintResetNfcResult.ProvideGrantSuccess)
  }
}

private suspend fun StateMachineTester<FingerprintResetProps, ScreenModel>.completeFingerprintEnrollment() {
  awaitBodyMock<EnrollingFingerprintProps> {
    fingerprintHandle.shouldBe(FingerprintHandle(index = 0, label = ""))
    context.shouldBe(EnrollmentContext.FingerprintReset)
    onSuccess(
      EnrolledFingerprints(
        fingerprintHandles = immutableListOf(FingerprintHandle(index = 0, label = ""))
      )
    )
  }
}
