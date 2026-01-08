package build.wallet.nfc

import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilderMock
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.wallet.SpendingWalletFake
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.cloud.backup.csek.Csek
import build.wallet.crypto.SymmetricKeyImpl
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.encrypt.MessageSignerFake
import build.wallet.encrypt.SignatureUtilsMock
import build.wallet.nfc.NfcSessionFake.Companion.invoke
import build.wallet.nfc.platform.sealSymmetricKey
import build.wallet.nfc.platform.unsealSymmetricKey
import build.wallet.nfc.transaction.TransactionError
import build.wallet.platform.random.uuid
import build.wallet.sqldelight.inMemorySqlDriver
import com.github.michaelbull.result.Ok
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import okio.ByteString.Companion.encodeUtf8

class NfcCommandsFakeTests : FunSpec({
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
  val sessionFake = invoke()

  beforeTest {
    fakeHardwareKeyStore.clear()
    fakeHardwareStatesDao.clear()
  }

  context("seal and unseal CSEK") {
    test("happy path") {
      val csekSeed = uuid()
      val generatedCsek = Csek(key = SymmetricKeyImpl(raw = csekSeed.encodeUtf8()))
      val sealedCsek = nfcCommands.sealSymmetricKey(
        session = sessionFake,
        key = generatedCsek.key
      )
      nfcCommands
        .unsealSymmetricKey(sessionFake, sealedCsek)
        .shouldBeEqual(generatedCsek.key)
    }

    test("cannot unseal key when appropriate private key is not present") {
      val csekSeed = uuid()
      val sealedCsek = nfcCommands.sealData(
        session = sessionFake,
        unsealedData = csekSeed.encodeUtf8()
      )

      fakeHardwareKeyStore.clear()

      shouldThrow<NfcException.CommandErrorSealCsekResponseUnsealException> {
        nfcCommands.unsealData(sessionFake, sealedCsek)
      }
    }
  }

  context("sign transaction") {
    test("throws VerificationRequired when transaction verification is enabled") {
      fakeHardwareStatesDao.setTransactionVerificationEnabled(true)

      shouldThrow<TransactionError.VerificationRequired> {
        nfcCommands.signTransaction(
          session = sessionFake,
          psbt = PsbtMock,
          spendingKeyset = SpendingKeysetMock
        )
      }
    }
  }
})
