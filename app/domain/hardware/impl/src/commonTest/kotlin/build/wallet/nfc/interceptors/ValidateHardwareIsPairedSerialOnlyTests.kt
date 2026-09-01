package build.wallet.nfc.interceptors

import bitkey.account.AccountConfigServiceFake
import bitkey.account.HardwareType
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilderMock
import build.wallet.bitcoin.wallet.SpendingWalletFake
import build.wallet.bitcoin.wallet.SpendingWalletV2ProviderMock
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.encrypt.MessageSignerFake
import build.wallet.encrypt.SignatureUtilsMock
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.Bdk2FeatureFlag
import build.wallet.feature.flags.FirmwareCommsLoggingFeatureFlag
import build.wallet.firmware.FirmwareCommsLogBufferFake
import build.wallet.firmware.FirmwareDeviceInfoDaoFake
import build.wallet.firmware.FirmwareFeatureFlag
import build.wallet.firmware.FirmwareFeatureFlagCfg
import build.wallet.firmware.FirmwareTelemetryUploader
import build.wallet.firmware.TelemetryIdentifiers
import build.wallet.grants.GrantAction
import build.wallet.grants.GrantRequest
import build.wallet.nfc.*
import build.wallet.nfc.NfcException.UnpairedHardwareError
import build.wallet.nfc.NfcSession.RequirePairedHardware.Required
import build.wallet.nfc.NfcSession.RequirePairedHardware.RequiredSerialOnly
import build.wallet.nfc.platform.NfcCommands
import build.wallet.sqldelight.inMemorySqlDriver
import com.github.michaelbull.result.Ok
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

/**
 * Tests for [NfcSession.RequirePairedHardware.RequiredSerialOnly], the serial-based pairing check
 * used by the fingerprint-reset grant-request flow (W-17516), which cannot sign a challenge.
 *
 * Also guards that [Required] on W1 keeps its challenge-signing posture.
 */
class ValidateHardwareIsPairedSerialOnlyTests : FunSpec({
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
  val w1NfcCommands = BitkeyW1CommandsFake(
    messageSigner,
    signatureUtils,
    fakeHardwareKeyStore,
    fakeHardwareSpendingWalletProvider,
    fakeHardwareStatesDao
  )
  val w3FakeHardwareKeyStore = FakeHardwareKeyStoreFake()
  val w3FakeHardwareSpendingWalletProvider = FakeHardwareSpendingWalletProvider(
    spendingWalletProvider = { Ok(SpendingWalletFake()) },
    spendingWalletV2Provider = SpendingWalletV2ProviderMock(),
    bdk2FeatureFlag = Bdk2FeatureFlag(featureFlagDao),
    descriptorBuilder = BitcoinMultiSigDescriptorBuilderMock(),
    fakeHardwareKeyStore = w3FakeHardwareKeyStore
  )
  val w3AccountConfigService = AccountConfigServiceFake().also {
    kotlinx.coroutines.runBlocking { it.setHardwareType(HardwareType.W3) }
  }
  val w3NfcCommands = BitkeyW3CommandsFake(
    w1CommandsFake = w1NfcCommands,
    accountConfigService = w3AccountConfigService,
    fakeHardwareKeyStore = w3FakeHardwareKeyStore,
    fakeHardwareSpendingWalletProvider = w3FakeHardwareSpendingWalletProvider,
    fakeHardwareStatesDao = fakeHardwareStatesDao,
    messageSigner = messageSigner,
    signatureUtils = signatureUtils
  )

  val firmwareDeviceInfoDao = FirmwareDeviceInfoDaoFake()

  beforeTest {
    firmwareDeviceInfoDao.reset()
  }

  fun session(
    hardwareType: HardwareType,
    requirePairedHardware: NfcSession.RequirePairedHardware,
  ) = NfcSessionFake(
    NfcSession.Parameters(
      isHardwareFake = false,
      hardwareType = hardwareType,
      needsAuthentication = false,
      shouldLock = false,
      skipFirmwareTelemetry = false,
      nfcFlowName = "test",
      requirePairedHardware = requirePairedHardware,
      maxNfcRetryAttempts = 3,
      onTagConnected = {},
      onTagDisconnected = {},
      asyncNfcSigning = false
    )
  )

  // --- RequiredSerialOnly: matching serial proceeds on both platforms ---

  test("RequiredSerialOnly - W1 proceeds when serial matches the paired device") {
    firmwareDeviceInfoDao.storedDeviceInfo = FakeFirmwareDeviceInfo

    var nextCalled = false
    val interceptor = validateHardwareIsPaired(firmwareDeviceInfoDao)
    val effect: NfcEffect = { _, _ -> nextCalled = true }
    interceptor.invoke(effect)(session(HardwareType.W1, RequiredSerialOnly), w1NfcCommands)

    nextCalled shouldBe true
  }

  test("RequiredSerialOnly - W3 proceeds when serial matches the paired device") {
    firmwareDeviceInfoDao.storedDeviceInfo = FakeW3FirmwareDeviceInfo

    var nextCalled = false
    val interceptor = validateHardwareIsPaired(firmwareDeviceInfoDao)
    val effect: NfcEffect = { _, _ -> nextCalled = true }
    interceptor.invoke(effect)(session(HardwareType.W3, RequiredSerialOnly), w3NfcCommands)

    nextCalled shouldBe true
  }

  // --- RequiredSerialOnly: unpaired serial rejected on both platforms ---

  test("RequiredSerialOnly - W1 throws UnpairedHardwareError when serial does not match") {
    // The production incident: an unpaired device tapped during grant creation.
    firmwareDeviceInfoDao.storedDeviceInfo =
      FakeFirmwareDeviceInfo.copy(serial = "someOtherPairedSerial")

    var nextCalled = false
    val interceptor = validateHardwareIsPaired(firmwareDeviceInfoDao)
    val effect: NfcEffect = { _, _ -> nextCalled = true }

    shouldThrow<UnpairedHardwareError> {
      interceptor.invoke(effect)(session(HardwareType.W1, RequiredSerialOnly), w1NfcCommands)
    }
    nextCalled shouldBe false
  }

  test("RequiredSerialOnly - W3 throws UnpairedHardwareError when serial does not match") {
    firmwareDeviceInfoDao.storedDeviceInfo =
      FakeW3FirmwareDeviceInfo.copy(serial = "someOtherPairedSerial")

    var nextCalled = false
    val interceptor = validateHardwareIsPaired(firmwareDeviceInfoDao)
    val effect: NfcEffect = { _, _ -> nextCalled = true }

    shouldThrow<UnpairedHardwareError> {
      interceptor.invoke(effect)(session(HardwareType.W3, RequiredSerialOnly), w3NfcCommands)
    }
    nextCalled shouldBe false
  }

  // --- RequiredSerialOnly: fails closed when no paired serial is known ---

  test("RequiredSerialOnly - W1 throws when expected serial is null (fails closed)") {
    // storedDeviceInfo left null
    val interceptor = validateHardwareIsPaired(firmwareDeviceInfoDao)
    val effect: NfcEffect = { _, _ -> }

    shouldThrow<UnpairedHardwareError> {
      interceptor.invoke(effect)(session(HardwareType.W1, RequiredSerialOnly), w1NfcCommands)
    }
  }

  test("RequiredSerialOnly - W3 throws when expected serial is null (fails closed)") {
    val interceptor = validateHardwareIsPaired(firmwareDeviceInfoDao)
    val effect: NfcEffect = { _, _ -> }

    shouldThrow<UnpairedHardwareError> {
      interceptor.invoke(effect)(session(HardwareType.W3, RequiredSerialOnly), w3NfcCommands)
    }
  }

  // --- RequiredSerialOnly must never sign a challenge (that's the whole point) ---

  test("RequiredSerialOnly - W1 does not call signChallenge") {
    firmwareDeviceInfoDao.storedDeviceInfo = FakeFirmwareDeviceInfo

    var signChallengeCalled = false
    val commands = object : NfcCommands by w1NfcCommands {
      override suspend fun signChallenge(
        session: NfcSession,
        challenge: ByteString,
      ): String {
        signChallengeCalled = true
        return "signature"
      }
    }

    val interceptor = validateHardwareIsPaired(firmwareDeviceInfoDao)
    val effect: NfcEffect = { _, _ -> }
    interceptor.invoke(effect)(session(HardwareType.W1, RequiredSerialOnly), commands)

    signChallengeCalled shouldBe false
  }

  // --- Regression guard: Required on W1 keeps signing and verifying the challenge ---

  test("Required - W1 still signs the challenge and verifies the signature") {
    // A serial mismatch must NOT be what decides this path; the signature is.
    firmwareDeviceInfoDao.storedDeviceInfo =
      FakeFirmwareDeviceInfo.copy(serial = "unrelatedSerial")

    var signChallengeCalled = false
    var verifyCalled = false
    val challenge = "w1-challenge".encodeUtf8()
    val commands = object : NfcCommands by w1NfcCommands {
      override suspend fun signChallenge(
        session: NfcSession,
        challenge: ByteString,
      ): String {
        signChallengeCalled = true
        return "signature"
      }
    }

    var nextCalled = false
    val interceptor = validateHardwareIsPaired(firmwareDeviceInfoDao)
    val effect: NfcEffect = { _, _ -> nextCalled = true }
    interceptor.invoke(effect)(
      session(
        HardwareType.W1,
        Required(challenge) { signature, signedChallenge ->
          verifyCalled = true
          signature shouldBe "signature"
          signedChallenge shouldBe challenge
          true
        }
      ),
      commands
    )

    signChallengeCalled shouldBe true
    verifyCalled shouldBe true
    nextCalled shouldBe true
  }

  test("Required - W1 still rejects when signature verification fails") {
    firmwareDeviceInfoDao.storedDeviceInfo = FakeFirmwareDeviceInfo

    val interceptor = validateHardwareIsPaired(firmwareDeviceInfoDao)
    val effect: NfcEffect = { _, _ -> }

    shouldThrow<UnpairedHardwareError> {
      interceptor.invoke(effect)(
        session(HardwareType.W1, Required("challenge".encodeUtf8()) { _, _ -> false }),
        w1NfcCommands
      )
    }
  }

  // --- Cache-clobber guard: telemetry must not persist the foreign device's identity ---

  test("RequiredSerialOnly - rejection prevents persistDeviceInfo from clobbering the cache") {
    // Mirrors the NfcComponent interceptor order: validateHardwareIsPaired runs before
    // collectFirmwareTelemetry, so a rejection must stop the DAO write entirely.
    val pairedDeviceInfo = FakeFirmwareDeviceInfo.copy(serial = "pairedW1Serial")
    firmwareDeviceInfoDao.storedDeviceInfo = pairedDeviceInfo

    val telemetry = collectFirmwareTelemetry(
      firmwareDeviceInfoDao = firmwareDeviceInfoDao,
      firmwareTelemetryUploader = NoopFirmwareTelemetryUploader,
      firmwareCommsLogBuffer = FirmwareCommsLogBufferFake(),
      firmwareCommsLoggingFeatureFlag = FirmwareCommsLoggingFeatureFlag(featureFlagDao)
    )
    val validate = validateHardwareIsPaired(firmwareDeviceInfoDao)

    val effect: NfcEffect = { _, _ -> }
    val chain = validate.invoke(telemetry.invoke(effect))

    shouldThrow<UnpairedHardwareError> {
      chain(session(HardwareType.W1, RequiredSerialOnly), w1NfcCommands)
    }

    // The genuinely-paired device's identity is untouched.
    firmwareDeviceInfoDao.storedDeviceInfo shouldBe pairedDeviceInfo
  }

  // --- Folded-in pre-1.0.101 bug: unpaired old-firmware device never reaches the body ---

  test("RequiredSerialOnly - unpaired device on pre-FINGERPRINT_RESET firmware never runs the session body") {
    // Precondition of the old "skip reset but report success" bug was an unpaired tap.
    // The interceptor rejects before the session body, so feature flags / grant request
    // are never queried at all.
    firmwareDeviceInfoDao.storedDeviceInfo =
      FakeFirmwareDeviceInfo.copy(serial = "pairedW1Serial")

    var getFirmwareFeatureFlagsCalled = false
    var getGrantRequestCalled = false
    val oldFirmwareCommands = object : NfcCommands by w1NfcCommands {
      override suspend fun getFirmwareFeatureFlags(
        session: NfcSession,
      ): List<FirmwareFeatureFlagCfg> {
        getFirmwareFeatureFlagsCalled = true
        // Pre-1.0.101: FINGERPRINT_RESET is not reported.
        return emptyList()
      }

      override suspend fun getGrantRequest(
        session: NfcSession,
        action: GrantAction,
      ): GrantRequest {
        getGrantRequestCalled = true
        return w1NfcCommands.getGrantRequest(session, action)
      }
    }

    var bodyRan = false
    val interceptor = validateHardwareIsPaired(firmwareDeviceInfoDao)
    val sessionBody: NfcEffect = { nfcSession, commands ->
      bodyRan = true
      val enabled = commands.getFirmwareFeatureFlags(nfcSession)
        .find { it.flag == FirmwareFeatureFlag.FINGERPRINT_RESET }
        ?.enabled ?: false
      if (enabled) {
        commands.getGrantRequest(nfcSession, GrantAction.FINGERPRINT_RESET)
      }
    }

    shouldThrow<UnpairedHardwareError> {
      interceptor.invoke(sessionBody)(
        session(HardwareType.W1, RequiredSerialOnly),
        oldFirmwareCommands
      )
    }

    bodyRan shouldBe false
    getFirmwareFeatureFlagsCalled shouldBe false
    getGrantRequestCalled shouldBe false
  }

  test("RequiredSerialOnly - paired device on pre-FINGERPRINT_RESET firmware still reaches the body and diverts to fwup") {
    // Legitimate old-firmware diversion must keep working.
    firmwareDeviceInfoDao.storedDeviceInfo = FakeFirmwareDeviceInfo

    val oldFirmwareCommands = object : NfcCommands by w1NfcCommands {
      override suspend fun getFirmwareFeatureFlags(
        session: NfcSession,
      ): List<FirmwareFeatureFlagCfg> = emptyList()
    }

    var fingerprintResetEnabled: Boolean? = null
    val interceptor = validateHardwareIsPaired(firmwareDeviceInfoDao)
    val sessionBody: NfcEffect = { nfcSession, commands ->
      fingerprintResetEnabled = commands.getFirmwareFeatureFlags(nfcSession)
        .find { it.flag == FirmwareFeatureFlag.FINGERPRINT_RESET }
        ?.enabled ?: false
    }

    interceptor.invoke(sessionBody)(
      session(HardwareType.W1, RequiredSerialOnly),
      oldFirmwareCommands
    )

    // Body ran and would take the FwUpRequired branch.
    fingerprintResetEnabled shouldBe false
  }
})

private object NoopFirmwareTelemetryUploader : FirmwareTelemetryUploader {
  override fun addEvents(
    events: ByteString,
    identifiers: TelemetryIdentifiers,
  ) = Unit

  override fun addCoredump(
    coredump: ByteString,
    identifiers: TelemetryIdentifiers,
  ) = Unit
}
