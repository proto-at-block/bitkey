package build.wallet.di

import build.wallet.bdk.BdkDescriptorSecretKeyGeneratorImpl
import build.wallet.bdk.BdkMnemonicGeneratorImpl
import build.wallet.bdk.bindings.BdkAddressBuilder
import build.wallet.bdk.bindings.BdkBlockchainFactory
import build.wallet.bdk.bindings.BdkBumpFeeTxBuilderFactory
import build.wallet.bdk.bindings.BdkPartiallySignedTransactionBuilder
import build.wallet.bdk.bindings.BdkTxBuilderFactory
import build.wallet.bdk.bindings.BdkWalletFactory
import build.wallet.datadog.DatadogRumMonitor
import build.wallet.datadog.FakeDatadogTracerImpl
import build.wallet.encrypt.MessageSigner
import build.wallet.encrypt.Secp256k1KeyGeneratorImpl
import build.wallet.firmware.HardwareAttestationMock
import build.wallet.firmware.Teltra
import build.wallet.platform.PlatformContext
import build.wallet.platform.config.AppId
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.DeviceOs
import build.wallet.platform.config.DeviceTokenConfigProvider
import build.wallet.platform.data.FileDirectoryProviderImpl
import build.wallet.platform.data.FileManagerImpl
import kotlin.time.Duration.Companion.seconds

fun makeAppComponent(
  bdkAddressBuilder: BdkAddressBuilder,
  bdkBlockchainFactory: BdkBlockchainFactory,
  bdkBumpFeeTxBuilderFactory: BdkBumpFeeTxBuilderFactory,
  bdkPartiallySignedTransactionBuilder: BdkPartiallySignedTransactionBuilder,
  bdkTxBuilderFactory: BdkTxBuilderFactory,
  bdkWalletFactory: BdkWalletFactory,
  datadogRumMonitor: DatadogRumMonitor,
  deviceTokenConfigProvider: DeviceTokenConfigProvider,
  messageSigner: MessageSigner,
  platformContext: PlatformContext,
  teltra: Teltra,
): AppComponentImpl {
  val appId = AppId(value = "build.wallet.cli")
  val appVariant = AppVariant.Development
  val appVersion = "N/A"
  val bdkDescriptorSecretKeyGenerator = BdkDescriptorSecretKeyGeneratorImpl()
  val bdkMnemonicGenerator = BdkMnemonicGeneratorImpl()
  val datadogTracer = FakeDatadogTracerImpl()
  val fileDirectoryProvider = FileDirectoryProviderImpl(platformContext)
  val fileManager = FileManagerImpl(fileDirectoryProvider)
  val publicKeyGenerator = Secp256k1KeyGeneratorImpl()
  val hardwareAttestation = HardwareAttestationMock()

  return AppComponentImpl(
    appId = appId,
    appVariant = appVariant,
    deviceOs = DeviceOs.Other,
    appVersion = appVersion,
    bdkAddressBuilder = bdkAddressBuilder,
    bdkBlockchainFactory = bdkBlockchainFactory,
    bdkBumpFeeTxBuilderFactory = bdkBumpFeeTxBuilderFactory,
    bdkDescriptorSecretKeyGenerator = bdkDescriptorSecretKeyGenerator,
    bdkMnemonicGenerator = bdkMnemonicGenerator,
    bdkPartiallySignedTransactionBuilder = bdkPartiallySignedTransactionBuilder,
    bdkTxBuilderFactory = bdkTxBuilderFactory,
    bdkWalletFactory = bdkWalletFactory,
    datadogRumMonitor = datadogRumMonitor,
    datadogTracer = datadogTracer,
    deviceTokenConfigProvider = deviceTokenConfigProvider,
    fileDirectoryProvider = fileDirectoryProvider,
    fileManager = fileManager,
    logWritersProvider = { emptyList() },
    messageSigner = messageSigner,
    platformContext = platformContext,
    secp256k1KeyGenerator = publicKeyGenerator,
    recoverySyncFrequency = 5.seconds,
    hardwareAttestation = hardwareAttestation,
    teltra = teltra
  )
}
