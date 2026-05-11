package build.wallet.nfc

import bitkey.account.AccountConfigServiceFake
import bitkey.account.HardwareType
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilderMock
import build.wallet.bitcoin.wallet.SpendingWalletFake
import build.wallet.bitcoin.wallet.SpendingWalletV2ProviderMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.encrypt.MessageSignerFake
import build.wallet.encrypt.SignatureUtilsMock
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.Bdk2FeatureFlag
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.nfc.NfcSession.RequirePairedHardware.NotRequired
import build.wallet.nfc.platform.actualHardwareType
import build.wallet.nfc.platform.detectedDeviceInfo
import build.wallet.sqldelight.inMemorySqlDriver
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking

class NfcCommandsProviderTests : FunSpec({
  val realW1 = NfcCommandsMock { name -> turbines.create("w1-$name") }
  val realW3 = W3NfcCommandsMock { name -> turbines.create("w3-$name") }

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

  val provider = NfcCommandsProvider(realW1, realW3, fakeW1, fakeW3)

  beforeTest {
    realW1.reset()
    realW3.reset()
  }

  test("real continuation session reuses resolved device identity without losing live device info reads") {
    val resolvedW3DeviceInfo =
      FirmwareDeviceInfoMock.copy(hwRevision = "w3a-core-evt", serial = "w3-resolved", version = "1.2.3")
    val liveW3DeviceInfo = resolvedW3DeviceInfo.copy(version = "9.9.9")
    realW3.deviceInfoResult = liveW3DeviceInfo

    val session = NfcSessionFake(
      NfcSession.Parameters(
        isHardwareFake = false,
        hardwareType = HardwareType.W1,
        resolvedDeviceInfoOverride = resolvedW3DeviceInfo,
        needsAuthentication = false,
        shouldLock = false,
        skipFirmwareTelemetry = false,
        nfcFlowName = "sign-transaction-confirmation",
        requirePairedHardware = NotRequired,
        asyncNfcSigning = false,
        onTagConnected = {},
        onTagDisconnected = {}
      )
    )

    val commands = provider.forSession(session.parameters)

    commands.detectedDeviceInfo(session).shouldBe(resolvedW3DeviceInfo)
    commands.actualHardwareType(session).shouldBe(HardwareType.W3)
    realW1.getDeviceInfoCalls.expectNoEvents()

    commands.getDeviceInfo(session).shouldBe(liveW3DeviceInfo)
    realW1.getDeviceInfoCalls.expectNoEvents()
    realW3.getDeviceInfoCalls.awaitItem().shouldBe(liveW3DeviceInfo)
  }
})
