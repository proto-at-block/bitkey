package build.wallet.nfc.interceptors

import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilderMock
import build.wallet.bitcoin.wallet.SpendingWalletFake
import build.wallet.encrypt.MessageSignerFake
import build.wallet.encrypt.SignatureUtilsMock
import build.wallet.fwup.FwupFinishResponseStatus
import build.wallet.fwup.FwupMode
import build.wallet.nfc.BitkeyW1CommandsFake
import build.wallet.nfc.FakeHardwareKeyStoreFake
import build.wallet.nfc.FakeHardwareSpendingWalletProvider
import build.wallet.nfc.NfcException.CanBeRetried
import build.wallet.nfc.NfcSession
import build.wallet.nfc.NfcSessionFake
import build.wallet.nfc.platform.NfcCommands
import com.github.michaelbull.result.Ok
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RetryingNfcCommandsImplTest : FunSpec({
  val messageSigner = MessageSignerFake()
  val signatureUtils = SignatureUtilsMock()
  val fakeHardwareKeyStore = FakeHardwareKeyStoreFake()
  val fakeHardwareSpendingWalletProvider = FakeHardwareSpendingWalletProvider(
    spendingWalletProvider = { Ok(SpendingWalletFake()) },
    fakeHardwareKeyStore = fakeHardwareKeyStore,
    descriptorBuilder = BitcoinMultiSigDescriptorBuilderMock()
  )

  test("fwupFinish should treat iOS 'Tag response error / no response' as success") {
    var callCount = 0
    val baseCommands = BitkeyW1CommandsFake(
      messageSigner,
      signatureUtils,
      fakeHardwareKeyStore,
      fakeHardwareSpendingWalletProvider
    )

    val mockCommands = object : NfcCommands by baseCommands {
      override suspend fun fwupFinish(
        session: NfcSession,
        appPropertiesOffset: UInt,
        signatureOffset: UInt,
        fwupMode: FwupMode,
      ): FwupFinishResponseStatus {
        callCount++
        // Simulate iOS detecting device reset before reading response
        throw CanBeRetried.TransceiveFailure("Tag response error / no response", null)
      }
    }

    val session = NfcSessionFake()

    val interceptorFunc = retryCommands()
    var result: FwupFinishResponseStatus? = null
    val effect: NfcEffect = { _, commands ->
      result = commands.fwupFinish(session, 0u, 0u, FwupMode.Delta)
    }

    interceptorFunc.invoke(effect)(session, mockCommands)

    // Should return WillApplyPatch instead of throwing
    result shouldBe FwupFinishResponseStatus.WillApplyPatch
    callCount shouldBe 1
  }

  test("fwupFinish should still throw for other TransceiveFailure errors") {
    var callCount = 0
    val baseCommands = BitkeyW1CommandsFake(
      messageSigner,
      signatureUtils,
      fakeHardwareKeyStore,
      fakeHardwareSpendingWalletProvider
    )

    val mockCommands = object : NfcCommands by baseCommands {
      override suspend fun fwupFinish(
        session: NfcSession,
        appPropertiesOffset: UInt,
        signatureOffset: UInt,
        fwupMode: FwupMode,
      ): FwupFinishResponseStatus {
        callCount++
        // Simulate different error that should not be treated as success
        throw CanBeRetried.TransceiveFailure("Some other NFC error", null)
      }
    }

    val session = NfcSessionFake()

    val interceptorFunc = retryCommands()
    val effect: NfcEffect = { _, commands ->
      commands.fwupFinish(session, 0u, 0u, FwupMode.Delta)
    }

    shouldThrow<CanBeRetried.TransceiveFailure> {
      interceptorFunc.invoke(effect)(session, mockCommands)
    }

    // Verify fwupFinish was called exactly once (no retry)
    callCount shouldBe 1
  }

  test("fwupFinish should throw for TransceiveFailure with null message") {
    var callCount = 0
    val baseCommands = BitkeyW1CommandsFake(
      messageSigner,
      signatureUtils,
      fakeHardwareKeyStore,
      fakeHardwareSpendingWalletProvider
    )

    val mockCommands = object : NfcCommands by baseCommands {
      override suspend fun fwupFinish(
        session: NfcSession,
        appPropertiesOffset: UInt,
        signatureOffset: UInt,
        fwupMode: FwupMode,
      ): FwupFinishResponseStatus {
        callCount++
        // Simulate TransceiveFailure with null message - should not be treated as success
        throw CanBeRetried.TransceiveFailure(null, null)
      }
    }

    val session = NfcSessionFake()

    val interceptorFunc = retryCommands()
    val effect: NfcEffect = { _, commands ->
      commands.fwupFinish(session, 0u, 0u, FwupMode.Delta)
    }

    shouldThrow<CanBeRetried.TransceiveFailure> {
      interceptorFunc.invoke(effect)(session, mockCommands)
    }

    // Verify fwupFinish was called exactly once (no retry)
    callCount shouldBe 1
  }

  test("version command should retry on TransceiveFailure") {
    var callCount = 0
    val baseCommands = BitkeyW1CommandsFake(
      messageSigner,
      signatureUtils,
      fakeHardwareKeyStore,
      fakeHardwareSpendingWalletProvider
    )

    val mockCommands = object : NfcCommands by baseCommands {
      override suspend fun version(session: NfcSession): UShort {
        callCount++
        if (callCount == 1) {
          throw CanBeRetried.TransceiveFailure("Temporary NFC failure", null)
        }
        return 100u
      }
    }

    val session = NfcSessionFake()

    val interceptorFunc = retryCommands()
    var result: UShort = 0u
    val effect: NfcEffect = { _, commands ->
      result = commands.version(session)
    }

    interceptorFunc.invoke(effect)(session, mockCommands)

    result shouldBe 100u
    callCount shouldBe 2 // Should have retried once
  }
})
