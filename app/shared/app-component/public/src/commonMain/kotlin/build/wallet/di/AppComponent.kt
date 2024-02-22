package build.wallet.di

import build.wallet.account.AccountRepository
import build.wallet.account.analytics.AppInstallationDao
import build.wallet.analytics.events.EventStore
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.PlatformInfoProvider
import build.wallet.analytics.events.SessionIdProvider
import build.wallet.auth.AccountAuthenticator
import build.wallet.auth.AppAuthKeyMessageSigner
import build.wallet.auth.AuthTokenDao
import build.wallet.auth.AuthTokensRepository
import build.wallet.auth.InactiveDeviceIsEnabledFeatureFlag
import build.wallet.availability.F8eAuthSignatureStatusProvider
import build.wallet.availability.NetworkReachabilityEventDao
import build.wallet.availability.NetworkReachabilityProvider
import build.wallet.bdk.bindings.BdkAddressBuilder
import build.wallet.bdk.bindings.BdkDescriptorSecretKeyGenerator
import build.wallet.bdk.bindings.BdkMnemonicGenerator
import build.wallet.bdk.bindings.BdkPartiallySignedTransactionBuilder
import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitcoin.bdk.BdkBlockchainProvider
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilder
import build.wallet.bitcoin.keys.ExtendedKeyGenerator
import build.wallet.bitcoin.sync.ElectrumReachability
import build.wallet.bitcoin.sync.ElectrumServerConfigRepository
import build.wallet.bitcoin.sync.ElectrumServerSettingProvider
import build.wallet.bitcoin.transactions.BitcoinTransactionAppSigner
import build.wallet.bitcoin.transactions.TransactionDetailDao
import build.wallet.bitcoin.wallet.SpendingWalletProvider
import build.wallet.bugsnag.BugsnagContext
import build.wallet.cloud.backup.CloudBackupHealthFeatureFlag
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.datadog.DatadogRumMonitor
import build.wallet.datadog.DatadogTracer
import build.wallet.encrypt.MessageSigner
import build.wallet.encrypt.Secp256k1KeyGenerator
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.debug.NetworkingDebugConfigRepository
import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagInitializer
import build.wallet.feature.FeatureFlagValue.BooleanFlag
import build.wallet.featureflag.AndroidTransitionsIsEnabledFeatureFlag
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.firmware.FirmwareMetadataDao
import build.wallet.firmware.FirmwareTelemetryUploader
import build.wallet.firmware.HardwareAttestation
import build.wallet.fwup.FwupDataDao
import build.wallet.fwup.FwupDataFetcher
import build.wallet.fwup.FwupProgressCalculator
import build.wallet.keybox.KeyboxDao
import build.wallet.keybox.config.TemplateKeyboxConfigDao
import build.wallet.keybox.keys.AppKeysGenerator
import build.wallet.keybox.keys.OnboardingAppKeyKeystore
import build.wallet.keybox.wallet.AppSpendingWalletProvider
import build.wallet.keybox.wallet.KeysetWalletProvider
import build.wallet.ktor.result.client.KtorLogLevelPolicy
import build.wallet.ldk.LdkNodeService
import build.wallet.logging.LogWriterContextStore
import build.wallet.logging.dev.LogStore
import build.wallet.memfault.MemfaultService
import build.wallet.money.MultipleFiatCurrencyEnabledFeatureFlag
import build.wallet.money.currency.FiatCurrencyDao
import build.wallet.money.display.BitcoinDisplayPreferenceRepository
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.nfc.haptics.NfcHaptics
import build.wallet.notifications.DeviceTokenManager
import build.wallet.platform.PlatformContext
import build.wallet.platform.config.AppId
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.DeviceOs
import build.wallet.platform.data.FileDirectoryProvider
import build.wallet.platform.data.FileManager
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.permissions.PermissionChecker
import build.wallet.platform.permissions.PushNotificationPermissionStatusProvider
import build.wallet.platform.random.Uuid
import build.wallet.platform.settings.LocaleCountryCodeProvider
import build.wallet.platform.settings.LocaleCurrencyCodeProvider
import build.wallet.platform.versions.OsVersionInfoProvider
import build.wallet.queueprocessor.PeriodicProcessor
import build.wallet.recovery.RecoveryDao
import build.wallet.recovery.emergencyaccess.EmergencyAccessFeatureFlag
import build.wallet.statemachine.send.FeeBumpIsAvailableFeatureFlag
import build.wallet.statemachine.settings.full.feedback.FeedbackFormNewUiEnabledFeatureFlag
import build.wallet.store.EncryptedKeyValueStoreFactory
import build.wallet.store.KeyValueStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.Clock
import kotlin.time.Duration

interface AppComponent {
  val accountAuthenticator: AccountAuthenticator
  val accountRepository: AccountRepository
  val allFeatureFlags: List<FeatureFlag<BooleanFlag>>
  val androidTransitionsIsEnabledFeatureFlag: AndroidTransitionsIsEnabledFeatureFlag
  val appAuthKeyMessageSigner: AppAuthKeyMessageSigner
  val authTokensRepository: AuthTokensRepository
  val appCoroutineScope: CoroutineScope
  val appId: AppId
  val deviceOs: DeviceOs
  val appInstallationDao: AppInstallationDao
  val appKeysGenerator: AppKeysGenerator
  val appPrivateKeyDao: AppPrivateKeyDao
  val appSpendingWalletProvider: AppSpendingWalletProvider
  val keysetWalletProvider: KeysetWalletProvider
  val appVariant: AppVariant
  val appVersion: String
  val authTokenDao: AuthTokenDao
  val bdkAddressBuilder: BdkAddressBuilder
  val bdkBlockchainProvider: BdkBlockchainProvider
  val bdkDescriptorSecretKeyGenerator: BdkDescriptorSecretKeyGenerator
  val bdkMnemonicGenerator: BdkMnemonicGenerator
  val bdkPartiallySignedTransactionBuilder: BdkPartiallySignedTransactionBuilder
  val bitcoinDisplayPreferenceRepository: BitcoinDisplayPreferenceRepository
  val bitcoinMultiSigDescriptorBuilder: BitcoinMultiSigDescriptorBuilder
  val bitcoinTransactionAppSigner: BitcoinTransactionAppSigner
  val bitkeyDatabaseProvider: BitkeyDatabaseProvider
  val bugsnagContext: BugsnagContext
  val clock: Clock
  val cloudBackupHealthFeatureFlag: CloudBackupHealthFeatureFlag
  val datadogRumMonitor: DatadogRumMonitor
  val datadogTracer: DatadogTracer
  val deviceInfoProvider: DeviceInfoProvider
  val deviceTokenManager: DeviceTokenManager
  val electrumReachability: ElectrumReachability
  val electrumServerDao: ElectrumServerConfigRepository
  val electrumServerSettingProvider: ElectrumServerSettingProvider
  val eventStore: EventStore
  val eventTracker: EventTracker
  val extendedKeyGenerator: ExtendedKeyGenerator
  val f8eHttpClient: F8eHttpClient
  val featureFlagInitializer: FeatureFlagInitializer
  val feeBumpIsAvailableFeatureFlag: FeeBumpIsAvailableFeatureFlag
  val feedbackFormNewUiEnabledFeatureFlag: FeedbackFormNewUiEnabledFeatureFlag
  val fiatCurrencyDao: FiatCurrencyDao
  val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository
  val fileManager: FileManager
  val fileDirectoryProvider: FileDirectoryProvider
  val firmwareDeviceInfoDao: FirmwareDeviceInfoDao
  val firmwareMetadataDao: FirmwareMetadataDao
  val firmwareTelemetryUploader: FirmwareTelemetryUploader
  val fwupDataFetcher: FwupDataFetcher
  val fwupDataDao: FwupDataDao
  val fwupProgressCalculator: FwupProgressCalculator
  val keyboxDao: KeyboxDao
  val keyValueStoreFactory: KeyValueStoreFactory
  val ktorLogLevelPolicy: KtorLogLevelPolicy
  val ldkNodeService: LdkNodeService
  val lightningIsAvailableFeatureFlag: FeatureFlag<BooleanFlag>
  val localeCountryCodeProvider: LocaleCountryCodeProvider
  val localeCurrencyCodeProvider: LocaleCurrencyCodeProvider
  val logStore: LogStore
  val logWriterContextStore: LogWriterContextStore
  val memfaultService: MemfaultService
  val messageSigner: MessageSigner
  val multipleFiatCurrencyEnabledFeatureFlag: MultipleFiatCurrencyEnabledFeatureFlag
  val emergencyAccessFeatureFlag: EmergencyAccessFeatureFlag
  val inactiveDeviceIsEnabledFeatureFlag: InactiveDeviceIsEnabledFeatureFlag
  val networkingDebugConfigRepository: NetworkingDebugConfigRepository
  val networkReachabilityEventDao: NetworkReachabilityEventDao
  val networkReachabilityProvider: NetworkReachabilityProvider
  val nfcHaptics: NfcHaptics
  val osVersionInfoProvider: OsVersionInfoProvider
  val periodicEventProcessor: PeriodicProcessor
  val periodicFirmwareCoredumpProcessor: PeriodicProcessor
  val periodicFirmwareTelemetryEventProcessor: PeriodicProcessor
  val permissionChecker: PermissionChecker
  val platformContext: PlatformContext
  val platformInfoProvider: PlatformInfoProvider
  val secp256k1KeyGenerator: Secp256k1KeyGenerator
  val purchaseFlowIsEnabledFeatureFlag: FeatureFlag<BooleanFlag>
  val pushNotificationPermissionStatusProvider: PushNotificationPermissionStatusProvider
  val recoveryDao: RecoveryDao
  val secureStoreFactory: EncryptedKeyValueStoreFactory
  val sessionIdProvider: SessionIdProvider
  val spendingWalletProvider: SpendingWalletProvider
  val templateKeyboxConfigDao: TemplateKeyboxConfigDao
  val transactionDetailDao: TransactionDetailDao
  val uuid: Uuid
  val onboardingAppKeyKeystore: OnboardingAppKeyKeystore
  val recoverySyncFrequency: Duration
  val hardwareAttestation: HardwareAttestation
  val f8eAuthSignatureStatusProvider: F8eAuthSignatureStatusProvider
}
