package build.wallet.nfc.interceptors

import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilderMock
import build.wallet.bitcoin.wallet.SpendingWalletFake
import build.wallet.encrypt.MessageSignerFake
import build.wallet.nfc.FakeHardwareKeyStoreFake
import build.wallet.nfc.FakeHardwareSpendingWalletProvider
import build.wallet.nfc.NfcCommandsFake
import build.wallet.nfc.NfcException.UnpairedHardwareError
import build.wallet.nfc.NfcSession
import build.wallet.nfc.NfcSession.RequirePairedHardware.NotRequired
import build.wallet.nfc.NfcSession.RequirePairedHardware.Required
import build.wallet.nfc.NfcSessionFake
import com.github.michaelbull.result.Ok
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8

class CheckHardwareIsPairedInterceptorTests : FunSpec({
  val messageSigner = MessageSignerFake()
  val fakeHardwareKeyStore = FakeHardwareKeyStoreFake()
  val fakeHardwareSpendingWalletProvider = FakeHardwareSpendingWalletProvider(
    spendingWalletProvider = { Ok(SpendingWalletFake()) },
    fakeHardwareKeyStore = fakeHardwareKeyStore,
    descriptorBuilder = BitcoinMultiSigDescriptorBuilderMock()
  )
  val nfcCommands =
    NfcCommandsFake(messageSigner, fakeHardwareKeyStore, fakeHardwareSpendingWalletProvider)

  test("does nothing when hardware validation not required") {
    var nextCalled = false
    val session = NfcSessionFake(
      NfcSession.Parameters(
        isHardwareFake = false,
        needsAuthentication = false,
        shouldLock = false,
        skipFirmwareTelemetry = false,
        nfcFlowName = "test",
        onTagConnected = {},
        onTagDisconnected = {},
        asyncNfcSigning = false,
        requirePairedHardware = NotRequired
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
        needsAuthentication = false,
        shouldLock = false,
        skipFirmwareTelemetry = false,
        nfcFlowName = "test",
        onTagConnected = {},
        onTagDisconnected = {},
        asyncNfcSigning = false,
        requirePairedHardware = Required("challenge".encodeUtf8()) { _, _ -> true }
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
        needsAuthentication = false,
        shouldLock = false,
        skipFirmwareTelemetry = false,
        nfcFlowName = "test",
        onTagConnected = {},
        onTagDisconnected = {},
        asyncNfcSigning = false,
        requirePairedHardware = Required("challenge".encodeUtf8()) { _, _ -> false }
      )
    )

    val interceptor = validateHardwareIsPaired()
    val effect: NfcEffect = { session, nfcCommands -> }

    shouldThrow<UnpairedHardwareError> {
      interceptor.invoke(effect)(session, nfcCommands)
    }
  }
})
