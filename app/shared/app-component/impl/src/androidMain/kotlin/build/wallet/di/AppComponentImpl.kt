package build.wallet.di

import build.wallet.bdk.BdkDescriptorSecretKeyGeneratorImpl
import build.wallet.bdk.BdkMnemonicGeneratorImpl
import build.wallet.bdk.bindings.BdkAddressBuilder
import build.wallet.bdk.bindings.BdkBlockchainFactory
import build.wallet.bdk.bindings.BdkBumpFeeTxBuilderFactory
import build.wallet.bdk.bindings.BdkPartiallySignedTransactionBuilder
import build.wallet.bdk.bindings.BdkTxBuilderFactory
import build.wallet.bdk.bindings.BdkWalletFactory
import build.wallet.crypto.WsmVerifierImpl
import build.wallet.datadog.DatadogRumMonitorImpl
import build.wallet.datadog.DatadogTracerImpl
import build.wallet.encrypt.MessageSigner
import build.wallet.encrypt.Secp256k1KeyGeneratorImpl
import build.wallet.encrypt.SignatureVerifier
import build.wallet.firmware.HardwareAttestation
import build.wallet.firmware.Teltra
import build.wallet.logging.LogWriterContextStore
import build.wallet.logging.dev.LogStoreInMemoryImpl
import build.wallet.logging.prod.BoundedInMemoryLogStoreImpl
import build.wallet.platform.PlatformContext
import build.wallet.platform.config.AppId
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.DeviceOs
import build.wallet.platform.config.DeviceTokenConfigProvider
import build.wallet.platform.data.FileDirectoryProviderImpl
import build.wallet.platform.data.FileManagerImpl
import build.wallet.time.Delayer
import co.touchlab.kermit.LogWriter

fun makeAppComponent(
  appId: AppId,
  appVariant: AppVariant,
  appVersion: String,
  bdkAddressBuilder: BdkAddressBuilder,
  bdkBlockchainFactory: BdkBlockchainFactory,
  bdkBumpFeeTxBuilderFactory: BdkBumpFeeTxBuilderFactory,
  bdkDescriptorSecretKeyGenerator: BdkDescriptorSecretKeyGeneratorImpl,
  bdkMnemonicGenerator: BdkMnemonicGeneratorImpl,
  bdkPartiallySignedTransactionBuilder: BdkPartiallySignedTransactionBuilder,
  bdkTxBuilderFactory: BdkTxBuilderFactory,
  bdkWalletFactory: BdkWalletFactory,
  deviceTokenConfigProvider: DeviceTokenConfigProvider,
  logWritersProvider: (LogWriterContextStore) -> List<LogWriter>,
  messageSigner: MessageSigner,
  signatureVerifier: SignatureVerifier,
  platformContext: PlatformContext,
  teltra: Teltra,
  hardwareAttestation: HardwareAttestation,
  deviceOs: DeviceOs,
): AppComponentImpl {
  val datadogTracer = DatadogTracerImpl()
  val datadogRumMonitor = DatadogRumMonitorImpl()
  val fileDirectoryProvider = FileDirectoryProviderImpl(platformContext)
  val fileManager = FileManagerImpl(fileDirectoryProvider)
  val publicKeyGenerator = Secp256k1KeyGeneratorImpl()
  val wsmVerifier = WsmVerifierImpl()

  val logStore = when (appVariant) {
    AppVariant.Development -> LogStoreInMemoryImpl()
    AppVariant.Team -> LogStoreInMemoryImpl()
    AppVariant.Beta -> LogStoreInMemoryImpl()
    AppVariant.Customer -> BoundedInMemoryLogStoreImpl()
    AppVariant.Emergency -> BoundedInMemoryLogStoreImpl()
  }

  return AppComponentImpl(
    appId = appId,
    appVariant = appVariant,
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
    delayer = Delayer.Default,
    deviceTokenConfigProvider = deviceTokenConfigProvider,
    fileDirectoryProvider = fileDirectoryProvider,
    fileManager = fileManager,
    logStore = logStore,
    logWritersProvider = logWritersProvider,
    messageSigner = messageSigner,
    signatureVerifier = signatureVerifier,
    platformContext = platformContext,
    secp256k1KeyGenerator = publicKeyGenerator,
    teltra = teltra,
    hardwareAttestation = hardwareAttestation,
    deviceOs = deviceOs,
    wsmVerifier = wsmVerifier
  )
}
