package build.wallet.nfc

import bitkey.account.AccountConfigServiceFake
import bitkey.account.HardwareType
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilderMock
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.wallet.SpendingWalletFake
import build.wallet.bitcoin.wallet.SpendingWalletV2ProviderMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.cloud.backup.csek.Csek
import build.wallet.coroutines.turbine.turbines
import build.wallet.crypto.SymmetricKeyImpl
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.encrypt.MessageSigner
import build.wallet.encrypt.MessageSignerFake
import build.wallet.encrypt.Secp256k1PrivateKey
import build.wallet.encrypt.SignatureUtils
import build.wallet.encrypt.SignatureUtilsMock
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.Bdk2FeatureFlag
import build.wallet.nfc.NfcSessionFake.Companion.invoke
import build.wallet.nfc.platform.ActionProofAction
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.nfc.platform.sealSymmetricKey
import build.wallet.nfc.platform.unsealSymmetricKey
import build.wallet.nfc.transaction.TransactionError
import build.wallet.platform.random.uuid
import build.wallet.sqldelight.inMemorySqlDriver
import com.github.michaelbull.result.Ok
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import kotlinx.coroutines.runBlocking

/**
 * Helper to deliver the descriptor to a W3 fake, unblocking [getAddress] and [signTransaction].
 */
private suspend fun BitkeyW3CommandsFake.deliverDescriptor(session: NfcSession) {
  verifyKeysAndBuildDescriptor(
    session = session,
    appSpendingKey = "00".repeat(33).decodeHex(),
    appSpendingKeyChaincode = "00".repeat(32).decodeHex(),
    networkMainnet = true,
    appAuthKey = "00".repeat(33).decodeHex(),
    serverSpendingKey = "00".repeat(33).decodeHex(),
    serverSpendingKeyChaincode = "00".repeat(32).decodeHex(),
    wsmSignature = "00".repeat(64).decodeHex(),
    accountIndex = 0u,
  )
}

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

    test("maps malformed sealed data to unseal exception") {
      shouldThrow<NfcException.CommandErrorSealCsekResponseUnsealException> {
        nfcCommands.unsealData(sessionFake, "not-protobuf".encodeUtf8())
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

  context("W3 getAddress") {
    val accountConfigService = AccountConfigServiceFake().also {
      runBlocking { it.setHardwareType(HardwareType.W3) }
    }
    val w3Commands = BitkeyW3CommandsFake(
      w1CommandsFake = nfcCommands,
      accountConfigService = accountConfigService,
      fakeHardwareKeyStore = fakeHardwareKeyStore,
      fakeHardwareSpendingWalletProvider = fakeHardwareSpendingWalletProvider,
      fakeHardwareStatesDao = fakeHardwareStatesDao,
      messageSigner = messageSigner,
      signatureUtils = signatureUtils
    )

    test("W3 fake throws DescriptorNotLoaded before descriptor delivery") {
      shouldThrow<NfcException.DescriptorNotLoaded> {
        w3Commands.getAddress(
          session = sessionFake,
          addressIndex = 0u
        )
      }
    }

    test("W3 fake returns address at index 0 after descriptor delivery") {
      w3Commands.deliverDescriptor(sessionFake)

      val result = w3Commands.getAddress(
        session = sessionFake,
        addressIndex = 0u
      )

      result.shouldBeEqual("bc1q_fake_w3_0")
    }

    test("W3 fake returns address at index 5 after descriptor delivery") {
      w3Commands.deliverDescriptor(sessionFake)

      val result = w3Commands.getAddress(
        session = sessionFake,
        addressIndex = 5u
      )

      result.shouldBeEqual("bc1q_fake_w3_5")
    }

    test("W3 fake returns different addresses for different indices after descriptor delivery") {
      w3Commands.deliverDescriptor(sessionFake)

      val result0 = w3Commands.getAddress(sessionFake, 0u)
      val result1 = w3Commands.getAddress(sessionFake, 1u)
      val result2 = w3Commands.getAddress(sessionFake, 2u)

      result0.shouldBeEqual("bc1q_fake_w3_0")
      result1.shouldBeEqual("bc1q_fake_w3_1")
      result2.shouldBeEqual("bc1q_fake_w3_2")
    }
  }

  context("W3 descriptor delivery") {
    val accountConfigService = AccountConfigServiceFake().also {
      runBlocking { it.setHardwareType(HardwareType.W3) }
    }
    val w3Commands = BitkeyW3CommandsFake(
      w1CommandsFake = nfcCommands,
      accountConfigService = accountConfigService,
      fakeHardwareKeyStore = fakeHardwareKeyStore,
      fakeHardwareSpendingWalletProvider = fakeHardwareSpendingWalletProvider,
      fakeHardwareStatesDao = fakeHardwareStatesDao,
      messageSigner = messageSigner,
      signatureUtils = signatureUtils
    )

    test("getAddress throws DescriptorNotLoaded when descriptor has not been delivered") {
      shouldThrow<NfcException.DescriptorNotLoaded> {
        w3Commands.getAddress(sessionFake, 0u)
      }
    }

    test("signTransaction throws DescriptorNotLoaded when descriptor has not been delivered") {
      shouldThrow<NfcException.DescriptorNotLoaded> {
        w3Commands.signTransaction(sessionFake, PsbtMock, SpendingKeysetMock)
      }
    }

    test("signTransaction records allowUnfinalized after descriptor delivery") {
      w3Commands.deliverDescriptor(sessionFake)

      w3Commands.signTransaction(
        session = sessionFake,
        psbt = PsbtMock,
        spendingKeyset = SpendingKeysetMock,
        displayPreference = null,
        allowUnfinalized = true
      )

      w3Commands.lastSignTransactionAllowUnfinalized.shouldBe(true)
    }

    test("getAddress works after descriptor delivery") {
      // Deliver the descriptor
      w3Commands.deliverDescriptor(sessionFake)

      // Now getAddress should work
      w3Commands.getAddress(sessionFake, 0u).shouldBeEqual("bc1q_fake_w3_0")
    }
  }

  context("W3 action proof signing") {
    val signedMessages = mutableListOf<ByteString>()
    val actionProofMessageSigner =
      object : MessageSigner {
        override fun sign(
          message: ByteString,
          key: Secp256k1PrivateKey,
        ): String {
          signedMessages.add(message)
          return "00"
        }
      }
    val actionProofSignatureUtils =
      object : SignatureUtils {
        override fun encodeSignatureToDer(compactSignature: ByteArray): ByteString = ByteString.EMPTY

        override fun decodeSignatureFromDer(derSignature: ByteString): ByteArray =
          ByteArray(64) { 0xAB.toByte() }
      }
    val accountConfigService = AccountConfigServiceFake().also {
      runBlocking { it.setHardwareType(HardwareType.W3) }
    }
    val w3Commands = BitkeyW3CommandsFake(
      w1CommandsFake = nfcCommands,
      accountConfigService = accountConfigService,
      fakeHardwareKeyStore = fakeHardwareKeyStore,
      fakeHardwareSpendingWalletProvider = fakeHardwareSpendingWalletProvider,
      fakeHardwareStatesDao = fakeHardwareStatesDao,
      messageSigner = actionProofMessageSigner,
      signatureUtils = actionProofSignatureUtils
    )

    test("W3 fake signs delay notify period with the firmware action and value") {
      signedMessages.clear()

      val bindings = "n=nonce,tb=token"
      val interaction = w3Commands.signActionProof(
        session = sessionFake,
        version = 1u,
        action = ActionProofAction.SET_DELAY_NOTIFY_PERIOD,
        value = "14",
        bindings = bindings
      )

      val prompt = interaction as HardwareInteraction.ConfirmWithEmulatedPrompt<String>
      val completed = prompt.approve.fetchResult(sessionFake, w3Commands)
        as HardwareInteraction.Completed<String>

      completed.result.shouldBeEqual("ab".repeat(64))
      signedMessages.single().utf8().split(0x1F.toChar()).shouldBeEqual(
        listOf(
          "ACTIONPROOF",
          "1",
          "SetDelayNotifyPeriod",
          "14",
          bindings
        )
      )
    }
  }

  context("W3 commands mock") {
    test("records delay notify period action proof calls") {
      val commands = W3NfcCommandsMock { name -> turbines.create("mock-$name") }
      val bindings = "n=nonce,tb=token"

      val completed = commands.signActionProof(
        session = sessionFake,
        version = 1u,
        action = ActionProofAction.SET_DELAY_NOTIFY_PERIOD,
        value = "14",
        bindings = bindings
      ) as HardwareInteraction.Completed<String>

      completed.result.shouldBeEqual("0".repeat(128))
      commands.signActionProofCalls.awaitItem().shouldBeEqual(
        SignActionProofCall(
          version = 1u,
          action = ActionProofAction.SET_DELAY_NOTIFY_PERIOD,
          value = "14",
          bindings = bindings
        )
      )
    }
  }
})
