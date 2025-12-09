package build.wallet.di

import bitkey.bugsnag.BugsnagContext
import bitkey.datadog.DatadogRumMonitor
import bitkey.datadog.DatadogTracer
import build.wallet.analytics.events.EventTracker
import build.wallet.bdk.bindings.*
import build.wallet.bitcoin.descriptor.FrostWalletDescriptorFactory
import build.wallet.bitcoin.lightning.LightningInvoiceParser
import build.wallet.chaincode.delegation.ChaincodeDelegationServerKeyGenerator
import build.wallet.chaincode.delegation.PsbtUtils
import build.wallet.chaincode.delegation.PublicKeyUtils
import build.wallet.cloud.store.CloudFileStore
import build.wallet.crypto.NoiseInitiator
import build.wallet.crypto.Spake2
import build.wallet.crypto.WsmVerifier
import build.wallet.encrypt.*
import build.wallet.firmware.FirmwareCommsLogBuffer
import build.wallet.firmware.HardwareAttestation
import build.wallet.firmware.Teltra
import build.wallet.frost.ShareGenerator
import build.wallet.inappsecurity.BiometricPreference
import build.wallet.logging.LogWriterContextStore
import build.wallet.logging.LoggerInitializer
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.NfcSessionProvider
import build.wallet.notifications.DeviceTokenManager
import build.wallet.phonenumber.lib.PhoneNumberLibBindings
import build.wallet.platform.app.AppSessionManager
import build.wallet.platform.biometrics.BiometricPrompter
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.DeviceTokenConfigProvider
import build.wallet.platform.data.FileDirectoryProvider
import build.wallet.platform.data.FileManager
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.haptics.Haptics
import build.wallet.platform.pdf.PdfAnnotatorFactory
import build.wallet.platform.permissions.PushNotificationPermissionStatusProvider
import build.wallet.platform.sensor.Accelerometer
import build.wallet.platform.settings.SystemSettingsLauncher
import build.wallet.platform.sharing.SharingManager
import build.wallet.platform.web.InAppBrowserNavigator
import co.touchlab.kermit.LogWriter
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent.CreateComponent

/**
 * Component bound to [AppScope] to be used by Swift iOS code. Tied to iOS app's [AppDelegate] instance.
 *
 * Constructor accepts Swift implementations of KMP interfaces which we cannot implement
 * directly in Kotlin - for example, classes from Rust bindings and various iOS specific APIs which
 * are not in K/N (`iosMain`).
 *
 * The [IosAppComponent] can be created using [create] function.
 *
 * To add a new implementation:
 * 1. Add a `@get:Provides val` for a KMP interface
 * 2. Add the interface to [create] as well
 * 3. Pass Swift implementation to [create] in [AppContext.swift]
 * 4. The interface is now available to injected into other dependencies
 *
 * Important: order of dependencies in [.IosAppComponent] constructor and [create] function has to match.
 */
@MergeComponent(AppScope::class)
@SingleIn(AppScope::class)
abstract class IosAppComponent internal constructor(
  @get:Provides val appVariant: AppVariant,
  @get:Provides val bdkAddressBuilder: BdkAddressBuilder,
  @get:Provides val bdkBlockchainFactory: BdkBlockchainFactory,
  @get:Provides val bdkBumpFeeTxBuilderFactory: BdkBumpFeeTxBuilderFactory,
  @get:Provides val bdkDescriptorSecretKeyGenerator: BdkDescriptorSecretKeyGenerator,
  @get:Provides val bdkMnemonicGenerator: BdkMnemonicGenerator,
  @get:Provides val bdkPartiallySignedTransactionBuilder: BdkPartiallySignedTransactionBuilder,
  @get:Provides val signatureUtils: SignatureUtils,
  @get:Provides val bdkTxBuilderFactory: BdkTxBuilderFactory,
  @get:Provides val bdkWalletFactory: BdkWalletFactory,
  @get:Provides val cloudFileStore: CloudFileStore,
  @get:Provides val cryptoBox: CryptoBox,
  @get:Provides val datadogRumMonitor: DatadogRumMonitor,
  @get:Provides val datadogTracer: DatadogTracer,
  @get:Provides val deviceTokenConfigProvider: DeviceTokenConfigProvider,
  @get:Provides val fileManagerProvider: (FileDirectoryProvider) -> FileManager,
  @get:Provides val firmwareCommsLogBuffer: FirmwareCommsLogBuffer,
  @get:Provides val frostWalletDescriptorFactory: FrostWalletDescriptorFactory,
  @get:Provides val hardwareAttestation: HardwareAttestation,
  @get:Provides val inAppBrowserNavigator: InAppBrowserNavigator,
  @get:Provides val lightningInvoiceParser: LightningInvoiceParser,
  @get:Provides val logWritersProvider: (LogWriterContextStore) -> List<LogWriter>,
  @get:Provides val messageSigner: MessageSigner,
  @get:Provides @get:Impl val nfcCommandsImpl: NfcCommands,
  @get:Provides @get:W3 val nfcCommandsW3: NfcCommands,
  @get:Provides val nfcSessionProvider: NfcSessionProvider,
  @get:Provides val pdfAnnotatorFactory: PdfAnnotatorFactory,
  @get:Provides val phoneNumberLibBindings: PhoneNumberLibBindings,
  @get:Provides val psbtUtils: PsbtUtils,
  @get:Provides val secp256k1KeyGenerator: Secp256k1KeyGenerator,
  @get:Provides val shareGenerator: ShareGenerator,
  @get:Provides val sharingManager: SharingManager,
  @get:Provides val signatureVerifier: SignatureVerifier,
  @get:Provides val spake2: Spake2,
  @get:Provides val symmetricKeyEncryptor: SymmetricKeyEncryptor,
  @get:Provides val symmetricKeyGenerator: SymmetricKeyGenerator,
  @get:Provides val systemSettingsLauncher: SystemSettingsLauncher,
  @get:Provides val teltra: Teltra,
  @get:Provides val wsmVerifier: WsmVerifier,
  @get:Provides val xChaCha20Poly1305: XChaCha20Poly1305,
  @get:Provides val xNonceGenerator: XNonceGenerator,
  @get:Provides val noiseInitiator: NoiseInitiator,
  @get:Provides val p256Box: P256Box,
  @get:Provides val chaincodeDelegationServerKeyGenerator: ChaincodeDelegationServerKeyGenerator,
  @get:Provides val publicKeyUtils: PublicKeyUtils,
) : IosActivityComponent.Factory {
  /**
   * Expose dependencies that we want to access at runtime from Swift code.
   */
  abstract val appSessionManager: AppSessionManager
  abstract val biometricPreference: BiometricPreference
  abstract val biometricPrompter: BiometricPrompter
  abstract val bugsnagContext: BugsnagContext
  abstract val deviceTokenManager: DeviceTokenManager
  abstract val eventTracker: EventTracker
  abstract val fileDirectoryProvider: FileDirectoryProvider
  abstract val loggerInitializer: LoggerInitializer
  abstract val pushNotificationPermissionStatusProvider: PushNotificationPermissionStatusProvider
  abstract val deviceInfoProvider: DeviceInfoProvider
  abstract val accelerometer: Accelerometer

  abstract val haptics: Haptics
}

/**
 * The `actual fun` will be generated for each iOS specific target.
 * See [MergeComponent] for more details.
 */
@CreateComponent
expect fun create(
  appVariant: AppVariant,
  bdkAddressBuilder: BdkAddressBuilder,
  bdkBlockchainFactory: BdkBlockchainFactory,
  bdkBumpFeeTxBuilderFactory: BdkBumpFeeTxBuilderFactory,
  bdkDescriptorSecretKeyGenerator: BdkDescriptorSecretKeyGenerator,
  bdkMnemonicGenerator: BdkMnemonicGenerator,
  bdkPartiallySignedTransactionBuilder: BdkPartiallySignedTransactionBuilder,
  signatureUtils: SignatureUtils,
  bdkTxBuilderFactory: BdkTxBuilderFactory,
  bdkWalletFactory: BdkWalletFactory,
  cloudFileStore: CloudFileStore,
  cryptoBox: CryptoBox,
  datadogRumMonitor: DatadogRumMonitor,
  datadogTracer: DatadogTracer,
  deviceTokenConfigProvider: DeviceTokenConfigProvider,
  fileManagerProvider: (FileDirectoryProvider) -> FileManager,
  firmwareCommsLogBuffer: FirmwareCommsLogBuffer,
  frostWalletDescriptorFactory: FrostWalletDescriptorFactory,
  hardwareAttestation: HardwareAttestation,
  inAppBrowserNavigator: InAppBrowserNavigator,
  lightningInvoiceParser: LightningInvoiceParser,
  logWritersProvider: (LogWriterContextStore) -> List<LogWriter>,
  messageSigner: MessageSigner,
  nfcCommandsImpl: NfcCommands,
  nfcCommandsW3: NfcCommands,
  nfcSessionProvider: NfcSessionProvider,
  pdfAnnotatorFactory: PdfAnnotatorFactory,
  phoneNumberLibBindings: PhoneNumberLibBindings,
  psbtUtils: PsbtUtils,
  secp256k1KeyGenerator: Secp256k1KeyGenerator,
  shareGenerator: ShareGenerator,
  sharingManager: SharingManager,
  signatureVerifier: SignatureVerifier,
  spake2: Spake2,
  symmetricKeyEncryptor: SymmetricKeyEncryptor,
  symmetricKeyGenerator: SymmetricKeyGenerator,
  systemSettingsLauncher: SystemSettingsLauncher,
  teltra: Teltra,
  wsmVerifier: WsmVerifier,
  xChaCha20Poly1305: XChaCha20Poly1305,
  xNonceGenerator: XNonceGenerator,
  noiseInitiator: NoiseInitiator,
  p256Box: P256Box,
  chaincodeDelegationServerKeyGenerator: ChaincodeDelegationServerKeyGenerator,
  publicKeyUtils: PublicKeyUtils,
): IosAppComponent
