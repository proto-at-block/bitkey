package build.wallet

import android.app.Application
import build.wallet.bdk.BdkAddressBuilderImpl
import build.wallet.bdk.BdkBlockchainFactoryImpl
import build.wallet.bdk.BdkBumpFeeTxBuilderFactoryImpl
import build.wallet.bdk.BdkDescriptorSecretKeyGeneratorImpl
import build.wallet.bdk.BdkMnemonicGeneratorImpl
import build.wallet.bdk.BdkPartiallySignedTransactionBuilderImpl
import build.wallet.bdk.BdkTxBuilderFactoryImpl
import build.wallet.bdk.BdkWalletFactoryImpl
import build.wallet.bugsnag.Bugsnag
import build.wallet.datadog.AndroidDatadogInitializer
import build.wallet.debug.StrictModeEnablerImpl
import build.wallet.di.AppComponent
import build.wallet.di.makeAppComponent
import build.wallet.encrypt.MessageSignerImpl
import build.wallet.encrypt.SignatureVerifierImpl
import build.wallet.firmware.FirmwareCommsLogBufferImpl
import build.wallet.firmware.HardwareAttestationImpl
import build.wallet.firmware.TeltraImpl
import build.wallet.logging.DatadogLogWriter
import build.wallet.platform.DeviceTokenConfigProviderImpl
import build.wallet.platform.PlatformContext
import build.wallet.platform.appVariant
import build.wallet.platform.config.AppId
import build.wallet.platform.config.DeviceOs

@Suppress("unused")
class BitkeyApplication : Application() {
  var isFreshLaunch = true
  lateinit var appComponent: AppComponent

  override fun onCreate() {
    super.onCreate()
    // Initialize crash reporters as soon as possible.
    AndroidDatadogInitializer(
      context = this,
      appVariant = appVariant
    ).initialize()
    Bugsnag.initialize(
      application = this,
      appVariant = appVariant
    )

    appComponent =
      makeAppComponent(
        appId = AppId(value = BuildConfig.APPLICATION_ID),
        appVariant = appVariant,
        appVersion = BuildConfig.VERSION_NAME,
        deviceOs = DeviceOs.Android,
        bdkAddressBuilder = BdkAddressBuilderImpl(),
        bdkBlockchainFactory = BdkBlockchainFactoryImpl(),
        bdkBumpFeeTxBuilderFactory = BdkBumpFeeTxBuilderFactoryImpl(),
        bdkDescriptorSecretKeyGenerator = BdkDescriptorSecretKeyGeneratorImpl(),
        bdkMnemonicGenerator = BdkMnemonicGeneratorImpl(),
        bdkPartiallySignedTransactionBuilder = BdkPartiallySignedTransactionBuilderImpl(),
        bdkTxBuilderFactory = BdkTxBuilderFactoryImpl(),
        bdkWalletFactory = BdkWalletFactoryImpl(),
        deviceTokenConfigProvider = DeviceTokenConfigProviderImpl(appVariant),
        logWritersProvider = {
          listOf(
            // TODO(W-1800): Use `LogWriterContextStore` in Shared code so we can get Debug Menu Logs from KMP
            DatadogLogWriter(it)
          )
        },
        messageSigner = MessageSignerImpl(),
        signatureVerifier = SignatureVerifierImpl(),
        platformContext = PlatformContext(this),
        teltra = TeltraImpl(),
        firmwareCommsLogBuffer = FirmwareCommsLogBufferImpl(),
        hardwareAttestation = HardwareAttestationImpl()
      )

    val strictModeEnabler = StrictModeEnablerImpl(appVariant)
    strictModeEnabler.configure()

    appComponent.bugsnagContext.configureCommonMetadata()
  }
}
