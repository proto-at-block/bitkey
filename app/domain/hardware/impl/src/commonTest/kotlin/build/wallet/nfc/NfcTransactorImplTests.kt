package build.wallet.nfc

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
import build.wallet.nfc.NfcSession.RequirePairedHardware.NotRequired
import build.wallet.nfc.platform.NfcSessionProvider
import build.wallet.sqldelight.inMemorySqlDriver
import com.github.michaelbull.result.Ok
import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalCoroutinesApi::class)
class NfcTransactorImplTests : FunSpec({
  coroutineTestScope = true

  lateinit var session: TestNfcSession
  val transactor = NfcTransactorImpl(
    commandsProvider = testNfcCommandsProvider(),
    sessionProvider = object : NfcSessionProvider {
      override fun get(parameters: NfcSession.Parameters): NfcSession =
        TestNfcSession(parameters).also { session = it }
    },
    interceptors = emptyList()
  )

  test("resets transacting state when transaction is cancelled") {
    val transactionStarted = CompletableDeferred<Unit>()

    val transactionJob = launch {
      transactor.transact(parameters = testParameters()) { _, _ ->
        transactionStarted.complete(Unit)
        awaitCancellation()
      }
    }

    testCoroutineScheduler.runCurrent()
    transactionStarted.await()
    transactor.isTransacting.shouldBe(true)

    transactionJob.cancelAndJoin()

    transactor.isTransacting.shouldBe(false)
    session.isClosed.shouldBe(true)
  }
})

private class TestNfcSession(
  override val parameters: NfcSession.Parameters,
) : NfcSession {
  override var message: String? = null
  var isClosed = false
    private set

  override suspend fun transceive(buffer: List<UByte>): List<UByte> = emptyList()

  override fun close() {
    isClosed = true
  }
}

private fun testParameters() =
  NfcSession.Parameters(
    isHardwareFake = false,
    hardwareType = HardwareType.W1,
    needsAuthentication = false,
    shouldLock = false,
    skipFirmwareTelemetry = false,
    asyncNfcSigning = false,
    nfcFlowName = "test-nfc-transactor",
    requirePairedHardware = NotRequired,
    onTagConnected = {},
    onTagDisconnected = {}
  )

private fun TestConfiguration.testNfcCommandsProvider(): NfcCommandsProvider {
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
  val fakeW1 = BitkeyW1CommandsFake(
    messageSigner = messageSigner,
    signatureUtils = signatureUtils,
    fakeHardwareKeyStore = fakeHardwareKeyStore,
    fakeHardwareSpendingWalletProvider = fakeHardwareSpendingWalletProvider,
    fakeHardwareStatesDao = fakeHardwareStatesDao
  )
  val fakeW3 = BitkeyW3CommandsFake(
    w1CommandsFake = fakeW1,
    accountConfigService = AccountConfigServiceFake().also {
      runBlocking { it.setHardwareType(HardwareType.W3) }
    },
    fakeHardwareKeyStore = fakeHardwareKeyStore,
    fakeHardwareSpendingWalletProvider = fakeHardwareSpendingWalletProvider,
    fakeHardwareStatesDao = fakeHardwareStatesDao,
    messageSigner = messageSigner,
    signatureUtils = signatureUtils
  )

  return NfcCommandsProvider(
    w1Impl = fakeW1,
    w3Impl = fakeW3,
    w1Fake = fakeW1,
    w3Fake = fakeW3
  )
}
