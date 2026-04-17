package build.wallet.nfc.interceptors

import bitkey.account.HardwareType
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilderMock
import build.wallet.bitcoin.wallet.SpendingWalletFake
import build.wallet.bitcoin.wallet.SpendingWalletV2ProviderMock
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.encrypt.MessageSignerFake
import build.wallet.encrypt.SignatureUtilsMock
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.Bdk2FeatureFlag
import build.wallet.nfc.*
import build.wallet.nfc.NfcException.HardwareReplacementPendingError
import build.wallet.nfc.NfcSession.RequirePairedHardware.Required
import build.wallet.recovery.RecoveryStatusServiceFake
import build.wallet.recovery.StillRecoveringHardwareRecoveryMock
import build.wallet.recovery.StillRecoveringInitiatedRecoveryMock
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Ok
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.hours
import okio.ByteString.Companion.encodeUtf8

class RejectDuringHardwareDelayNotifyInterceptorTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()
  val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
  val fakeHardwareStatesDao = FakeHardwareStatesDaoImpl(databaseProvider)
  val messageSigner = MessageSignerFake()
  val signatureUtils = SignatureUtilsMock()
  val fakeHardwareKeyStore = FakeHardwareKeyStoreFake()
  val featureFlagDao = FeatureFlagDaoFake()
  val fakeHardwareSpendingWalletProvider = FakeHardwareSpendingWalletProvider(
    spendingWalletProvider = { Ok(SpendingWalletFake()) },
    spendingWalletV2Provider = SpendingWalletV2ProviderMock(),
    bdk2FeatureFlag = Bdk2FeatureFlag(featureFlagDao),
    descriptorBuilder = BitcoinMultiSigDescriptorBuilderMock(),
    fakeHardwareKeyStore = fakeHardwareKeyStore
  )
  val nfcCommands = BitkeyW1CommandsFake(
    messageSigner,
    signatureUtils,
    fakeHardwareKeyStore,
    fakeHardwareSpendingWalletProvider,
    fakeHardwareStatesDao
  )

  val recoveryStatusService = RecoveryStatusServiceFake()
  // ClockFake defaults to someInstant; StillRecoveringHardwareRecoveryMock has
  // delayEndTime = someInstant + 2.hours, so by default the delay is still active.
  val clock = ClockFake()

  beforeEach {
    recoveryStatusService.reset()
    clock.reset()
  }

  fun makeW3Session(skipLostHardwareCheck: Boolean = false) = NfcSessionFake(
    NfcSession.Parameters(
      isHardwareFake = false,
      hardwareType = HardwareType.W3,
      needsAuthentication = false,
      shouldLock = false,
      skipFirmwareTelemetry = false,
      nfcFlowName = "test",
      requirePairedHardware = Required("challenge".encodeUtf8()) { _, _ -> true },
      maxNfcRetryAttempts = 3,
      onTagConnected = {},
      onTagDisconnected = {},
      asyncNfcSigning = false,
      skipLostHardwareCheck = skipLostHardwareCheck
    )
  )

  // --- Hardware-factor D+N active (waiting period not yet expired) ---

  test("W3 - throws HardwareReplacementPendingError when hardware-factor D+N waiting period is active") {
    // delayEndTime = someInstant + 2.hours; clock at someInstant → delay still running.
    recoveryStatusService.status.value = StillRecoveringHardwareRecoveryMock

    val interceptor = rejectDuringHardwareDelayNotify(recoveryStatusService, clock)
    val effect: NfcEffect = { _, _ -> }

    shouldThrow<HardwareReplacementPendingError> {
      interceptor.invoke(effect)(makeW3Session(), nfcCommands)
    }
  }

  test("W3 - succeeds when hardware-factor D+N delay has expired and completion taps are running") {
    // InitiatedRecovery still set (completion not yet done), but delayEndTime passed.
    recoveryStatusService.status.value = StillRecoveringHardwareRecoveryMock
    clock.advanceBy(3.hours)

    var nextCalled = false
    val interceptor = rejectDuringHardwareDelayNotify(recoveryStatusService, clock)
    val effect: NfcEffect = { _, _ -> nextCalled = true }
    interceptor.invoke(effect)(makeW3Session(), nfcCommands)

    nextCalled shouldBe true
  }

  // --- factorToRecover = App must not be blocked ---

  test("W3 - does not block when app-factor InitiatedRecovery is active (lost-app recovery)") {
    // App-factor recovery (factorToRecover = App) uses InitiatedRecovery, but paired W3
    // taps are still needed during this flow. The guard must only fire for Hardware factor.
    recoveryStatusService.status.value = StillRecoveringInitiatedRecoveryMock

    var nextCalled = false
    val interceptor = rejectDuringHardwareDelayNotify(recoveryStatusService, clock)
    val effect: NfcEffect = { _, _ -> nextCalled = true }
    interceptor.invoke(effect)(makeW3Session(), nfcCommands)

    nextCalled shouldBe true
  }

  // --- skipLostHardwareCheck opt-out ---

  test("W3 - skips guard when skipLostHardwareCheck is true (lost-hardware cancellation PoP)") {
    // Even with an active hardware-factor D+N, the cancellation flow must pass through.
    recoveryStatusService.status.value = StillRecoveringHardwareRecoveryMock

    var nextCalled = false
    val interceptor = rejectDuringHardwareDelayNotify(recoveryStatusService, clock)
    val effect: NfcEffect = { _, _ -> nextCalled = true }
    interceptor.invoke(effect)(makeW3Session(skipLostHardwareCheck = true), nfcCommands)

    nextCalled shouldBe true
  }

  // --- No active recovery ---

  test("W3 - does nothing when no active recovery") {
    // recoveryStatusService defaults to NoActiveRecovery after reset()
    var nextCalled = false
    val interceptor = rejectDuringHardwareDelayNotify(recoveryStatusService, clock)
    val effect: NfcEffect = { _, _ -> nextCalled = true }
    interceptor.invoke(effect)(makeW3Session(), nfcCommands)

    nextCalled shouldBe true
  }

  // --- W1 is not affected ---

  test("W1 - does not apply, passes through regardless of D+N state") {
    recoveryStatusService.status.value = StillRecoveringHardwareRecoveryMock

    var nextCalled = false
    val w1Session = NfcSessionFake(
      NfcSession.Parameters(
        isHardwareFake = false,
        hardwareType = HardwareType.W1,
        needsAuthentication = false,
        shouldLock = false,
        skipFirmwareTelemetry = false,
        nfcFlowName = "test",
        requirePairedHardware = Required("challenge".encodeUtf8()) { _, _ -> true },
        maxNfcRetryAttempts = 3,
        onTagConnected = {},
        onTagDisconnected = {},
        asyncNfcSigning = false
      )
    )

    val interceptor = rejectDuringHardwareDelayNotify(recoveryStatusService, clock)
    val effect: NfcEffect = { _, _ -> nextCalled = true }
    interceptor.invoke(effect)(w1Session, nfcCommands)

    nextCalled shouldBe true
  }
})
