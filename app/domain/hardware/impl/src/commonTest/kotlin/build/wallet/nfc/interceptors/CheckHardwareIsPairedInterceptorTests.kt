package build.wallet.nfc.interceptors

import bitkey.account.HardwareType
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilderMock
import build.wallet.bitcoin.wallet.SpendingWalletFake
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.encrypt.MessageSignerFake
import build.wallet.encrypt.SignatureUtilsMock
import build.wallet.nfc.BitkeyW1CommandsFake
import build.wallet.nfc.FakeHardwareKeyStoreFake
import build.wallet.nfc.FakeHardwareSpendingWalletProvider
import build.wallet.nfc.FakeHardwareStatesDaoImpl
import build.wallet.nfc.NfcException.UnpairedHardwareError
import build.wallet.nfc.NfcSession
import build.wallet.nfc.NfcSession.RequirePairedHardware.NotRequired
import build.wallet.nfc.NfcSession.RequirePairedHardware.Required
import build.wallet.nfc.NfcSessionFake
import build.wallet.sqldelight.inMemorySqlDriver
import com.github.michaelbull.result.Ok
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8

class CheckHardwareIsPairedInterceptorTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()
  val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
  val fakeHardwareStatesDao = FakeHardwareStatesDaoImpl(databaseProvider)
  val messageSigner = MessageSignerFake()
  val signatureUtils = SignatureUtilsMock()
  val fakeHardwareKeyStore = FakeHardwareKeyStoreFake()
  val fakeHardwareSpendingWalletProvider = FakeHardwareSpendingWalletProvider(
    spendingWalletProvider = { Ok(SpendingWalletFake()) },
    fakeHardwareKeyStore = fakeHardwareKeyStore,
    descriptorBuilder = BitcoinMultiSigDescriptorBuilderMock()
  )
  val nfcCommands = BitkeyW1CommandsFake(
    messageSigner,
    signatureUtils,
    fakeHardwareKeyStore,
    fakeHardwareSpendingWalletProvider,
    fakeHardwareStatesDao
  )

  test("does nothing when hardware validation not required") {
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

    val interceptor = validateHardwareIsPaired()
    val effect: NfcEffect = { _, _ -> nextCalled = true }
    interceptor.invoke(effect)(session, nfcCommands)

    nextCalled shouldBe true
  }

  test("validates hardware when required and succeeds") {
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

    val interceptor = validateHardwareIsPaired()
    val effect: NfcEffect = { _, _ -> nextCalled = true }
    interceptor.invoke(effect)(session, nfcCommands)

    nextCalled shouldBe true
  }

  test("throws UnpairedHardwareError when validation fails") {
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

    val interceptor = validateHardwareIsPaired()
    val effect: NfcEffect = { session, nfcCommands -> }

    shouldThrow<UnpairedHardwareError> {
      interceptor.invoke(effect)(session, nfcCommands)
    }
  }
})
