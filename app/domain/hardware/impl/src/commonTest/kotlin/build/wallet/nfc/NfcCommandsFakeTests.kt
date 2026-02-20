package build.wallet.nfc

import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilderMock
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.wallet.SpendingWalletFake
import build.wallet.bitcoin.wallet.SpendingWalletV2ProviderMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.cloud.backup.csek.Csek
import build.wallet.crypto.SymmetricKeyImpl
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.encrypt.MessageSignerFake
import build.wallet.encrypt.SignatureUtilsMock
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.Bdk2FeatureFlag
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

  context("getAddress") {
    test("W1 fake throws exception because getAddress is W3-only") {
      shouldThrow<NfcException.CommandError> {
        nfcCommands.getAddress(
          session = sessionFake,
          addressIndex = 0u
        )
      }
    }
  }

  context("W3 getAddress") {
    val w3Commands = BitkeyW3CommandsFake(nfcCommands)

    test("W3 fake returns address at index 0") {
      val result = w3Commands.getAddress(
        session = sessionFake,
        addressIndex = 0u
      )

      result.shouldBeEqual("bc1q_fake_w3_0")
    }

    test("W3 fake returns address at index 5") {
      val result = w3Commands.getAddress(
        session = sessionFake,
        addressIndex = 5u
      )

      result.shouldBeEqual("bc1q_fake_w3_5")
    }

    test("W3 fake returns different addresses for different indices") {
      val result0 = w3Commands.getAddress(sessionFake, 0u)
      val result1 = w3Commands.getAddress(sessionFake, 1u)
      val result2 = w3Commands.getAddress(sessionFake, 2u)

      result0.shouldBeEqual("bc1q_fake_w3_0")
      result1.shouldBeEqual("bc1q_fake_w3_1")
      result2.shouldBeEqual("bc1q_fake_w3_2")
    }
  }
})
