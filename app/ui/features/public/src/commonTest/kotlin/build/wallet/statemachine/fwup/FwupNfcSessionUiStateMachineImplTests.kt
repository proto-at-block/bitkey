package build.wallet.statemachine.fwup

import bitkey.account.AccountConfigServiceFake
import bitkey.account.HardwareType
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.events.screen.context.FwupMcuEventTrackerContext
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.FWUP
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId.NFC_DETECTED
import build.wallet.analytics.v1.Action.ACTION_APP_FWUP_COMPLETE
import build.wallet.analytics.v1.Action.ACTION_APP_FWUP_MCU_UPDATE_FAILED
import build.wallet.analytics.v1.Action.ACTION_APP_FWUP_MCU_UPDATE_STARTED
import build.wallet.analytics.v1.Action.ACTION_APP_SCREEN_IMPRESSION
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.SignatureVerifierMock
import build.wallet.encrypt.SignatureVerifierMock.VerifyEcdsaCall
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue.DoubleFlag
import build.wallet.feature.flags.FwupNfcBackgroundRetryStartupRevealDelayMsFeatureFlag
import build.wallet.feature.flags.FwupNfcCooldownPeriodSecondsFeatureFlag
import build.wallet.feature.flags.NfcSessionRetryAttemptsFeatureFlag
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.fwup.*
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.PendingUpdate
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.nfc.*
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.nfc.platform.NfcCommands
import build.wallet.platform.device.DevicePlatform
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.test
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.fwup.FwupNfcBodyModel.Status.*
import build.wallet.statemachine.nfc.HardwareConfirmationResultBodyModel
import build.wallet.statemachine.platform.nfc.EnableNfcNavigatorMock
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationContent
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationUiProps
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import app.cash.turbine.Turbine
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.CompletableDeferred

class FwupNfcSessionUiStateMachineImplTests : FunSpec({

  val eventTracker = EventTrackerMock(turbines::create)
  val deviceInfoProvider = DeviceInfoProviderMock()
  val nfcTransactor = NfcTransactorMock(turbines::create)
  val firmwareDataService = FirmwareDataServiceFake()
  val accountConfigService = AccountConfigServiceFake()
  val fwupDataDao = FwupDataDaoMock(turbines::create)
  val signatureVerifierTurbine = turbines.create<VerifyEcdsaCall>("verifyEcdsa calls")
  val keyboxDao = KeyboxDaoMock(turbines::create)
  val featureFlagDao = FeatureFlagDaoFake()
  val nfcSessionRetryAttemptsFeatureFlag = NfcSessionRetryAttemptsFeatureFlag(featureFlagDao)
  val fwupNfcCooldownPeriodSecondsFeatureFlag =
    FwupNfcCooldownPeriodSecondsFeatureFlag(featureFlagDao)
  val fwupNfcBackgroundRetryStartupRevealDelayMsFeatureFlag =
    FwupNfcBackgroundRetryStartupRevealDelayMsFeatureFlag(featureFlagDao)
  val hardwareConfirmationUiStateMachine =
    object : HardwareConfirmationUiStateMachine,
      ScreenStateMachineMock<HardwareConfirmationUiProps>("hardware-confirmation") {}

  val stateMachine =
    FwupNfcSessionUiStateMachineImpl(
      enableNfcNavigator = EnableNfcNavigatorMock(),
      eventTracker = eventTracker,
      fwupProgressCalculator = FwupProgressCalculatorMock(),
      deviceInfoProvider = deviceInfoProvider,
      nfcReaderCapability = NfcReaderCapabilityMock(),
      nfcTransactor = nfcTransactor,
      fwupDataDao = fwupDataDao,
      firmwareDataService = firmwareDataService,
      accountConfigService = accountConfigService,
      keyboxDao = keyboxDao,
      signatureVerifier = SignatureVerifierMock(signatureVerifierTurbine),
      nfcSessionRetryAttemptsFeatureFlag = nfcSessionRetryAttemptsFeatureFlag,
      fwupNfcCooldownPeriodSecondsFeatureFlag = fwupNfcCooldownPeriodSecondsFeatureFlag,
      fwupNfcBackgroundRetryStartupRevealDelayMsFeatureFlag =
        fwupNfcBackgroundRetryStartupRevealDelayMsFeatureFlag,
      hardwareConfirmationUiStateMachine = hardwareConfirmationUiStateMachine
    )

  /**
   * Most tests use the shared [stateMachine] with [NfcTransactorMock]. This helper exists for the
   * small set of cases that need to inject a custom transactor, such as executing the real FWUP
   * transaction block to drive the iOS cooldown path after FWUP has already started.
   */
  fun createStateMachineWithTransactor(nfcTransactor: NfcTransactor) =
    FwupNfcSessionUiStateMachineImpl(
      enableNfcNavigator = EnableNfcNavigatorMock(),
      eventTracker = eventTracker,
      fwupProgressCalculator = FwupProgressCalculatorMock(),
      deviceInfoProvider = deviceInfoProvider,
      nfcReaderCapability = NfcReaderCapabilityMock(),
      nfcTransactor = nfcTransactor,
      fwupDataDao = fwupDataDao,
      firmwareDataService = firmwareDataService,
      accountConfigService = accountConfigService,
      keyboxDao = keyboxDao,
      signatureVerifier = SignatureVerifierMock(signatureVerifierTurbine),
      nfcSessionRetryAttemptsFeatureFlag = nfcSessionRetryAttemptsFeatureFlag,
      fwupNfcCooldownPeriodSecondsFeatureFlag = fwupNfcCooldownPeriodSecondsFeatureFlag,
      fwupNfcBackgroundRetryStartupRevealDelayMsFeatureFlag =
        fwupNfcBackgroundRetryStartupRevealDelayMsFeatureFlag,
      hardwareConfirmationUiStateMachine = hardwareConfirmationUiStateMachine
    )

  val onBackCalls = turbines.create<Unit>("onBack calls")
  val onDoneCalls = turbines.create<Unit>("onDone calls")
  val onErrorCalls = turbines.create<NfcException>("onError calls")

  val props =
    FwupNfcSessionUiProps(
      onBack = { onBackCalls.add(Unit) },
      onDone = { onDoneCalls.add(Unit) },
      transactionType = FwupTransactionType.StartFromBeginning(),
      onError = { error, _, _ -> onErrorCalls.add(error) }
    )
  val w3ResolvedDeviceInfo =
    FirmwareDeviceInfoMock.copy(hwRevision = "w3a-core-evt", serial = "w3-confirmation-serial")

  beforeTest {
    featureFlagDao.reset()
    accountConfigService.reset()
    deviceInfoProvider.reset()
    nfcTransactor.reset()
    keyboxDao.reset()
    firmwareDataService.firmwareData.value = FirmwareDataPendingUpdateMock.copy(
      firmwareUpdateState = PendingUpdate(immutableListOf(McuFwupDataMock_W1_CORE))
    )
  }

  test("happy path") {
    nfcTransactor.transactResult = Ok(FwupTransactionResult.Completed)
    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.text.shouldBe("Hold your Bitkey to the back of your phone")
        status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()

      awaitBody<FwupNfcBodyModel> {
        status.text.shouldBe("Successfully updated")
        status.shouldBeTypeOf<Success>()
        onCancel.shouldBeNull()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_FWUP_COMPLETE))
      firmwareDataService.firmwareData.value.shouldBe(
        FirmwareDataUpToDateMock.copy(
          firmwareDeviceInfo = FirmwareDeviceInfoMock.copy(
            version = McuFwupDataMock_W1_CORE.version
          )
        )
      )
      onDoneCalls.awaitItem()
    }
  }

  test("FWUP progress screens use platform NFC screens with design system v2") {
    nfcTransactor.transactResult = Ok(FwupTransactionResult.Completed)

    stateMachine.test(props) {
      awaitItem().apply {
        platformNfcScreen.shouldBe(true)
        presentationStyle.shouldBe(ScreenPresentationStyle.ModalFullScreen)
        themePreference.shouldBe(ThemePreference.Manual(Theme.DARK))
        body.shouldBeTypeOf<FwupNfcBodyModel>().status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()
      awaitItem()
      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_FWUP_COMPLETE))
      onDoneCalls.awaitItem()
    }
  }

  test("in progress cancel") {
    nfcTransactor.transactResult = Ok(FwupTransactionResult.Completed)
    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.text.shouldBe("Hold your Bitkey to the back of your phone")
        status.shouldBeTypeOf<Searching>()
        onCancel.shouldNotBeNull().invoke()
      }

      nfcTransactor.transactCalls.awaitItem()

      // TODO(W-4584): Make testing this better simulate reality where [NfcTransactor] wouldn't
      // have responded in this case.
      awaitBody<FwupNfcBodyModel>()
      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_FWUP_COMPLETE))
      onDoneCalls.awaitItem()

      onBackCalls.awaitItem()
    }
  }

  test("onTagConnected") {
    val transactionResult = CompletableDeferred<Result<FwupTransactionResult, NfcException>>()
    val pendingTransactor =
      DeferredResultNfcTransactor(
        result = transactionResult,
        turbineName = "deferred transact calls - onTagConnected",
        turbineFactory = turbines::create
      )

    createStateMachineWithTransactor(pendingTransactor).test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.text.shouldBe("Hold your Bitkey to the back of your phone")
        status.shouldBeTypeOf<Searching>()
      }

      val transactCalls =
        pendingTransactor.transactCalls.awaitItem()
          .shouldBeTypeOf<NfcSession.Parameters>()

      transactCalls.onTagConnected(NfcSessionFake())
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_SCREEN_IMPRESSION, NFC_DETECTED, FWUP)
      )

      awaitBody<FwupNfcBodyModel> {
        status.text.shouldBe("Updating...")
        status.shouldBeTypeOf<InProgress>()
      }

      transactionResult.complete(Ok(FwupTransactionResult.Completed))
      awaitBody<FwupNfcBodyModel>()
      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_FWUP_COMPLETE))
      onDoneCalls.awaitItem()
    }
  }

  test("onTagDisconnected") {
    val transactionResult = CompletableDeferred<Result<FwupTransactionResult, NfcException>>()
    val pendingTransactor =
      DeferredResultNfcTransactor(
        result = transactionResult,
        turbineName = "deferred transact calls - onTagDisconnected",
        turbineFactory = turbines::create
      )

    createStateMachineWithTransactor(pendingTransactor).test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.text.shouldBe("Hold your Bitkey to the back of your phone")
        status.shouldBeTypeOf<Searching>()
      }

      val transactCalls =
        pendingTransactor.transactCalls.awaitItem()
          .shouldBeTypeOf<NfcSession.Parameters>()

      transactCalls.onTagDisconnected()
      awaitBody<FwupNfcBodyModel> {
        status.text.shouldBe("Device no longer detected,\nhold device to phone")
        status.shouldBeTypeOf<LostConnection>()
      }

      transactionResult.complete(Ok(FwupTransactionResult.Completed))
      awaitBody<FwupNfcBodyModel>()
      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_FWUP_COMPLETE))
      onDoneCalls.awaitItem()
    }
  }

  test("failure - user cancellation") {
    nfcTransactor.transactResult = Err(NfcException.IOSOnly.UserCancellation())
    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.text.shouldBe("Hold your Bitkey to the back of your phone")
        status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()

      onBackCalls.awaitItem()
    }
  }

  test("failure - other") {
    nfcTransactor.transactResult = Err(NfcException.CommandError())
    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.text.shouldBe("Hold your Bitkey to the back of your phone")
        status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()

      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_FWUP_MCU_UPDATE_FAILED, context = FwupMcuEventTrackerContext.CORE)
      )
      onErrorCalls.awaitItem().shouldBeTypeOf<NfcException.CommandError>()
    }
  }

  test("failure - no session while fwup is in progress on iOS shows cooldown") {
    deviceInfoProvider.devicePlatformValue = DevicePlatform.IOS
    val commandsMock = NfcCommandsMock { name -> turbines.create("$name no-session-cooldown") }
    val failingCommands =
      object : NfcCommands by commandsMock {
        override suspend fun fwupTransfer(
          session: NfcSession,
          sequenceId: UInt,
          fwupData: List<UByte>,
          offset: UInt,
          fwupMode: FwupMode,
          mcuRole: build.wallet.firmware.McuRole,
        ): Boolean {
          throw NfcException.IOSOnly.NoSession()
        }
      }

    createStateMachineWithTransactor(
      ExecutingNfcTransactor(commandsPerCall = listOf(failingCommands))
    ).test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      awaitUntilBody<FwupNfcCooldownModel> {
        remainingSeconds.shouldBe(8)
        onContinue.shouldBeNull()
        isStartingSession.shouldBe(false)
      }

      commandsMock.getDeviceInfoCalls.awaitItem().shouldBe(FirmwareDeviceInfoMock)
      fwupDataDao.setMcuSequenceIdCalls.awaitItem().shouldBe(build.wallet.firmware.McuRole.CORE to 0u)
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_FWUP_MCU_UPDATE_STARTED, context = FwupMcuEventTrackerContext.CORE)
      )
      onErrorCalls.expectNoEvents()
    }
  }

  test("failure - session invalidated while fwup is in progress on iOS shows cooldown") {
    deviceInfoProvider.devicePlatformValue = DevicePlatform.IOS
    val commandsMock = NfcCommandsMock { name -> turbines.create("$name session-invalidated-cooldown") }
    val failingCommands =
      object : NfcCommands by commandsMock {
        override suspend fun fwupTransfer(
          session: NfcSession,
          sequenceId: UInt,
          fwupData: List<UByte>,
          offset: UInt,
          fwupMode: FwupMode,
          mcuRole: build.wallet.firmware.McuRole,
        ): Boolean {
          throw NfcException.CanBeRetried.SessionInvalidated()
        }
      }

    createStateMachineWithTransactor(
      ExecutingNfcTransactor(commandsPerCall = listOf(failingCommands))
    ).test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      awaitUntilBody<FwupNfcCooldownModel> {
        remainingSeconds.shouldBe(8)
        onContinue.shouldBeNull()
        isStartingSession.shouldBe(false)
      }

      commandsMock.getDeviceInfoCalls.awaitItem().shouldBe(FirmwareDeviceInfoMock)
      fwupDataDao.setMcuSequenceIdCalls.awaitItem().shouldBe(build.wallet.firmware.McuRole.CORE to 0u)
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_FWUP_MCU_UPDATE_STARTED, context = FwupMcuEventTrackerContext.CORE)
      )
      onErrorCalls.expectNoEvents()
    }
  }

  test("failure - no session cooldown uses configured feature flag seconds") {
    deviceInfoProvider.devicePlatformValue = DevicePlatform.IOS
    fwupNfcCooldownPeriodSecondsFeatureFlag.setFlagValue(DoubleFlag(3.0))

    val commandsMock = NfcCommandsMock { name -> turbines.create("$name cooldown-custom-seconds") }
    val failingCommands =
      object : NfcCommands by commandsMock {
        override suspend fun fwupTransfer(
          session: NfcSession,
          sequenceId: UInt,
          fwupData: List<UByte>,
          offset: UInt,
          fwupMode: FwupMode,
          mcuRole: build.wallet.firmware.McuRole,
        ): Boolean {
          throw NfcException.IOSOnly.NoSession()
        }
      }

    createStateMachineWithTransactor(
      ExecutingNfcTransactor(commandsPerCall = listOf(failingCommands))
    ).test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      awaitUntilBody<FwupNfcCooldownModel> {
        remainingSeconds.shouldBe(3)
        onContinue.shouldBeNull()
        isStartingSession.shouldBe(false)
      }

      commandsMock.getDeviceInfoCalls.awaitItem().shouldBe(FirmwareDeviceInfoMock)
      fwupDataDao.setMcuSequenceIdCalls.awaitItem().shouldBe(build.wallet.firmware.McuRole.CORE to 0u)
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_FWUP_MCU_UPDATE_STARTED, context = FwupMcuEventTrackerContext.CORE)
      )
      onErrorCalls.expectNoEvents()
    }
  }

  test("failure - timeout does not show cooldown") {
    deviceInfoProvider.devicePlatformValue = DevicePlatform.IOS
    nfcTransactor.transactResult = Err(NfcException.Timeout())

    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()

      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_FWUP_MCU_UPDATE_FAILED, context = FwupMcuEventTrackerContext.CORE)
      )
      onErrorCalls.awaitItem().shouldBeTypeOf<NfcException.Timeout>()
    }
  }

  test("failure - can be retried does not show cooldown") {
    deviceInfoProvider.devicePlatformValue = DevicePlatform.IOS
    nfcTransactor.transactResult = Err(NfcException.CanBeRetried.TagLost())

    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()

      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_FWUP_MCU_UPDATE_FAILED, context = FwupMcuEventTrackerContext.CORE)
      )
      onErrorCalls.awaitItem().shouldBeTypeOf<NfcException.CanBeRetried.TagLost>()
    }
  }

  test("cooldown continue starts background retry startup then reveals searching") {
    deviceInfoProvider.devicePlatformValue = DevicePlatform.IOS
    val startupGate = CompletableDeferred<Unit>()
    val commandsMock = NfcCommandsMock { name -> turbines.create("$name cooldown-continue") }
    var getDeviceInfoCallCount = 0
    var shouldFailTransfer = true
    val failingCommands =
      object : NfcCommands by commandsMock {
        override suspend fun getDeviceInfo(session: NfcSession): build.wallet.firmware.FirmwareDeviceInfo {
          getDeviceInfoCallCount += 1
          val deviceInfo = commandsMock.getDeviceInfo(session)
          if (getDeviceInfoCallCount > 1) {
            startupGate.await()
          }
          return deviceInfo
        }

        override suspend fun fwupTransfer(
          session: NfcSession,
          sequenceId: UInt,
          fwupData: List<UByte>,
          offset: UInt,
          fwupMode: FwupMode,
          mcuRole: build.wallet.firmware.McuRole,
        ): Boolean {
          if (shouldFailTransfer) {
            shouldFailTransfer = false
            throw NfcException.IOSOnly.NoSession()
          }
          return commandsMock.fwupTransfer(session, sequenceId, fwupData, offset, fwupMode, mcuRole)
        }
      }

    createStateMachineWithTransactor(
      ExecutingNfcTransactor(commandsPerCall = listOf(failingCommands, failingCommands))
    ).testWithVirtualTime(props) {
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      val cooldownModel =
        awaitUntilBody<FwupNfcCooldownModel>(
          matching = { body -> body.remainingSeconds == 0 && body.onContinue != null }
        )
      cooldownModel.isStartingSession.shouldBe(false)
      cooldownModel.onContinue.shouldNotBeNull().invoke()

      commandsMock.getDeviceInfoCalls.awaitItem().shouldBe(FirmwareDeviceInfoMock)
      fwupDataDao.setMcuSequenceIdCalls.awaitItem().shouldBe(build.wallet.firmware.McuRole.CORE to 0u)
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_FWUP_MCU_UPDATE_STARTED, context = FwupMcuEventTrackerContext.CORE)
      )

      awaitBody<FwupNfcCooldownModel> {
        isStartingSession.shouldBe(true)
        remainingSeconds.shouldBe(0)
        onContinue.shouldBeNull()
      }

      commandsMock.getDeviceInfoCalls.awaitItem().shouldBe(FirmwareDeviceInfoMock)
      awaitUntilBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }
    }
  }

  // W3 Two-Tap Confirmation Flow Tests

  test("W3 confirmation flow - shows hardware confirmation screen") {
    // Configure as W3 hardware
    accountConfigService.setHardwareType(HardwareType.W3)

    // Simulate W3 two-tap flow by returning RequiresConfirmation with a mock fetchResult
    val mockFetchResult: suspend (NfcSession, NfcCommands) -> HardwareInteraction<Boolean> =
      { _, _ -> HardwareInteraction.Completed(true) }

    nfcTransactor.transactResult = Ok(
      FwupTransactionResult.RequiresConfirmation(
        fetchResult = mockFetchResult,
        resolvedDeviceInfo = w3ResolvedDeviceInfo
      )
    )
    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      val initialParams = nfcTransactor.transactCalls.awaitItem()
        .shouldBeTypeOf<NfcSession.Parameters>()
      initialParams.shouldLock.shouldBe(true)
      initialParams.resolvedDeviceInfoOverride.shouldBeNull()

      // Verify the HardwareConfirmationUiStateMachine is shown (via BodyModelMock)
      awaitBodyMock<HardwareConfirmationUiProps>(id = "hardware-confirmation") {
        // Verify the props are correctly wired
        onBack.shouldNotBeNull()
        onConfirm.shouldNotBeNull()
        content.shouldBe(HardwareConfirmationContent.FirmwareUpdate)
      }
    }
  }

  test("W3 confirmation flow - onConfirm starts new NFC session for continuation") {
    // Configure as W3 hardware
    accountConfigService.setHardwareType(HardwareType.W3)

    val mockFetchResult: suspend (NfcSession, NfcCommands) -> HardwareInteraction<Boolean> =
      { _, _ -> HardwareInteraction.Completed(true) }

    nfcTransactor.transactResult = Ok(
      FwupTransactionResult.RequiresConfirmation(
        fetchResult = mockFetchResult,
        resolvedDeviceInfo = w3ResolvedDeviceInfo
      )
    )
    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      val initialParams = nfcTransactor.transactCalls.awaitItem()
        .shouldBeTypeOf<NfcSession.Parameters>()
      initialParams.shouldLock.shouldBe(true)
      initialParams.resolvedDeviceInfoOverride.shouldBeNull()

      // Get the confirmation screen and invoke onConfirm
      awaitBodyMock<HardwareConfirmationUiProps>(id = "hardware-confirmation") {
        onConfirm()
      }

      // After confirmation, should transition back to NFC session (searching state)
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      // Second tap: shouldLock = true (continuation transaction)
      val continuationParams = nfcTransactor.transactCalls.awaitItem()
        .shouldBeTypeOf<NfcSession.Parameters>()
      continuationParams.shouldLock.shouldBe(true)
      continuationParams.nfcFlowName.shouldBe("fwup-confirmation")
      continuationParams.resolvedDeviceInfoOverride.shouldBe(w3ResolvedDeviceInfo)

      // The new transaction also completes (with RequiresConfirmation again since mock
      // is still set to that), emitting another confirmation screen
      awaitBodyMock<HardwareConfirmationUiProps>(id = "hardware-confirmation")
    }
  }

  test("W3 continuation path - succeeds and emits ACTION_APP_FWUP_MCU_UPDATE_COMPLETE context via two-tap flow") {
    // Configure as W3 hardware with W1-style single MCU (to keep the test simple — continuation
    // path itself does not depend on W3 multi-MCU sequencing).
    accountConfigService.setHardwareType(HardwareType.W3)

    val mockFetchResult: suspend (NfcSession, NfcCommands) -> HardwareInteraction<Boolean> =
      { _, _ -> HardwareInteraction.Completed(true) }

    // First transact: firmware requires hold-to-confirm before it can proceed
    // Second transact (continuation): firmware completes successfully
    nfcTransactor.queueTransactResults(
      listOf(
        Ok(
          FwupTransactionResult.RequiresConfirmation(
            fetchResult = mockFetchResult,
            resolvedDeviceInfo = w3ResolvedDeviceInfo
          )
        ),
        Ok(FwupTransactionResult.Completed)
      )
    )

    stateMachine.test(props) {
      // Initial searching state
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      // First NFC tap returns RequiresConfirmation → hardware confirmation screen is shown
      nfcTransactor.transactCalls.awaitItem()
      awaitBodyMock<HardwareConfirmationUiProps>(id = "hardware-confirmation") {
        // User taps confirm on the hardware screen
        onConfirm()
      }

      // Continuation: a new NFC session starts for the second tap
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }
      val continuationParams = nfcTransactor.transactCalls.awaitItem()
        .shouldBeTypeOf<NfcSession.Parameters>()
      // Continuation sessions use the "fwup-confirmation" flow name
      continuationParams.nfcFlowName.shouldBe("fwup-confirmation")
      continuationParams.resolvedDeviceInfoOverride.shouldBe(w3ResolvedDeviceInfo)

      // Continuation succeeds → success screen
      // Note: ACTION_APP_FWUP_MCU_UPDATE_COMPLETE is tracked inside the NFC transaction
      // lambda (fwupContinuationTransaction). NfcTransactorMock bypasses the lambda, so we
      // assert the end-to-end completion event instead.
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Success>()
      }
      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_FWUP_COMPLETE))
      onDoneCalls.awaitItem()
    }
  }

  test("W3 confirmation flow - onBack cancels the flow") {
    // Configure as W3 hardware
    accountConfigService.setHardwareType(HardwareType.W3)

    val mockFetchResult: suspend (NfcSession, NfcCommands) -> HardwareInteraction<Boolean> =
      { _, _ -> HardwareInteraction.Completed(true) }

    nfcTransactor.transactResult = Ok(
      FwupTransactionResult.RequiresConfirmation(
        fetchResult = mockFetchResult,
        resolvedDeviceInfo = w3ResolvedDeviceInfo
      )
    )
    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      val initialParams = nfcTransactor.transactCalls.awaitItem()
        .shouldBeTypeOf<NfcSession.Parameters>()
      initialParams.shouldLock.shouldBe(true)

      // Get the confirmation screen and invoke onBack
      awaitBodyMock<HardwareConfirmationUiProps>(id = "hardware-confirmation") {
        onBack()
      }

      // onBack should trigger the props.onBack callback
      onBackCalls.awaitItem()
    }
  }

  // W3 Confirmation Pending/Denied Tests

  test("W3 confirmation flow - ConfirmationPending during continuation shows pending screen") {
    accountConfigService.setHardwareType(HardwareType.W3)

    val mockFetchResult: suspend (NfcSession, NfcCommands) -> HardwareInteraction<Boolean> =
      { _, _ -> HardwareInteraction.Completed(true) }

    // First tap returns RequiresConfirmation, second tap returns ConfirmationPending
    nfcTransactor.queueTransactResults(
      listOf(
        Ok(
          FwupTransactionResult.RequiresConfirmation(
            fetchResult = mockFetchResult,
            resolvedDeviceInfo = w3ResolvedDeviceInfo
          )
        ),
        Err(NfcException.ConfirmationPending())
      )
    )

    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      // First tap
      nfcTransactor.transactCalls.awaitItem()

      // Confirmation screen shown
      awaitBodyMock<HardwareConfirmationUiProps>(id = "hardware-confirmation") {
        onConfirm()
      }

      // Second tap starts
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }
      nfcTransactor.transactCalls.awaitItem()

      // ConfirmationPending error → shows pending screen
      awaitBody<HardwareConfirmationResultBodyModel> {
        headline.shouldBe("Review update on Bitkey")
      }
    }
  }

  test("W3 confirmation flow - UserDenied during continuation shows denied screen") {
    accountConfigService.setHardwareType(HardwareType.W3)

    val mockFetchResult: suspend (NfcSession, NfcCommands) -> HardwareInteraction<Boolean> =
      { _, _ -> HardwareInteraction.Completed(true) }

    // First tap returns RequiresConfirmation, second tap returns UserDenied
    nfcTransactor.queueTransactResults(
      listOf(
        Ok(
          FwupTransactionResult.RequiresConfirmation(
            fetchResult = mockFetchResult,
            resolvedDeviceInfo = w3ResolvedDeviceInfo
          )
        ),
        Err(NfcException.UserDenied())
      )
    )

    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      // First tap
      nfcTransactor.transactCalls.awaitItem()

      // Confirmation screen shown
      awaitBodyMock<HardwareConfirmationUiProps>(id = "hardware-confirmation") {
        onConfirm()
      }

      // Second tap starts
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }
      nfcTransactor.transactCalls.awaitItem()

      // UserDenied error → shows denied screen
      awaitBody<HardwareConfirmationResultBodyModel> {
        headline.shouldBe("The update was not confirmed on your Bitkey")
      }
    }
  }

  test("W3 confirmation flow - pending screen acknowledge returns to confirmation") {
    accountConfigService.setHardwareType(HardwareType.W3)

    val mockFetchResult: suspend (NfcSession, NfcCommands) -> HardwareInteraction<Boolean> =
      { _, _ -> HardwareInteraction.Completed(true) }

    // First tap returns RequiresConfirmation, second tap returns ConfirmationPending
    nfcTransactor.queueTransactResults(
      listOf(
        Ok(
          FwupTransactionResult.RequiresConfirmation(
            fetchResult = mockFetchResult,
            resolvedDeviceInfo = w3ResolvedDeviceInfo
          )
        ),
        Err(NfcException.ConfirmationPending())
      )
    )

    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()

      awaitBodyMock<HardwareConfirmationUiProps>(id = "hardware-confirmation") {
        onConfirm()
      }

      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }
      nfcTransactor.transactCalls.awaitItem()

      // Pending screen shown - acknowledge it
      awaitBody<HardwareConfirmationResultBodyModel> {
        headline.shouldBe("Review update on Bitkey")
        onAcknowledge()
      }

      // Should return to hardware confirmation screen
      awaitBodyMock<HardwareConfirmationUiProps>(id = "hardware-confirmation")
    }
  }

  test("W3 confirmation flow - denied screen acknowledge returns to beginning of flow") {
    accountConfigService.setHardwareType(HardwareType.W3)

    val mockFetchResult: suspend (NfcSession, NfcCommands) -> HardwareInteraction<Boolean> =
      { _, _ -> HardwareInteraction.Completed(true) }

    // First tap returns RequiresConfirmation, second tap returns UserDenied,
    // third tap (after restart) succeeds
    nfcTransactor.queueTransactResults(
      listOf(
        Ok(
          FwupTransactionResult.RequiresConfirmation(
            fetchResult = mockFetchResult,
            resolvedDeviceInfo = w3ResolvedDeviceInfo
          )
        ),
        Err(NfcException.UserDenied()),
        Ok(FwupTransactionResult.Completed)
      )
    )

    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()

      awaitBodyMock<HardwareConfirmationUiProps>(id = "hardware-confirmation") {
        onConfirm()
      }

      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }
      nfcTransactor.transactCalls.awaitItem()

      // Denied screen shown - acknowledge it
      awaitBody<HardwareConfirmationResultBodyModel> {
        headline.shouldBe("The update was not confirmed on your Bitkey")
        onAcknowledge()
      }

      // Should return to beginning of flow (fresh NFC session that succeeds)
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }
      nfcTransactor.transactCalls.awaitItem()

      // The restarted flow completes successfully — consume remaining events
      eventTracker.eventCalls.awaitItem()
      onDoneCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  // W3 Sequential MCU Update Tests

  test("W3 sequential update - UXC then CORE completes successfully") {
    // Configure as W3 hardware
    accountConfigService.setHardwareType(HardwareType.W3)
    firmwareDataService.firmwareData.value = FirmwareDataPendingUpdateMock_W3
    nfcTransactor.transactResult = Ok(FwupTransactionResult.Completed)

    stateMachine.test(props) {
      // Initial searching state for UXC (first MCU)
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      // First NFC transaction (UXC MCU) - completes instantly
      nfcTransactor.transactCalls.awaitItem()

      // After first MCU completes, show intermediate screen prompting for next MCU
      awaitBody<FwupNextComponentReadyModel> {
        completedIndex.shouldBe(1)
        totalMcus.shouldBe(2)
        onContinue()
      }

      // Second NFC transaction (CORE MCU) starts after user taps continue
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }
      nfcTransactor.transactCalls.awaitItem()

      // After both MCUs complete, should show success
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Success>()
        status.text.shouldBe("Successfully updated")
      }

      // Verify completion event is tracked (only once for entire flow)
      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_FWUP_COMPLETE))

      // Verify firmware version is updated with W3 CORE version
      firmwareDataService.firmwareData.value.firmwareDeviceInfo?.version.shouldBe(
        McuFwupDataMock_W3_CORE.version
      )

      onDoneCalls.awaitItem()
    }
  }

  test("W3 sequential update - both MCUs are processed in order") {
    // Configure as W3 hardware
    accountConfigService.setHardwareType(HardwareType.W3)
    firmwareDataService.firmwareData.value = FirmwareDataPendingUpdateMock_W3
    nfcTransactor.transactResult = Ok(FwupTransactionResult.Completed)

    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      // First NFC transaction (UXC MCU)
      val uxcParams = nfcTransactor.transactCalls.awaitItem()
        .shouldBeTypeOf<NfcSession.Parameters>()
      uxcParams.nfcFlowName.shouldBe("fwup")

      // After first MCU completes, show intermediate screen prompting for next MCU
      awaitBody<FwupNextComponentReadyModel> {
        onContinue()
      }

      // Second NFC transaction (CORE MCU) starts after user taps continue
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }
      val coreParams = nfcTransactor.transactCalls.awaitItem()
        .shouldBeTypeOf<NfcSession.Parameters>()
      coreParams.nfcFlowName.shouldBe("fwup")

      // After both MCUs complete, success
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Success>()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_FWUP_COMPLETE))
      onDoneCalls.awaitItem()
    }
  }

  // W3 Partial Failure Tests

  test("W3 update - failure during UXC MCU calls onError with UXC context") {
    // Configure as W3 hardware
    accountConfigService.setHardwareType(HardwareType.W3)
    firmwareDataService.firmwareData.value = FirmwareDataPendingUpdateMock_W3
    nfcTransactor.transactResult = Err(NfcException.CommandError())

    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      // UXC transaction fails (first MCU)
      nfcTransactor.transactCalls.awaitItem()

      // Should track failure with UXC context
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_FWUP_MCU_UPDATE_FAILED, context = FwupMcuEventTrackerContext.UXC)
      )

      // Should call onError
      onErrorCalls.awaitItem().shouldBeTypeOf<NfcException.CommandError>()
    }
  }

  // Selected MCU Updates Override Tests

  test("selectedMcuUpdates - uses provided list instead of pending updates from FirmwareDataService") {
    // Set up FirmwareDataService with W3 data (UXC + CORE)
    firmwareDataService.firmwareData.value = FirmwareDataPendingUpdateMock_W3

    // But only select CORE MCU update via selectedMcuUpdates
    val selectedUpdates = immutableListOf(McuFwupDataMock_W3_CORE)
    nfcTransactor.transactResult = Ok(FwupTransactionResult.Completed)

    stateMachine.test(
      props.copy(selectedMcuUpdates = selectedUpdates)
    ) {
      // Should start NFC session immediately with only the CORE MCU
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()

      // Should go directly to success (no intermediate screen since only 1 MCU selected)
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Success>()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_FWUP_COMPLETE))
      onDoneCalls.awaitItem()
    }
  }

  test("W3 update - failure during CORE MCU after UXC succeeds calls onError with CORE context") {
    // Configure as W3 hardware
    accountConfigService.setHardwareType(HardwareType.W3)
    firmwareDataService.firmwareData.value = FirmwareDataPendingUpdateMock_W3

    // Queue results upfront: UXC succeeds, then CORE fails
    nfcTransactor.queueTransactResults(
      listOf(
        Ok(FwupTransactionResult.Completed), // UXC
        Err(NfcException.CommandError()) // CORE
      )
    )

    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      // UXC transaction succeeds (first MCU)
      nfcTransactor.transactCalls.awaitItem()

      // After first MCU completes, show intermediate screen prompting for next MCU
      awaitBody<FwupNextComponentReadyModel> {
        onContinue()
      }

      // CORE transaction starts after user taps continue
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }
      nfcTransactor.transactCalls.awaitItem()

      // Should track failure with CORE context
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_FWUP_MCU_UPDATE_FAILED, context = FwupMcuEventTrackerContext.CORE)
      )

      // Should call onError
      onErrorCalls.awaitItem().shouldBeTypeOf<NfcException.CommandError>()
    }
  }

  // Resume / Retry Tests

  test("W3 failure during CORE MCU emits transactionType with currentMcuIndex = 1") {
    // Configure as W3 hardware
    accountConfigService.setHardwareType(HardwareType.W3)
    firmwareDataService.firmwareData.value = FirmwareDataPendingUpdateMock_W3

    // Capture the full transactionType from onError
    val errorTransactionTypeCalls = turbines.create<FwupTransactionType>("error transactionType")

    val captureProps = FwupNfcSessionUiProps(
      onBack = { onBackCalls.add(Unit) },
      onDone = { onDoneCalls.add(Unit) },
      transactionType = FwupTransactionType.StartFromBeginning(),
      onError = { error, _, transactionType ->
        onErrorCalls.add(error)
        errorTransactionTypeCalls.add(transactionType)
      }
    )

    // Queue results: UXC succeeds, then CORE fails
    nfcTransactor.queueTransactResults(
      listOf(
        Ok(FwupTransactionResult.Completed), // UXC
        Err(NfcException.CommandError()) // CORE
      )
    )

    stateMachine.test(captureProps) {
      awaitBody<FwupNfcBodyModel> { status.shouldBeTypeOf<Searching>() }
      nfcTransactor.transactCalls.awaitItem()

      // First MCU completes, transition to next
      awaitBody<FwupNextComponentReadyModel> { onContinue() }

      // Second MCU (CORE) starts
      awaitBody<FwupNfcBodyModel> { status.shouldBeTypeOf<Searching>() }
      nfcTransactor.transactCalls.awaitItem()

      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_FWUP_MCU_UPDATE_FAILED, context = FwupMcuEventTrackerContext.CORE)
      )
      onErrorCalls.awaitItem().shouldBeTypeOf<NfcException.CommandError>()

      // Verify the emitted transactionType points to MCU index 1 (CORE)
      val emittedType = errorTransactionTypeCalls.awaitItem()
      emittedType.currentMcuIndex.shouldBe(1)
    }
  }

  test("resume with ResumeFromSequenceId at MCU index 1 starts at CORE MCU directly") {
    // Configure as W3 hardware
    accountConfigService.setHardwareType(HardwareType.W3)
    firmwareDataService.firmwareData.value = FirmwareDataPendingUpdateMock_W3
    nfcTransactor.transactResult = Ok(FwupTransactionResult.Completed)

    // Start at MCU index 1 (CORE) — simulating resume after UXC already completed
    val resumeProps = FwupNfcSessionUiProps(
      onBack = { onBackCalls.add(Unit) },
      onDone = { onDoneCalls.add(Unit) },
      transactionType = FwupTransactionType.ResumeFromSequenceId(
        sequenceId = 5u,
        currentMcuIndex = 1
      ),
      onError = { error, _, _ -> onErrorCalls.add(error) }
    )

    stateMachine.test(resumeProps) {
      // Should start searching directly (no intermediate screen for first MCU)
      awaitBody<FwupNfcBodyModel> { status.shouldBeTypeOf<Searching>() }

      // Only one NFC transaction (for CORE at index 1), not two
      nfcTransactor.transactCalls.awaitItem()

      // Should go straight to success (both MCUs done — UXC was already complete)
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Success>()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_FWUP_COMPLETE))
      onDoneCalls.awaitItem()
    }
  }

  test("resume with StartFromBeginning at MCU index 1 starts at CORE MCU directly") {
    // Configure as W3 hardware
    accountConfigService.setHardwareType(HardwareType.W3)
    firmwareDataService.firmwareData.value = FirmwareDataPendingUpdateMock_W3
    nfcTransactor.transactResult = Ok(FwupTransactionResult.Completed)

    // Start at MCU index 1 (CORE) — simulating retry after failure before fwupInProgress
    val retryProps = FwupNfcSessionUiProps(
      onBack = { onBackCalls.add(Unit) },
      onDone = { onDoneCalls.add(Unit) },
      transactionType = FwupTransactionType.StartFromBeginning(currentMcuIndex = 1),
      onError = { error, _, _ -> onErrorCalls.add(error) }
    )

    stateMachine.test(retryProps) {
      // Should start searching directly at CORE MCU
      awaitBody<FwupNfcBodyModel> { status.shouldBeTypeOf<Searching>() }

      // Only one NFC transaction (for CORE at index 1)
      nfcTransactor.transactCalls.awaitItem()

      // Should go straight to success
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Success>()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_FWUP_COMPLETE))
      onDoneCalls.awaitItem()
    }
  }

  // Previous MCU Update Verification Tests

  test("W3 sequential update - PreviousMcuUpdateNotApplied calls onError and resets to beginning") {
    accountConfigService.setHardwareType(HardwareType.W3)
    firmwareDataService.firmwareData.value = FirmwareDataPendingUpdateMock_W3

    val prevMcuErrorTransactionTypeCalls = turbines.create<FwupTransactionType>("prevMcu error transactionType")

    val captureProps = FwupNfcSessionUiProps(
      onBack = { onBackCalls.add(Unit) },
      onDone = { onDoneCalls.add(Unit) },
      transactionType = FwupTransactionType.StartFromBeginning(),
      onError = { error, _, transactionType ->
        onErrorCalls.add(error)
        prevMcuErrorTransactionTypeCalls.add(transactionType)
      }
    )

    // UXC completes, then CORE detects previous MCU not applied
    nfcTransactor.queueTransactResults(
      listOf(
        Ok(FwupTransactionResult.Completed), // UXC
        Ok(
          FwupTransactionResult.PreviousMcuUpdateNotApplied(
            mcuRole = build.wallet.firmware.McuRole.UXC,
            expectedVersion = "2.0.0-fake",
            actualVersion = "1.2.3"
          )
        )
      )
    )

    stateMachine.test(captureProps) {
      awaitBody<FwupNfcBodyModel> { status.shouldBeTypeOf<Searching>() }
      nfcTransactor.transactCalls.awaitItem()

      // First MCU completes, transition to next
      awaitBody<FwupNextComponentReadyModel> { onContinue() }

      // Second MCU (CORE) starts
      awaitBody<FwupNfcBodyModel> { status.shouldBeTypeOf<Searching>() }
      nfcTransactor.transactCalls.awaitItem()

      // Should call onError with PreviousMcuUpdateNotApplied
      onErrorCalls.awaitItem().shouldBeTypeOf<NfcException.PreviousMcuUpdateNotApplied>()

      // Should reset to StartFromBeginning at MCU index 0
      val emittedType = prevMcuErrorTransactionTypeCalls.awaitItem()
      emittedType.shouldBeTypeOf<FwupTransactionType.StartFromBeginning>()
      emittedType.currentMcuIndex.shouldBe(0)
    }
  }
})

/**
 * Test transactor that executes the real transaction lambda against injected [NfcCommands].
 *
 * Most tests use [NfcTransactorMock], which only records transact calls. The iOS cooldown tests
 * need to drive the FWUP transaction far enough to set `fwupInProgress = true` before injecting a
 * `NoSession` failure, so they use this transactor instead.
 */
private class ExecutingNfcTransactor(
  private val commandsPerCall: List<NfcCommands>,
  private val sessionFactory: (NfcSession.Parameters, Int) -> NfcSession =
    { parameters, _ -> SilentNfcSession(parameters) },
) : NfcTransactor {
  override var isTransacting: Boolean = false
  private var transactCount = 0

  override suspend fun <T> transact(
    parameters: NfcSession.Parameters,
    transaction: TransactionFn<T>,
  ): Result<T, NfcException> {
    isTransacting = true
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

private class DeferredResultNfcTransactor<T>(
  private val result: CompletableDeferred<Result<T, NfcException>>,
  turbineName: String,
  turbineFactory: (name: String) -> Turbine<Any>,
) : NfcTransactor {
  override var isTransacting: Boolean = false
  val transactCalls = turbineFactory(turbineName)

  @Suppress("UNCHECKED_CAST")
  override suspend fun <R> transact(
    parameters: NfcSession.Parameters,
    transaction: TransactionFn<R>,
  ): Result<R, NfcException> {
    isTransacting = true
    transactCalls.add(parameters)
    return try {
      result.await() as Result<R, NfcException>
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
