package build.wallet.nfc

import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilderMock
import build.wallet.bitcoin.wallet.SpendingWalletFake
import build.wallet.cloud.backup.csek.Csek
import build.wallet.crypto.SymmetricKeyImpl
import build.wallet.encrypt.MessageSignerFake
import build.wallet.encrypt.SignatureUtilsMock
import build.wallet.nfc.NfcSessionFake.Companion.invoke
import build.wallet.platform.random.uuid
import com.github.michaelbull.result.Ok
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.throwable.shouldHaveMessage
import okio.ByteString.Companion.encodeUtf8

class NfcCommandsFakeTests : FunSpec({
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
    fakeHardwareSpendingWalletProvider
  )
  val sessionFake = invoke()

  beforeTest {
    fakeHardwareKeyStore.clear()
  }

  context("seal and unseal CSEK") {
    test("happy path") {
      val csekSeed = uuid()
      val generatedCsek = Csek(key = SymmetricKeyImpl(raw = csekSeed.encodeUtf8()))
      val sealedCsek = nfcCommands.sealData(
        session = sessionFake,
        unsealedData = generatedCsek.key.raw
      )
      nfcCommands
        .unsealData(sessionFake, sealedCsek)
        .shouldBeEqual(generatedCsek.key.raw)
    }

    test("cannot unseal key when appropriate private key is not present") {
      val csekSeed = uuid()
      val sealedCsek = nfcCommands.sealData(
        session = sessionFake,
        unsealedData = csekSeed.encodeUtf8()
      )

      fakeHardwareKeyStore.clear()

      shouldThrow<IllegalArgumentException> {
        nfcCommands.unsealData(sessionFake, sealedCsek)
      }.shouldHaveMessage("Appropriate fake hw auth private key missing")
    }
  }
})
