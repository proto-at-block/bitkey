package build.wallet.di

import build.wallet.bdk.BdkDescriptorSecretKeyGeneratorImpl
import build.wallet.bdk.BdkMnemonicGeneratorImpl
import build.wallet.bdk.bindings.BdkAddressBuilder
import build.wallet.bdk.bindings.BdkBlockchainFactory
import build.wallet.bdk.bindings.BdkBumpFeeTxBuilderFactory
import build.wallet.bdk.bindings.BdkPartiallySignedTransactionBuilder
import build.wallet.bdk.bindings.BdkTxBuilderFactory
import build.wallet.bdk.bindings.BdkWalletFactory
import build.wallet.crypto.Spake2Impl
import build.wallet.crypto.WsmVerifierImpl
import build.wallet.datadog.DatadogRumMonitor
import build.wallet.datadog.FakeDatadogTracerImpl
import build.wallet.encrypt.*
import build.wallet.firmware.FirmwareCommsLogBufferMock
import build.wallet.firmware.HardwareAttestationMock
import build.wallet.firmware.Teltra
import build.wallet.logging.dev.LogStoreNoop
import build.wallet.money.exchange.ExchangeRateF8eClient
import build.wallet.phonenumber.PhoneNumberLibBindingsImpl
import build.wallet.platform.PlatformContext
import build.wallet.platform.config.AppId
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.DeviceOs
import build.wallet.platform.config.DeviceTokenConfigProvider
import build.wallet.platform.data.FileDirectoryProviderImpl
import build.wallet.platform.data.FileManagerImpl
import build.wallet.time.Delayer
import kotlin.time.Duration.Companion.seconds

fun makeAppComponent(
  bdkAddressBuilder: BdkAddressBuilder,
  bdkBlockchainFactory: BdkBlockchainFactory,
  bdkBumpFeeTxBuilderFactory: BdkBumpFeeTxBuilderFactory,
  bdkPartiallySignedTransactionBuilder: BdkPartiallySignedTransactionBuilder,
  bdkTxBuilderFactory: BdkTxBuilderFactory,
  bdkWalletFactory: BdkWalletFactory,
  datadogRumMonitor: DatadogRumMonitor,
  delayer: Delayer,
  deviceTokenConfigProvider: DeviceTokenConfigProvider,
  exchangeRateF8eClient: ExchangeRateF8eClient,
  messageSigner: MessageSigner,
  signatureVerifier: SignatureVerifier,
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
  val firmwareCommsLogBuffer = FirmwareCommsLogBufferMock()
  val wsmVerifier = WsmVerifierImpl()

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
    delayer = delayer,
    deviceTokenConfigProvider = deviceTokenConfigProvider,
    fileDirectoryProvider = fileDirectoryProvider,
    fileManager = fileManager,
    logStore = LogStoreNoop,
    logWritersProvider = { emptyList() },
    messageSigner = messageSigner,
    signatureVerifier = signatureVerifier,
    platformContext = platformContext,
    phoneNumberLibBindings = PhoneNumberLibBindingsImpl(),
    secp256k1KeyGenerator = publicKeyGenerator,
    recoverySyncFrequency = 5.seconds,
    hardwareAttestation = hardwareAttestation,
    firmwareCommsLogBuffer = firmwareCommsLogBuffer,
    teltra = teltra,
    wsmVerifier = wsmVerifier,
    exchangeRateF8eClient = exchangeRateF8eClient,
    symmetricKeyEncryptor = SymmetricKeyEncryptorImpl(),
    symmetricKeyGenerator = SymmetricKeyGeneratorImpl(),
    xChaCha20Poly1305 = XChaCha20Poly1305Impl(),
    xNonceGenerator = XNonceGeneratorImpl(),
    spake2 = Spake2Impl(),
    cryptoBox = CryptoBoxImpl()
  )
}
