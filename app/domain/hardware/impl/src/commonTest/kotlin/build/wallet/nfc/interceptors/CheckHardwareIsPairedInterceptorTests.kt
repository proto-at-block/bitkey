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
import build.wallet.firmware.FirmwareDeviceInfoDaoFake
import build.wallet.nfc.*
import build.wallet.nfc.FakeFirmwareDeviceInfo
import build.wallet.nfc.FakeW3FirmwareDeviceInfo
import build.wallet.nfc.NfcException.UnpairedHardwareError
import build.wallet.nfc.NfcSession.RequirePairedHardware.NotRequired
import build.wallet.nfc.NfcSession.RequirePairedHardware.Required
import build.wallet.nfc.platform.NfcCommands
import build.wallet.sqldelight.inMemorySqlDriver
import com.github.michaelbull.result.Ok
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

class CheckHardwareIsPairedInterceptorTests : FunSpec({
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
    w1CommandsFake = nfcCommands,
    accountConfigService = w3AccountConfigService,
    fakeHardwareKeyStore = w3FakeHardwareKeyStore,
    fakeHardwareSpendingWalletProvider = w3FakeHardwareSpendingWalletProvider,
    fakeHardwareStatesDao = fakeHardwareStatesDao,
    messageSigner = messageSigner,
    signatureUtils = signatureUtils
  )

  val firmwareDeviceInfoDao = FirmwareDeviceInfoDaoFake()

  beforeEach {
    firmwareDeviceInfoDao.reset()
  }

  // --- W1 tests (challenge-signing path) ---

  test("W1 - does nothing when hardware validation not required") {
    var nextCalled = false
    val session = NfcSessionFake(
      NfcSession.Parameters(
        isHardwareFake = false,
        hardwareType = HardwareType.W1,
        needsAuthentication = false,
        shouldLock = false,
        skipFirmwareTelemetry = false,
        nfcFlowName = "test",
        requirePairedHardware = NotRequired,
        maxNfcRetryAttempts = 3,
        onTagConnected = {},
        onTagDisconnected = {},
        asyncNfcSigning = false
      )
    )

    val interceptor = validateHardwareIsPaired(firmwareDeviceInfoDao)
    val effect: NfcEffect = { _, _ -> nextCalled = true }
    interceptor.invoke(effect)(session, nfcCommands)

    nextCalled shouldBe true
  }

  test("W1 - validates hardware via challenge signing and succeeds") {
    var nextCalled = false
    val session = NfcSessionFake(
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

    val interceptor = validateHardwareIsPaired(firmwareDeviceInfoDao)
    val effect: NfcEffect = { _, _ -> nextCalled = true }
    interceptor.invoke(effect)(session, nfcCommands)

    nextCalled shouldBe true
  }

  test("W1 - throws UnpairedHardwareError when challenge verification fails") {
    val session = NfcSessionFake(
      NfcSession.Parameters(
        isHardwareFake = false,
        hardwareType = HardwareType.W1,
        needsAuthentication = false,
        shouldLock = false,
        skipFirmwareTelemetry = false,
        nfcFlowName = "test",
        requirePairedHardware = Required("challenge".encodeUtf8()) { _, _ -> false },
        maxNfcRetryAttempts = 3,
        onTagConnected = {},
        onTagDisconnected = {},
        asyncNfcSigning = false
      )
    )

    val interceptor = validateHardwareIsPaired(firmwareDeviceInfoDao)
    val effect: NfcEffect = { _, _ -> }

    shouldThrow<UnpairedHardwareError> {
      interceptor.invoke(effect)(session, nfcCommands)
    }
  }

  test("W1 - throws UnpairedHardwareError when signChallenge is not supported") {
    val session = NfcSessionFake(
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

    val unsupportedSignChallengeCommands = object : NfcCommands by nfcCommands {
      override suspend fun signChallenge(
        session: NfcSession,
        challenge: ByteString,
      ): String {
        throw NfcException.FeatureNotSupported()
      }
    }

    val interceptor = validateHardwareIsPaired(firmwareDeviceInfoDao)
    val effect: NfcEffect = { _, _ -> }

    shouldThrow<UnpairedHardwareError> {
      interceptor.invoke(effect)(session, unsupportedSignChallengeCommands)
    }
  }

  // --- W3 tests (serial-comparison path) ---

  test("W3 - validates hardware via serial match and succeeds") {
    // Store the same serial that BitkeyW3CommandsFake.getDeviceInfo() returns
    firmwareDeviceInfoDao.storedDeviceInfo = FakeW3FirmwareDeviceInfo

    var nextCalled = false
    val session = NfcSessionFake(
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
        asyncNfcSigning = false
      )
    )

    val interceptor = validateHardwareIsPaired(firmwareDeviceInfoDao)
    val effect: NfcEffect = { _, _ -> nextCalled = true }
    interceptor.invoke(effect)(session, w3NfcCommands)

    nextCalled shouldBe true
  }

  test("W3 - throws UnpairedHardwareError when serial does not match") {
    // Store a different serial than what the hardware returns
    firmwareDeviceInfoDao.storedDeviceInfo =
      FakeFirmwareDeviceInfo.copy(serial = "different-serial")

    val session = NfcSessionFake(
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
        asyncNfcSigning = false
      )
    )

    val interceptor = validateHardwareIsPaired(firmwareDeviceInfoDao)
    val effect: NfcEffect = { _, _ -> }

    shouldThrow<UnpairedHardwareError> {
      interceptor.invoke(effect)(session, w3NfcCommands)
    }
  }

  test("W3 - throws UnpairedHardwareError when no stored device info") {
    // Don't set any stored device info
    val session = NfcSessionFake(
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
        asyncNfcSigning = false
      )
    )

    val interceptor = validateHardwareIsPaired(firmwareDeviceInfoDao)
    val effect: NfcEffect = { _, _ -> }

    shouldThrow<UnpairedHardwareError> {
      interceptor.invoke(effect)(session, w3NfcCommands)
    }
  }

  test("W3 - does nothing when hardware validation not required") {
    var nextCalled = false
    val session = NfcSessionFake(
      NfcSession.Parameters(
        isHardwareFake = false,
        hardwareType = HardwareType.W3,
        needsAuthentication = false,
        shouldLock = false,
        skipFirmwareTelemetry = false,
        nfcFlowName = "test",
        requirePairedHardware = NotRequired,
        maxNfcRetryAttempts = 3,
        onTagConnected = {},
        onTagDisconnected = {},
        asyncNfcSigning = false
      )
    )

    val interceptor = validateHardwareIsPaired(firmwareDeviceInfoDao)
    val effect: NfcEffect = { _, _ -> nextCalled = true }
    interceptor.invoke(effect)(session, w3NfcCommands)

    nextCalled shouldBe true
  }
})
