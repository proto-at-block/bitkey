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
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.SignatureUtilsMock
import build.wallet.firmware.FirmwareFeatureFlag
import build.wallet.firmware.FirmwareFeatureFlagCfg
import build.wallet.grants.GrantAction
import build.wallet.grants.GrantRequest
import build.wallet.ktor.result.EmptyResponseBody
import build.wallet.nfc.NfcCommandsMock
import build.wallet.nfc.NfcSessionFake
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintEnrolled
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareProps
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachine
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.recovery.inprogress.waiting.AppDelayNotifyInProgressBodyModel
import build.wallet.statemachine.root.RemainingRecoveryDelayWordsUpdateFrequency
import build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints.FinishResetFingerprintsBodyModel
import build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints.ResetFingerprintsConfirmationBodyModel
import build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints.ResetFingerprintsConfirmationSheetModel
import build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints.ResetFingerprintsEventTrackerScreenId
import build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints.ResetFingerprintsNfcResult
import build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints.ResetFingerprintsProps
import build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints.ResetFingerprintsUiStateMachineImpl
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
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.util.encodeBase64
import io.ktor.utils.io.core.toByteArray
import okio.ByteString.Companion.EMPTY
import okio.ByteString.Companion.decodeHex
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

class ResetFingerprintsUiStateMachineImplTests : FunSpec({

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

  val pairNewHardwareUiStateMachine =
    object : PairNewHardwareUiStateMachine,
      ScreenStateMachineMock<PairNewHardwareProps>(
        id = "pair-new-hardware"
      ) {}

  val stateMachine = ResetFingerprintsUiStateMachineImpl(
    nfcSessionUIStateMachine = nfcSessionUiStateMachine,
    clock = clock,
    durationFormatter = DurationFormatterFake(),
    fingerprintResetService = fingerprintResetService,
    remainingRecoveryDelayWordsUpdateFrequency = RemainingRecoveryDelayWordsUpdateFrequency(1.milliseconds),
    pairNewHardwareUiStateMachine = pairNewHardwareUiStateMachine,
    metricTrackerService = metricTrackerService
  )

  val onCompleteCalls = turbines.create<Unit>("onComplete calls")
  val onCancelCalls = turbines.create<Unit>("onCancel calls")
  val onFwUpRequiredCalls = turbines.create<Unit>("onFwUpRequired calls")

  val props = ResetFingerprintsProps(
    onComplete = { onCompleteCalls += Unit },
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

      awaitBody<ResetFingerprintsConfirmationBodyModel> {
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

      awaitBody<ResetFingerprintsConfirmationBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitSheet<ResetFingerprintsConfirmationSheetModel> {
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

      awaitBody<ResetFingerprintsConfirmationBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitSheet<ResetFingerprintsConfirmationSheetModel> {
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

      awaitBody<ResetFingerprintsConfirmationBodyModel> { primaryButton!!.onClick() }
      awaitSheet<ResetFingerprintsConfirmationSheetModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<ResetFingerprintsNfcResult>>(id = nfcSessionUiStateMachine.id) {
        val mockGrantRequest = GrantRequest(
          version = 1.toByte(),
          deviceId = "mockDevice".toByteArray(),
          challenge = "mockChallenge".toByteArray(),
          action = GrantAction.FINGERPRINT_RESET,
          signature = "21a1aa12efc8512727856a9ccc428a511cf08b211f26551781ae0a37661de8060c566ded9486500f6927e9c9df620c65653c68316e61930a49ecab31b3bec498".decodeHex()
            .toByteArray()
        )
        val sessionResult = session(NfcSessionFake(), nfcCommandsMock)
        sessionResult shouldBe ResetFingerprintsNfcResult.GrantRequestRetrieved(mockGrantRequest)

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
    val delayStartTime = clock.now - 1.days
    val delayEndTime = clock.now + 3.days

    val pendingActionInstance = PrivilegedActionInstance(
      id = "pending-action-123",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayStartTime = delayStartTime,
        delayEndTime = delayEndTime,
        cancellationToken = "test-cancel-token",
        completionToken = "test-complete-token"
      )
    )

    fingerprintResetF8eClientFake.getPrivilegedActionInstancesResult =
      Ok(listOf(pendingActionInstance))

    stateMachine.test(props) {
      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()

      fingerprintResetF8eClientFake.getPrivilegedActionInstancesCalls.awaitItem()
        .shouldBe(Pair(expectedEnv, expectedAccountId))
      fingerprintResetF8eClientFake.getPrivilegedActionInstancesCalls.expectNoEvents()

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

      awaitBody<ResetFingerprintsConfirmationBodyModel> {
        val accessory =
          toolbar?.leadingAccessory.shouldBeInstanceOf<ToolbarAccessoryModel.IconAccessory>()
        accessory.model.onClick?.invoke()
      }

      onCancelCalls.awaitItem()
    }
  }

  test("cancelling reset from progress screen calls onCancel") {
    val delayStartTime = clock.now - 1.days
    val delayEndTime = clock.now + 3.days
    val cancellationToken = "test-cancel-token"

    val pendingActionInstance = PrivilegedActionInstance(
      id = "pending-action-123",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayStartTime = delayStartTime,
        delayEndTime = delayEndTime,
        cancellationToken = cancellationToken,
        completionToken = "test-complete-token"
      )
    )

    fingerprintResetF8eClientFake.getPrivilegedActionInstancesResult =
      Ok(listOf(pendingActionInstance))
    fingerprintResetF8eClientFake.cancelFingerprintResetResult = Ok(EmptyResponseBody)

    stateMachine.test(props) {
      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()

      fingerprintResetF8eClientFake.getPrivilegedActionInstancesCalls.awaitItem()

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

      awaitUntilBody<FinishResetFingerprintsBodyModel> {
        onCancelReset()
      }

      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()
      fingerprintResetF8eClientFake.cancelFingerprintResetCalls.awaitItem()

      onCancelCalls.awaitItem()
    }
  }

  test("completing reset from finish screen calls onComplete") {
    val delayStartTime = clock.now - 3.days
    val delayEndTime = clock.now - 1.days

    val pendingActionInstance = PrivilegedActionInstance(
      id = "pending-action-123",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayStartTime = delayStartTime,
        delayEndTime = delayEndTime,
        cancellationToken = "test-cancel-token",
        completionToken = "test-complete-token"
      )
    )

    fingerprintResetF8eClientFake.getPrivilegedActionInstancesResult =
      Ok(listOf(pendingActionInstance))
    fingerprintResetF8eClientFake.continuePrivilegedActionResult = Ok(
      FingerprintResetResponse(
        version = 1,
        serializedRequest = "test".toByteArray().encodeBase64(),
        signature = "test".toByteArray().toHexString()
      )
    )

    stateMachine.test(props) {
      awaitItem().body.shouldBeInstanceOf<LoadingSuccessBodyModel>()

      fingerprintResetF8eClientFake.getPrivilegedActionInstancesCalls.awaitItem()
        .shouldBe(Pair(expectedEnv, expectedAccountId))
      fingerprintResetF8eClientFake.getPrivilegedActionInstancesCalls.expectNoEvents()

      awaitBody<FinishResetFingerprintsBodyModel> {
        header.shouldNotBeNull()
          .headline.shouldBe("Finish fingerprint reset")
        primaryButton.shouldNotBeNull().onClick()
      }

      // Confirm on the bottom sheet
      awaitSheet<ResetFingerprintsConfirmationSheetModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitBody<LoadingSuccessBodyModel> {
        id.shouldBe(ResetFingerprintsEventTrackerScreenId.LOADING_GRANT)
      }

      fingerprintResetF8eClientFake.continuePrivilegedActionCalls.awaitItem()
      fingerprintResetF8eClientFake.continuePrivilegedActionCalls.expectNoEvents()

      // Provide grant
      awaitBodyMock<NfcSessionUIStateMachineProps<ResetFingerprintsNfcResult>>(id = nfcSessionUiStateMachine.id) {
        eventTrackerContext.shouldBe(NfcEventTrackerScreenIdContext.RESET_FINGERPRINTS_PROVIDE_SIGNED_GRANT)
        onSuccess(ResetFingerprintsNfcResult.ProvideGrantSuccess)
      }

      // Reset fingerprints
      awaitBodyMock<PairNewHardwareProps> {
        request
          .shouldBeTypeOf<PairNewHardwareProps.Request.Ready>()

        request.onSuccess(
          FingerprintEnrolled(
            appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
            keyBundle = HwKeyBundleMock,
            sealedCsek = EMPTY,
            sealedSsek = EMPTY,
            serial = "serial"
          )
        )
      }

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

      awaitBody<ResetFingerprintsConfirmationBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitSheet<ResetFingerprintsConfirmationSheetModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      // NFC session should return FwUpRequired and immediately call onFwUpRequired
      awaitBodyMock<NfcSessionUIStateMachineProps<ResetFingerprintsNfcResult>>(id = nfcSessionUiStateMachine.id) {
        val sessionResult = session(NfcSessionFake(), nfcCommandsMock)
        sessionResult shouldBe ResetFingerprintsNfcResult.FwUpRequired

        onSuccess(sessionResult)
      }

      onFwUpRequiredCalls.awaitItem()
    }
  }
})
