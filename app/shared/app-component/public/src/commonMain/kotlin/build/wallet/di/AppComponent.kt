package build.wallet.di

import build.wallet.account.AccountService
import build.wallet.account.analytics.AppInstallationDao
import build.wallet.activity.TransactionsActivityService
import build.wallet.analytics.events.*
import build.wallet.auth.AccountAuthenticator
import build.wallet.auth.AppAuthKeyMessageSigner
import build.wallet.auth.AuthTokenDao
import build.wallet.auth.AuthTokensRepository
import build.wallet.availability.AppFunctionalityService
import build.wallet.availability.F8eAuthSignatureStatusProvider
import build.wallet.availability.F8eNetworkReachabilityService
import build.wallet.availability.NetworkReachabilityProvider
import build.wallet.bdk.bindings.BdkAddressBuilder
import build.wallet.bdk.bindings.BdkDescriptorSecretKeyGenerator
import build.wallet.bdk.bindings.BdkMnemonicGenerator
import build.wallet.bdk.bindings.BdkPartiallySignedTransactionBuilder
import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitcoin.address.BitcoinAddressService
import build.wallet.bitcoin.bdk.BdkBlockchainProvider
import build.wallet.bitcoin.blockchain.BitcoinBlockchain
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilder
import build.wallet.bitcoin.export.ExportTransactionsService
import build.wallet.bitcoin.export.ExportWatchingDescriptorService
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimator
import build.wallet.bitcoin.keys.ExtendedKeyGenerator
import build.wallet.bitcoin.sync.ElectrumConfigService
import build.wallet.bitcoin.sync.ElectrumReachability
import build.wallet.bitcoin.sync.ElectrumServerConfigRepository
import build.wallet.bitcoin.sync.ElectrumServerSettingProvider
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.bitcoin.transactions.FeeBumpAllowShrinkingChecker
import build.wallet.bitcoin.transactions.OutgoingTransactionDetailDao
import build.wallet.bitcoin.utxo.UtxoConsolidationService
import build.wallet.bitcoin.wallet.SpendingWalletProvider
import build.wallet.bugsnag.BugsnagContext
import build.wallet.configuration.MobilePayFiatConfigService
import build.wallet.crypto.Spake2
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.datadog.DatadogRumMonitor
import build.wallet.datadog.DatadogTracer
import build.wallet.debug.DebugOptionsService
import build.wallet.encrypt.*
import build.wallet.f8e.auth.AuthF8eClient
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.debug.NetworkingDebugService
import build.wallet.f8e.featureflags.FeatureFlagsF8eClient
import build.wallet.f8e.mobilepay.MobilePaySigningF8eClient
import build.wallet.f8e.notifications.NotificationTouchpointF8eClient
import build.wallet.f8e.recovery.ListKeysetsF8eClient
import build.wallet.f8e.relationships.RelationshipsF8eClient
import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagService
import build.wallet.feature.flags.*
import build.wallet.firmware.*
import build.wallet.frost.ShareGeneratorFactory
import build.wallet.fwup.FirmwareDataService
import build.wallet.fwup.FwupDataDao
import build.wallet.fwup.FwupDataFetcher
import build.wallet.fwup.FwupProgressCalculator
import build.wallet.inappsecurity.BiometricPreference
import build.wallet.inheritance.InheritanceClaimsDao
import build.wallet.inheritance.InheritanceService
import build.wallet.keybox.KeyboxDao
import build.wallet.keybox.keys.AppKeysGenerator
import build.wallet.keybox.keys.OnboardingAppKeyKeystore
import build.wallet.keybox.wallet.AppSpendingWalletProvider
import build.wallet.keybox.wallet.KeysetWalletProvider
import build.wallet.limit.MobilePayService
import build.wallet.logging.LogWriterContextStore
import build.wallet.logging.dev.LogStore
import build.wallet.memfault.MemfaultClient
import build.wallet.money.currency.FiatCurrenciesService
import build.wallet.money.currency.FiatCurrencyDao
import build.wallet.money.display.BitcoinDisplayPreferenceRepository
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.exchange.ExchangeRateF8eClient
import build.wallet.money.exchange.ExchangeRateService
import build.wallet.nfc.haptics.NfcHaptics
import build.wallet.notifications.DeviceTokenManager
import build.wallet.notifications.NotificationTouchpointDao
import build.wallet.notifications.NotificationTouchpointService
import build.wallet.notifications.RegisterWatchAddressPeriodicProcessor
import build.wallet.partnerships.PartnershipPurchaseService
import build.wallet.partnerships.PartnershipTransactionsService
import build.wallet.phonenumber.PhoneNumberValidator
import build.wallet.phonenumber.lib.PhoneNumberLibBindings
import build.wallet.platform.PlatformContext
import build.wallet.platform.app.AppSessionManager
import build.wallet.platform.config.AppId
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVersion
import build.wallet.platform.config.DeviceOs
import build.wallet.platform.data.FileDirectoryProvider
import build.wallet.platform.data.FileManager
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.haptics.Haptics
import build.wallet.platform.permissions.PermissionChecker
import build.wallet.platform.permissions.PushNotificationPermissionStatusProvider
import build.wallet.platform.random.UuidGenerator
import build.wallet.platform.settings.CountryCodeGuesser
import build.wallet.platform.settings.LocaleCountryCodeProvider
import build.wallet.platform.settings.LocaleCurrencyCodeProvider
import build.wallet.platform.settings.LocaleLanguageCodeProvider
import build.wallet.platform.versions.OsVersionInfoProvider
import build.wallet.pricechart.BitcoinPriceCardPreference
import build.wallet.recovery.RecoveryDao
import build.wallet.recovery.socrec.*
import build.wallet.relationships.*
import build.wallet.sqldelight.DatabaseIntegrityChecker
import build.wallet.store.EncryptedKeyValueStoreFactory
import build.wallet.store.KeyValueStoreFactory
import build.wallet.time.Delayer
import build.wallet.worker.AppWorkerExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.Clock
import kotlin.time.Duration

interface AppComponent {
  val accountAuthenticator: AccountAuthenticator
  val accountService: AccountService
  val featureFlags: List<FeatureFlag<*>>
  val appAuthKeyMessageSigner: AppAuthKeyMessageSigner
  val appFunctionalityService: AppFunctionalityService
  val registerWatchAddressPeriodicProcessor: RegisterWatchAddressPeriodicProcessor
  val appWorkerExecutor: AppWorkerExecutor
  val authF8eClient: AuthF8eClient
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
  val appVersion: AppVersion
  val authTokenDao: AuthTokenDao
  val bdkAddressBuilder: BdkAddressBuilder
  val bdkBlockchainProvider: BdkBlockchainProvider
  val bdkDescriptorSecretKeyGenerator: BdkDescriptorSecretKeyGenerator
  val bdkMnemonicGenerator: BdkMnemonicGenerator
  val bdkPartiallySignedTransactionBuilder: BdkPartiallySignedTransactionBuilder
  val bitcoinDisplayPreferenceRepository: BitcoinDisplayPreferenceRepository
  val bitcoinBlockchain: BitcoinBlockchain
  val bitcoinMultiSigDescriptorBuilder: BitcoinMultiSigDescriptorBuilder
  val bitkeyDatabaseProvider: BitkeyDatabaseProvider
  val bugsnagContext: BugsnagContext
  val clock: Clock
  val countryCodeGuesser: CountryCodeGuesser
  val cryptoBox: CryptoBox
  val currencyConverter: CurrencyConverter
  val datadogRumMonitor: DatadogRumMonitor
  val datadogTracer: DatadogTracer
  val delayer: Delayer
  val deviceInfoProvider: DeviceInfoProvider
  val deviceTokenManager: DeviceTokenManager
  val electrumConfigService: ElectrumConfigService
  val electrumReachability: ElectrumReachability
  val electrumServerConfigRepository: ElectrumServerConfigRepository
  val electrumServerSettingProvider: ElectrumServerSettingProvider
  val endorseTrustedContactsService: EndorseTrustedContactsService
  val eventStore: EventStore
  val eventTracker: EventTracker
  val exchangeRateService: ExchangeRateService
  val exportToolsFeatureFlag: ExportToolsFeatureFlag
  val exportWatchingDescriptorService: ExportWatchingDescriptorService
  val extendedKeyGenerator: ExtendedKeyGenerator
  val exportTransactionsService: ExportTransactionsService
  val inviteCodeLoader: InviteCodeLoader
  val f8eHttpClient: F8eHttpClient
  val featureFlagService: FeatureFlagService
  val feeBumpIsAvailableFeatureFlag: FeeBumpIsAvailableFeatureFlag
  val fiatCurrencyDao: FiatCurrencyDao
  val fiatCurrenciesService: FiatCurrenciesService
  val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository
  val fileManager: FileManager
  val fileDirectoryProvider: FileDirectoryProvider
  val firmwareDataService: FirmwareDataService
  val firmwareDeviceInfoDao: FirmwareDeviceInfoDao
  val firmwareMetadataDao: FirmwareMetadataDao
  val firmwareTelemetryUploader: FirmwareTelemetryUploader
  val firmwareCommsLogBuffer: FirmwareCommsLogBuffer
  val fwupDataFetcher: FwupDataFetcher
  val fwupDataDao: FwupDataDao
  val fwupProgressCalculator: FwupProgressCalculator
  val featureFlagsF8eClient: FeatureFlagsF8eClient
  val keyboxDao: KeyboxDao
  val keyValueStoreFactory: KeyValueStoreFactory
  val listKeysetsF8eClient: ListKeysetsF8eClient
  val localeCountryCodeProvider: LocaleCountryCodeProvider
  val localeCurrencyCodeProvider: LocaleCurrencyCodeProvider
  val localeLanguageCodeProvider: LocaleLanguageCodeProvider
  val logStore: LogStore
  val logWriterContextStore: LogWriterContextStore
  val memfaultClient: MemfaultClient
  val messageSigner: MessageSigner
  val mobileTestFeatureFlag: MobileTestFeatureFlag
  val mobilePayFiatConfigService: MobilePayFiatConfigService
  val signatureVerifier: SignatureVerifier
  val spake2: Spake2
  val symmetricKeyEncryptor: SymmetricKeyEncryptor
  val symmetricKeyGenerator: SymmetricKeyGenerator
  val networkingDebugService: NetworkingDebugService
  val f8eNetworkReachabilityService: F8eNetworkReachabilityService
  val networkReachabilityProvider: NetworkReachabilityProvider
  val notificationTouchpointDao: NotificationTouchpointDao
  val notificationTouchpointF8eClient: NotificationTouchpointF8eClient
  val notificationTouchpointService: NotificationTouchpointService
  val bitcoinAddressService: BitcoinAddressService
  val haptics: Haptics
  val nfcHaptics: NfcHaptics
  val osVersionInfoProvider: OsVersionInfoProvider
  val analyticsEventPeriodicProcessor: AnalyticsEventPeriodicProcessor
  val periodicFirmwareCoredumpProcessor: FirmwareCoredumpEventPeriodicProcessor
  val periodicFirmwareTelemetryEventProcessor: FirmwareTelemetryEventPeriodicProcessor
  val permissionChecker: PermissionChecker
  val phoneNumberLibBindings: PhoneNumberLibBindings
  val platformContext: PlatformContext
  val platformInfoProvider: PlatformInfoProvider
  val secp256k1KeyGenerator: Secp256k1KeyGenerator
  val pushNotificationPermissionStatusProvider: PushNotificationPermissionStatusProvider
  val recoveryDao: RecoveryDao
  val secureStoreFactory: EncryptedKeyValueStoreFactory
  val appSessionManager: AppSessionManager
  val bitcoinFeeRateEstimator: BitcoinFeeRateEstimator
  val feeBumpAllowShrinkingChecker: FeeBumpAllowShrinkingChecker
  val spendingWalletProvider: SpendingWalletProvider
  val debugOptionsService: DebugOptionsService
  val outgoingTransactionDetailDao: OutgoingTransactionDetailDao
  val uuidGenerator: UuidGenerator
  val onboardingAppKeyKeystore: OnboardingAppKeyKeystore
  val phoneNumberValidator: PhoneNumberValidator
  val recoverySyncFrequency: Duration
  val hardwareAttestation: HardwareAttestation
  val f8eAuthSignatureStatusProvider: F8eAuthSignatureStatusProvider
  val analyticsTrackingPreference: AnalyticsTrackingPreference
  val exchangeRateF8eClient: ExchangeRateF8eClient
  val postSocRecTaskRepository: PostSocRecTaskRepository
  val relationshipsCodeBuilder: RelationshipsCodeBuilder
  val softwareWalletIsEnabledFeatureFlag: SoftwareWalletIsEnabledFeatureFlag
  val socRecChallengeRepository: SocRecChallengeRepository
  val relationshipsCrypto: RelationshipsCrypto
  val relationshipsEnrollmentAuthenticationDao: RelationshipsEnrollmentAuthenticationDao
  val socRecF8eClientProvider: SocRecF8eClientProvider
  val relationshipsF8eClient: RelationshipsF8eClient
  val relationshipsF8eClientProvider: RelationshipsF8eClientProvider
  val relationshipsDao: RelationshipsDao
  val socRecService: SocRecService
  val relationshipsService: RelationshipsService
  val socRecStartedChallengeAuthenticationDao: SocRecStartedChallengeAuthenticationDao
  val socRecStartedChallengeDao: SocRecStartedChallengeDao
  val socialChallengeVerifier: SocialChallengeVerifier
  val firmwareCommsLoggingFeatureFlag: FirmwareCommsLoggingFeatureFlag
  val asyncNfcSigningFeatureFlag: AsyncNfcSigningFeatureFlag
  val progressSpinnerForLongNfcOpsFeatureFlag: ProgressSpinnerForLongNfcOpsFeatureFlag
  val promptSweepFeatureFlag: PromptSweepFeatureFlag
  val coachmarksGlobalFeatureFlag: CoachmarksGlobalFeatureFlag
  val inheritanceFeatureFlag: InheritanceFeatureFlag
  val expectedTransactionsPhase2FeatureFlag: ExpectedTransactionsPhase2FeatureFlag
  val biometricPreference: BiometricPreference
  val bitcoinPriceCardPreference: BitcoinPriceCardPreference
  val bitcoinWalletService: BitcoinWalletService
  val xChaCha20Poly1305: XChaCha20Poly1305
  val xNonceGenerator: XNonceGenerator
  val mobilePayService: MobilePayService
  val inheritanceClaimsDao: InheritanceClaimsDao
  val inheritanceService: InheritanceService
  val utxoConsolidationFeatureFlag: UtxoConsolidationFeatureFlag
  val utxoMaxConsolidationCountFeatureFlag: UtxoMaxConsolidationCountFeatureFlag
  val speedUpAllowShrinkingFeatureFlag: SpeedUpAllowShrinkingFeatureFlag
  val utxoConsolidationService: UtxoConsolidationService
  val mobilePayRevampFeatureFlag: MobilePayRevampFeatureFlag
  val databaseIntegrityChecker: DatabaseIntegrityChecker
  val sellBitcoinFeatureFlag: SellBitcoinFeatureFlag
  val mobilePaySigningF8eClient: MobilePaySigningF8eClient
  val shareGeneratorFactory: ShareGeneratorFactory
  val sellBitcoinQuotesEnabledFeatureFlag: SellBitcoinQuotesEnabledFeatureFlag
  val sellBitcoinMinAmountFeatureFlag: SellBitcoinMinAmountFeatureFlag
  val sellBitcoinMaxAmountFeatureFlag: SellBitcoinMaxAmountFeatureFlag
  val transactionsActivityService: TransactionsActivityService
  val partnershipTransactionsService: PartnershipTransactionsService
  val partnershipPurchaseService: PartnershipPurchaseService
}
