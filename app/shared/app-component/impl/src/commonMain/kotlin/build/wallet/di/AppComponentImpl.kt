package build.wallet.di

import build.wallet.account.AccountDao
import build.wallet.account.AccountDaoImpl
import build.wallet.account.AccountRepository
import build.wallet.account.AccountRepositoryImpl
import build.wallet.account.analytics.AppInstallationDao
import build.wallet.account.analytics.AppInstallationDaoImpl
import build.wallet.analytics.events.AnalyticsTrackingPreferenceImpl
import build.wallet.analytics.events.AppDeviceIdDaoImpl
import build.wallet.analytics.events.AppSessionManagerImpl
import build.wallet.analytics.events.EventQueueImpl
import build.wallet.analytics.events.EventSenderImpl
import build.wallet.analytics.events.EventStoreImpl
import build.wallet.analytics.events.EventTrackerImpl
import build.wallet.analytics.events.HardwareInfoProviderImpl
import build.wallet.analytics.events.PlatformInfoProvider
import build.wallet.analytics.events.PlatformInfoProviderImpl
import build.wallet.auth.AccountAuthenticator
import build.wallet.auth.AccountAuthenticatorImpl
import build.wallet.auth.AppAuthKeyMessageSigner
import build.wallet.auth.AppAuthKeyMessageSignerImpl
import build.wallet.auth.AppAuthPublicKeyProvider
import build.wallet.auth.AppAuthPublicKeyProviderImpl
import build.wallet.auth.AppAuthTokenRefresher
import build.wallet.auth.AppAuthTokenRefresherImpl
import build.wallet.auth.AuthTokenDao
import build.wallet.auth.AuthTokenDaoImpl
import build.wallet.auth.AuthTokensRepository
import build.wallet.auth.AuthTokensRepositoryImpl
import build.wallet.availability.F8eAuthSignatureStatusProvider
import build.wallet.availability.F8eAuthSignatureStatusProviderImpl
import build.wallet.availability.F8eNetworkReachabilityService
import build.wallet.availability.F8eNetworkReachabilityServiceImpl
import build.wallet.availability.InternetNetworkReachabilityService
import build.wallet.availability.InternetNetworkReachabilityServiceImpl
import build.wallet.availability.NetworkReachabilityEventDao
import build.wallet.availability.NetworkReachabilityEventDaoImpl
import build.wallet.availability.NetworkReachabilityProvider
import build.wallet.availability.NetworkReachabilityProviderImpl
import build.wallet.bdk.bindings.BdkAddressBuilder
import build.wallet.bdk.bindings.BdkBlockchainFactory
import build.wallet.bdk.bindings.BdkBumpFeeTxBuilderFactory
import build.wallet.bdk.bindings.BdkDescriptorSecretKeyGenerator
import build.wallet.bdk.bindings.BdkMnemonicGenerator
import build.wallet.bdk.bindings.BdkPartiallySignedTransactionBuilder
import build.wallet.bdk.bindings.BdkTxBuilderFactory
import build.wallet.bdk.bindings.BdkWalletFactory
import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitcoin.AppPrivateKeyDaoImpl
import build.wallet.bitcoin.bdk.BdkBlockchainProviderImpl
import build.wallet.bitcoin.bdk.BdkDatabaseConfigProviderImpl
import build.wallet.bitcoin.bdk.BdkTransactionMapperImpl
import build.wallet.bitcoin.bdk.BdkWalletProviderImpl
import build.wallet.bitcoin.bdk.BdkWalletSyncerImpl
import build.wallet.bitcoin.bdk.ElectrumReachabilityImpl
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilderImpl
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimatorImpl
import build.wallet.bitcoin.fees.MempoolHttpClientImpl
import build.wallet.bitcoin.keys.ExtendedKeyGeneratorImpl
import build.wallet.bitcoin.sync.ElectrumServerConfigRepositoryImpl
import build.wallet.bitcoin.sync.ElectrumServerSettingProviderImpl
import build.wallet.bitcoin.transactions.OutgoingTransactionDetailDaoImpl
import build.wallet.bitcoin.wallet.SpendingWalletProviderImpl
import build.wallet.bitcoin.wallet.WatchingWalletProviderImpl
import build.wallet.bugsnag.BugsnagContextImpl
import build.wallet.coroutines.scopes.CoroutineScopes
import build.wallet.crypto.WsmVerifier
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.datadog.DatadogRumMonitor
import build.wallet.datadog.DatadogTracer
import build.wallet.encrypt.MessageSigner
import build.wallet.encrypt.Secp256k1KeyGenerator
import build.wallet.encrypt.SignatureVerifier
import build.wallet.f8e.analytics.EventTrackerServiceImpl
import build.wallet.f8e.auth.AuthenticationService
import build.wallet.f8e.auth.AuthenticationServiceImpl
import build.wallet.f8e.client.DatadogTracerPluginProvider
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.F8eHttpClientImpl
import build.wallet.f8e.client.F8eHttpClientProvider
import build.wallet.f8e.client.ProofOfPossessionPluginProvider
import build.wallet.f8e.client.UnauthenticatedF8eHttpClient
import build.wallet.f8e.client.UnauthenticatedOnlyF8eHttpClientImpl
import build.wallet.f8e.debug.NetworkingDebugConfigDao
import build.wallet.f8e.debug.NetworkingDebugConfigDaoImpl
import build.wallet.f8e.debug.NetworkingDebugConfigRepository
import build.wallet.f8e.debug.NetworkingDebugConfigRepositoryImpl
import build.wallet.f8e.featureflags.GetFeatureFlagsServiceImpl
import build.wallet.f8e.notifications.RegisterWatchAddressServiceImpl
import build.wallet.f8e.onboarding.AddDeviceTokenServiceImpl
import build.wallet.feature.DoubleMobileTestFeatureFlag
import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDaoImpl
import build.wallet.feature.FeatureFlagInitializerImpl
import build.wallet.feature.FeatureFlagSyncerImpl
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.MobileTestFeatureFlag
import build.wallet.feature.StringFlagMobileTestFeatureFlag
import build.wallet.fingerprints.MultipleFingerprintsIsEnabledFeatureFlag
import build.wallet.firmware.FirmwareCoredumpQueueImpl
import build.wallet.firmware.FirmwareCoredumpSenderImpl
import build.wallet.firmware.FirmwareDeviceInfoDaoImpl
import build.wallet.firmware.FirmwareMetadataDaoImpl
import build.wallet.firmware.FirmwareTelemetryQueueImpl
import build.wallet.firmware.FirmwareTelemetrySenderImpl
import build.wallet.firmware.FirmwareTelemetryUploaderImpl
import build.wallet.firmware.HardwareAttestation
import build.wallet.firmware.Teltra
import build.wallet.fwup.FirmwareDownloaderImpl
import build.wallet.fwup.FwupDataDaoImpl
import build.wallet.fwup.FwupDataFetcherImpl
import build.wallet.fwup.FwupManifestParserImpl
import build.wallet.fwup.FwupProgressCalculatorImpl
import build.wallet.inappsecurity.BiometricPreferenceImpl
import build.wallet.inappsecurity.InAppSecurityFeatureFlag
import build.wallet.keybox.KeyboxDao
import build.wallet.keybox.KeyboxDaoImpl
import build.wallet.keybox.config.TemplateFullAccountConfigDaoImpl
import build.wallet.keybox.keys.AppAuthKeyGeneratorImpl
import build.wallet.keybox.keys.AppKeysGeneratorImpl
import build.wallet.keybox.keys.OnboardingAppKeyKeystoreImpl
import build.wallet.keybox.keys.SpendingKeyGeneratorImpl
import build.wallet.keybox.wallet.AppSpendingWalletProviderImpl
import build.wallet.keybox.wallet.KeysetWalletProviderImpl
import build.wallet.ktor.result.client.KtorLogLevelPolicy
import build.wallet.ktor.result.client.KtorLogLevelPolicyImpl
import build.wallet.logging.LogStoreWriterImpl
import build.wallet.logging.LogWriterContextStore
import build.wallet.logging.LogWriterContextStoreImpl
import build.wallet.logging.dev.LogStore
import build.wallet.logging.initializeLogger
import build.wallet.memfault.MemfaultHttpClientImpl
import build.wallet.memfault.MemfaultServiceImpl
import build.wallet.money.currency.FiatCurrencyDaoImpl
import build.wallet.money.display.BitcoinDisplayPreferenceDaoImpl
import build.wallet.money.display.BitcoinDisplayPreferenceRepositoryImpl
import build.wallet.money.display.FiatCurrencyPreferenceDaoImpl
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryImpl
import build.wallet.money.exchange.F8eExchangeRateService
import build.wallet.money.exchange.F8eExchangeRateServiceImpl
import build.wallet.nfc.haptics.NfcHapticsImpl
import build.wallet.nfc.haptics.NfcHapticsOnConnectedIsEnabledFeatureFlag
import build.wallet.notifications.DeviceTokenManagerImpl
import build.wallet.notifications.RegisterWatchAddressQueueImpl
import build.wallet.notifications.RegisterWatchAddressSenderImpl
import build.wallet.platform.PlatformContext
import build.wallet.platform.config.AppId
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.DeviceOs
import build.wallet.platform.config.DeviceTokenConfigProvider
import build.wallet.platform.data.FileDirectoryProvider
import build.wallet.platform.data.FileManager
import build.wallet.platform.device.DeviceInfoProviderImpl
import build.wallet.platform.haptics.HapticsImpl
import build.wallet.platform.haptics.HapticsPolicyImpl
import build.wallet.platform.hardware.SerialNumberParserImpl
import build.wallet.platform.permissions.PermissionCheckerImpl
import build.wallet.platform.permissions.PushNotificationPermissionStatusProviderImpl
import build.wallet.platform.random.UuidGenerator
import build.wallet.platform.random.UuidGeneratorImpl
import build.wallet.platform.settings.CountryCodeGuesser
import build.wallet.platform.settings.CountryCodeGuesserImpl
import build.wallet.platform.settings.LocaleCountryCodeProvider
import build.wallet.platform.settings.LocaleCountryCodeProviderImpl
import build.wallet.platform.settings.LocaleCurrencyCodeProviderImpl
import build.wallet.platform.settings.LocaleLanguageCodeProviderImpl
import build.wallet.platform.settings.TelephonyCountryCodeProvider
import build.wallet.platform.settings.TelephonyCountryCodeProviderImpl
import build.wallet.platform.versions.OsVersionInfoProvider
import build.wallet.platform.versions.OsVersionInfoProviderImpl
import build.wallet.queueprocessor.BatcherProcessorImpl
import build.wallet.queueprocessor.ProcessorImpl
import build.wallet.recovery.RecoveryAppAuthPublicKeyProvider
import build.wallet.recovery.RecoveryAppAuthPublicKeyProviderImpl
import build.wallet.recovery.RecoveryDao
import build.wallet.recovery.RecoveryDaoImpl
import build.wallet.sqldelight.SqlDriverFactory
import build.wallet.sqldelight.SqlDriverFactoryImpl
import build.wallet.statemachine.send.FeeBumpIsAvailableFeatureFlag
import build.wallet.statemachine.settings.full.device.ResetDeviceIsEnabledFeatureFlag
import build.wallet.store.EncryptedKeyValueStoreFactory
import build.wallet.store.EncryptedKeyValueStoreFactoryImpl
import build.wallet.store.KeyValueStoreFactoryImpl
import build.wallet.time.Delayer
import build.wallet.worker.AppWorkerExecutorImpl
import build.wallet.worker.AppWorkerProviderImpl
import co.touchlab.kermit.LogWriter
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class AppComponentImpl(
  override val appId: AppId,
  override val appVariant: AppVariant,
  override val delayer: Delayer,
  override val deviceOs: DeviceOs,
  override val appVersion: String,
  override val bdkAddressBuilder: BdkAddressBuilder,
  bdkBlockchainFactory: BdkBlockchainFactory,
  bdkBumpFeeTxBuilderFactory: BdkBumpFeeTxBuilderFactory,
  override val bdkDescriptorSecretKeyGenerator: BdkDescriptorSecretKeyGenerator,
  override val bdkMnemonicGenerator: BdkMnemonicGenerator,
  override val bdkPartiallySignedTransactionBuilder: BdkPartiallySignedTransactionBuilder,
  bdkTxBuilderFactory: BdkTxBuilderFactory,
  bdkWalletFactory: BdkWalletFactory,
  override val datadogRumMonitor: DatadogRumMonitor,
  override val datadogTracer: DatadogTracer,
  deviceTokenConfigProvider: DeviceTokenConfigProvider,
  override val fileDirectoryProvider: FileDirectoryProvider,
  override val fileManager: FileManager,
  logWritersProvider: (LogWriterContextStore) -> List<LogWriter>,
  override val logStore: LogStore,
  override val messageSigner: MessageSigner,
  override val signatureVerifier: SignatureVerifier,
  override val platformContext: PlatformContext,
  override val secp256k1KeyGenerator: Secp256k1KeyGenerator,
  teltra: Teltra,
  override val hardwareAttestation: HardwareAttestation,
  wsmVerifier: WsmVerifier,
  override val recoverySyncFrequency: Duration = 1.minutes,
  override val f8eAuthSignatureStatusProvider: F8eAuthSignatureStatusProvider =
    F8eAuthSignatureStatusProviderImpl(),
  override val secureStoreFactory: EncryptedKeyValueStoreFactory =
    EncryptedKeyValueStoreFactoryImpl(platformContext, fileManager),
  override val authTokenDao: AuthTokenDao =
    AuthTokenDaoImpl(secureStoreFactory),
  override val appPrivateKeyDao: AppPrivateKeyDao = AppPrivateKeyDaoImpl(secureStoreFactory),
  override val appAuthKeyMessageSigner: AppAuthKeyMessageSigner =
    AppAuthKeyMessageSignerImpl(
      appPrivateKeyDao,
      messageSigner
    ),
  override val osVersionInfoProvider: OsVersionInfoProvider = OsVersionInfoProviderImpl(),
  override val platformInfoProvider: PlatformInfoProvider =
    PlatformInfoProviderImpl(
      platformContext,
      appId,
      appVersion,
      osVersionInfoProvider
    ),
  override val ktorLogLevelPolicy: KtorLogLevelPolicy = KtorLogLevelPolicyImpl(appVariant),
  override val uuidGenerator: UuidGenerator = UuidGeneratorImpl(),
  private val databaseDriverFactory: SqlDriverFactory =
    SqlDriverFactoryImpl(
      platformContext,
      fileDirectoryProvider,
      secureStoreFactory,
      uuidGenerator,
      appVariant
    ),
  override val bitkeyDatabaseProvider: BitkeyDatabaseProvider =
    BitkeyDatabaseProviderImpl(databaseDriverFactory),
  private val networkingDebugConfigDao: NetworkingDebugConfigDao =
    NetworkingDebugConfigDaoImpl(bitkeyDatabaseProvider),
  override val networkingDebugConfigRepository: NetworkingDebugConfigRepository =
    NetworkingDebugConfigRepositoryImpl(networkingDebugConfigDao),
  override val appInstallationDao: AppInstallationDao =
    AppInstallationDaoImpl(bitkeyDatabaseProvider, uuidGenerator),
  override val localeCountryCodeProvider: LocaleCountryCodeProvider =
    LocaleCountryCodeProviderImpl(platformContext),
  private val telephonyCountryCodeProvider: TelephonyCountryCodeProvider =
    TelephonyCountryCodeProviderImpl(platformContext),
  private val countryCodeGuesser: CountryCodeGuesser = CountryCodeGuesserImpl(
    localeCountryCodeProvider,
    telephonyCountryCodeProvider
  ),
  private val f8eHttpClientProvider: F8eHttpClientProvider =
    F8eHttpClientProvider(
      appId = appId,
      appVersion = appVersion,
      appVariant = appVariant,
      platformInfoProvider = platformInfoProvider,
      datadogTracerPluginProvider = DatadogTracerPluginProvider(datadogTracer = datadogTracer),
      networkingDebugConfigRepository = networkingDebugConfigRepository,
      appInstallationDao = appInstallationDao,
      countryCodeGuesser = countryCodeGuesser
    ),
  private val f8eNetworkReachabilityService: F8eNetworkReachabilityService =
    F8eNetworkReachabilityServiceImpl(
      unauthenticatedF8eHttpClient =
        UnauthenticatedOnlyF8eHttpClientImpl(
          f8eHttpClientProvider = f8eHttpClientProvider,
          networkReachabilityProvider = null
        )
    ),
  private val internetNetworkReachabilityService: InternetNetworkReachabilityService =
    InternetNetworkReachabilityServiceImpl(),
  override val clock: Clock = Clock.System,
  override val networkReachabilityEventDao: NetworkReachabilityEventDao =
    NetworkReachabilityEventDaoImpl(
      clock = clock,
      databaseProvider = bitkeyDatabaseProvider
    ),
  override val networkReachabilityProvider: NetworkReachabilityProvider =
    NetworkReachabilityProviderImpl(
      f8eNetworkReachabilityService = f8eNetworkReachabilityService,
      internetNetworkReachabilityService = internetNetworkReachabilityService,
      networkReachabilityEventDao = networkReachabilityEventDao
    ),
  private val unauthenticatedOnlyF8eHttpClient: UnauthenticatedF8eHttpClient =
    UnauthenticatedOnlyF8eHttpClientImpl(
      f8eHttpClientProvider = f8eHttpClientProvider,
      networkReachabilityProvider = networkReachabilityProvider
    ),
  override val authenticationService: AuthenticationService =
    AuthenticationServiceImpl(unauthenticatedOnlyF8eHttpClient),
  override val accountAuthenticator: AccountAuthenticator =
    AccountAuthenticatorImpl(
      appAuthKeyMessageSigner = appAuthKeyMessageSigner,
      authenticationService = authenticationService
    ),
  override val recoveryDao: RecoveryDao =
    RecoveryDaoImpl(
      databaseProvider = bitkeyDatabaseProvider
    ),
  private val recoveryAppAuthPublicKeyProvider: RecoveryAppAuthPublicKeyProvider =
    RecoveryAppAuthPublicKeyProviderImpl(
      recoveryDao = recoveryDao
    ),
  private val accountDao: AccountDao = AccountDaoImpl(bitkeyDatabaseProvider),
  override val accountRepository: AccountRepository = AccountRepositoryImpl(accountDao),
  private val appAuthPublicKeyProvider: AppAuthPublicKeyProvider =
    AppAuthPublicKeyProviderImpl(
      accountRepository = accountRepository,
      recoveryAppAuthPublicKeyProvider = recoveryAppAuthPublicKeyProvider
    ),
  private val authTokenRefresher: AppAuthTokenRefresher = AppAuthTokenRefresherImpl(
    authTokenDao,
    accountAuthenticator,
    authenticationService,
    appAuthPublicKeyProvider,
    f8eAuthSignatureStatusProvider
  ),
  override val authTokensRepository: AuthTokensRepository =
    AuthTokensRepositoryImpl(authTokenDao, authTokenRefresher),
  override val keyboxDao: KeyboxDao = KeyboxDaoImpl(bitkeyDatabaseProvider),
  private val proofOfPossessionPluginProvider: ProofOfPossessionPluginProvider =
    ProofOfPossessionPluginProvider(authTokensRepository, appAuthKeyMessageSigner, keyboxDao),
  override val f8eHttpClient: F8eHttpClient =
    F8eHttpClientImpl(
      authTokensRepository = authTokensRepository,
      proofOfPossessionPluginProvider = proofOfPossessionPluginProvider,
      unauthenticatedF8eHttpClient = unauthenticatedOnlyF8eHttpClient,
      f8eHttpClientProvider = f8eHttpClientProvider,
      networkReachabilityProvider = networkReachabilityProvider,
      wsmVerifier = wsmVerifier
    ),
  override val f8eExchangeRateService: F8eExchangeRateService =
    F8eExchangeRateServiceImpl(f8eHttpClient = f8eHttpClient),
) : AppComponent {
  override val appCoroutineScope = CoroutineScopes.AppScope
  override val bugsnagContext = BugsnagContextImpl(appCoroutineScope, appInstallationDao)
  override val logWriterContextStore = LogWriterContextStoreImpl(appInstallationDao)
  private val logStoreWriter = LogStoreWriterImpl(logStore, clock)

  init {
    initializeLogger(
      appVariant,
      appId,
      appCoroutineScope,
      logWriterContextStore,
      logStoreWriter,
      logWritersProvider
    )
  }

  override val keyValueStoreFactory = KeyValueStoreFactoryImpl(platformContext, fileManager)

  private val bitcoinDisplayPreferenceDao = BitcoinDisplayPreferenceDaoImpl(bitkeyDatabaseProvider)
  override val bitcoinDisplayPreferenceRepository =
    BitcoinDisplayPreferenceRepositoryImpl(
      appScope = appCoroutineScope,
      bitcoinDisplayPreferenceDao = bitcoinDisplayPreferenceDao
    )

  override val fiatCurrencyDao = FiatCurrencyDaoImpl(bitkeyDatabaseProvider)
  private val fiatCurrencyPreferenceDao =
    FiatCurrencyPreferenceDaoImpl(
      databaseProvider = bitkeyDatabaseProvider
    )
  override val localeCurrencyCodeProvider = LocaleCurrencyCodeProviderImpl(platformContext)
  override val fiatCurrencyPreferenceRepository =
    FiatCurrencyPreferenceRepositoryImpl(
      appScope = appCoroutineScope,
      fiatCurrencyPreferenceDao = fiatCurrencyPreferenceDao,
      localeCurrencyCodeProvider = localeCurrencyCodeProvider,
      fiatCurrencyDao = fiatCurrencyDao
    )

  private val deviceTokenService = AddDeviceTokenServiceImpl(f8eHttpClient)
  override val deviceTokenManager =
    DeviceTokenManagerImpl(
      deviceTokenService,
      deviceTokenConfigProvider,
      keyboxDao
    )

  private val featureFlagDao = FeatureFlagDaoImpl(bitkeyDatabaseProvider)
  private val nfcHapticsOnConnectedIsEnabledFeatureFlag =
    NfcHapticsOnConnectedIsEnabledFeatureFlag(featureFlagDao)

  override val feeBumpIsAvailableFeatureFlag = FeeBumpIsAvailableFeatureFlag(featureFlagDao)

  override val multipleFingerprintsIsEnabledFeatureFlag =
    MultipleFingerprintsIsEnabledFeatureFlag(featureFlagDao)

  override val resetDeviceIsEnabledFeatureFlag =
    ResetDeviceIsEnabledFeatureFlag(featureFlagDao)

  override val inAppSecurityFeatureFlag = InAppSecurityFeatureFlag(
    featureFlagDao = featureFlagDao
  )

  override val mobileTestFeatureFlag =
    MobileTestFeatureFlag(featureFlagDao)

  override val allRemoteFeatureFlags: List<FeatureFlag<out FeatureFlagValue>> =
    setOf(
      multipleFingerprintsIsEnabledFeatureFlag,
      resetDeviceIsEnabledFeatureFlag,
      inAppSecurityFeatureFlag,
      mobileTestFeatureFlag,
      DoubleMobileTestFeatureFlag(featureFlagDao),
      StringFlagMobileTestFeatureFlag(featureFlagDao)
    ).toList()

  override val allFeatureFlags: List<FeatureFlag<*>> =
    setOf(
      nfcHapticsOnConnectedIsEnabledFeatureFlag,
      feeBumpIsAvailableFeatureFlag
    )
      .union(allRemoteFeatureFlags)
      .toList()
  override val featureFlagInitializer = FeatureFlagInitializerImpl(allFeatureFlags)
  private val appDeviceIdDao = AppDeviceIdDaoImpl(secureStoreFactory, uuidGenerator)
  override val deviceInfoProvider = DeviceInfoProviderImpl()
  override val localeLanguageCodeProvider = LocaleLanguageCodeProviderImpl(platformContext)
  override val getFeatureFlagsService = GetFeatureFlagsServiceImpl(
    f8eHttpClient = f8eHttpClient,
    appInstallationDao = appInstallationDao,
    platformInfoProvider = platformInfoProvider,
    localeCountryCodeProvider = localeCountryCodeProvider,
    localeLanguageCodeProvider = localeLanguageCodeProvider
  )

  override val firmwareDeviceInfoDao =
    FirmwareDeviceInfoDaoImpl(bitkeyDatabaseProvider)
  private val hardwareInfoProvider =
    HardwareInfoProviderImpl(
      appInstallationDao,
      SerialNumberParserImpl(),
      firmwareDeviceInfoDao
    )
  override val periodicEventProcessor =
    BatcherProcessorImpl(
      queue = EventQueueImpl(bitkeyDatabaseProvider),
      processor = EventSenderImpl(EventTrackerServiceImpl(f8eHttpClient)),
      frequency = 1.minutes,
      batchSize = 50
    )

  override val appSessionManager = AppSessionManagerImpl(clock, uuidGenerator)
  override val eventStore = EventStoreImpl()
  override val templateFullAccountConfigDao =
    TemplateFullAccountConfigDaoImpl(
      appVariant,
      bitkeyDatabaseProvider
    )

  override val featureFlagSyncer = FeatureFlagSyncerImpl(
    accountRepository = accountRepository,
    templateFullAccountConfigDao = templateFullAccountConfigDao,
    getFeatureFlagsService = getFeatureFlagsService,
    clock = clock,
    remoteFlags = allRemoteFeatureFlags,
    appSessionManager = appSessionManager
  )

  override val analyticsTrackingPreference = AnalyticsTrackingPreferenceImpl(
    appVariant = appVariant,
    databaseProvider = bitkeyDatabaseProvider
  )

  override val eventTracker =
    EventTrackerImpl(
      appCoroutineScope = appCoroutineScope,
      appDeviceIdDao = appDeviceIdDao,
      deviceInfoProvider = deviceInfoProvider,
      accountRepository = accountRepository,
      templateFullAccountConfigDao = templateFullAccountConfigDao,
      clock = clock,
      countryCodeProvider = localeCountryCodeProvider,
      hardwareInfoProvider = hardwareInfoProvider,
      eventProcessor = periodicEventProcessor,
      appInstallationDao = appInstallationDao,
      platformInfoProvider = platformInfoProvider,
      appSessionManager = appSessionManager,
      eventStore = eventStore,
      bitcoinDisplayPreferenceRepository = bitcoinDisplayPreferenceRepository,
      localeCurrencyCodeProvider = localeCurrencyCodeProvider,
      fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
      analyticsTrackingPreference = analyticsTrackingPreference
    )
  override val biometricPreference = BiometricPreferenceImpl(
    databaseProvider = bitkeyDatabaseProvider,
    eventTracker = eventTracker
  )
  override val memfaultService =
    MemfaultServiceImpl(
      MemfaultHttpClientImpl(
        logLevelPolicy = ktorLogLevelPolicy,
        networkReachabilityProvider = networkReachabilityProvider
      )
    )
  override val fwupDataDao = FwupDataDaoImpl(bitkeyDatabaseProvider)
  private val fwupManifestParser = FwupManifestParserImpl()
  private val firmwareDownloader = FirmwareDownloaderImpl(memfaultService, fileManager)
  override val firmwareMetadataDao = FirmwareMetadataDaoImpl(bitkeyDatabaseProvider)
  override val fwupDataFetcher =
    FwupDataFetcherImpl(fileManager, fwupManifestParser, firmwareDownloader)
  override val fwupProgressCalculator = FwupProgressCalculatorImpl()
  override val pushNotificationPermissionStatusProvider =
    PushNotificationPermissionStatusProviderImpl(platformContext)
  override val permissionChecker =
    PermissionCheckerImpl(platformContext, pushNotificationPermissionStatusProvider)
  private val hapticsPolicy = HapticsPolicyImpl(permissionChecker)
  private val haptics = HapticsImpl(platformContext, hapticsPolicy)
  override val nfcHaptics =
    NfcHapticsImpl(
      haptics,
      nfcHapticsOnConnectedIsEnabledFeatureFlag,
      appCoroutineScope
    )
  override val periodicFirmwareTelemetryEventProcessor =
    BatcherProcessorImpl(
      queue = FirmwareTelemetryQueueImpl(bitkeyDatabaseProvider),
      processor = FirmwareTelemetrySenderImpl(memfaultService),
      frequency = 1.minutes,
      batchSize = 10
    )
  override val periodicFirmwareCoredumpProcessor =
    BatcherProcessorImpl(
      queue = FirmwareCoredumpQueueImpl(bitkeyDatabaseProvider),
      processor = FirmwareCoredumpSenderImpl(memfaultService),
      frequency = 1.minutes,
      batchSize = 10
    )
  override val firmwareTelemetryUploader =
    FirmwareTelemetryUploaderImpl(
      firmwareCoredumpProcessor = periodicFirmwareCoredumpProcessor,
      firmwareTelemetryProcessor = periodicFirmwareTelemetryEventProcessor,
      teltra = teltra
    )

  private val registerWatchAddressQueue =
    RegisterWatchAddressQueueImpl(
      databaseProvider = bitkeyDatabaseProvider
    )

  private val registerWatchAddressService =
    RegisterWatchAddressServiceImpl(
      f8eHttpClient = f8eHttpClient
    )

  private val registerWatchAddressSender =
    RegisterWatchAddressSenderImpl(registerWatchAddressService)

  override val registerWatchAddressProcessor =
    ProcessorImpl(
      queue = registerWatchAddressQueue,
      processor = registerWatchAddressSender,
      retryFrequency = 1.minutes,
      retryBatchSize = 1
    )

  private val appWorkerProvider = AppWorkerProviderImpl(
    eventTracker = eventTracker,
    networkingDebugConfigRepository = networkingDebugConfigRepository,
    periodicEventProcessor = periodicEventProcessor,
    periodicFirmwareCoredumpProcessor = periodicFirmwareCoredumpProcessor,
    periodicFirmwareTelemetryProcessor = periodicFirmwareTelemetryEventProcessor,
    periodicRegisterWatchAddressProcessor = registerWatchAddressProcessor
  )
  override val appWorkerExecutor = AppWorkerExecutorImpl(
    appScope = appCoroutineScope,
    workerProvider = appWorkerProvider
  )
  override val outgoingTransactionDetailDao =
    OutgoingTransactionDetailDaoImpl(bitkeyDatabaseProvider)
  private val bdkTransactionMapper =
    BdkTransactionMapperImpl(bdkAddressBuilder, outgoingTransactionDetailDao)
  private val bdkDatabaseConfigProvider = BdkDatabaseConfigProviderImpl(fileDirectoryProvider)
  private val bdkWalletProvider = BdkWalletProviderImpl(bdkWalletFactory, bdkDatabaseConfigProvider)
  override val electrumServerDao = ElectrumServerConfigRepositoryImpl(bitkeyDatabaseProvider)
  override val electrumServerSettingProvider =
    ElectrumServerSettingProviderImpl(
      keyboxDao,
      templateFullAccountConfigDao,
      electrumServerDao
    )
  override val bdkBlockchainProvider =
    BdkBlockchainProviderImpl(
      bdkBlockchainFactory,
      electrumServerSettingProvider
    )

  override val electrumReachability =
    ElectrumReachabilityImpl(
      bdkBlockchainProvider = bdkBlockchainProvider
    )
  private val bdkWalletSyncer =
    BdkWalletSyncerImpl(
      bdkBlockchainProvider = bdkBlockchainProvider,
      clock = clock,
      datadogRumMonitor = datadogRumMonitor,
      electrumServerSettingProvider = electrumServerSettingProvider,
      electrumReachability = electrumReachability,
      networkReachabilityProvider = networkReachabilityProvider
    )

  override val bitcoinFeeRateEstimator =
    BitcoinFeeRateEstimatorImpl(
      mempoolHttpClient =
        MempoolHttpClientImpl(
          logLevelPolicy = ktorLogLevelPolicy,
          networkReachabilityProvider = networkReachabilityProvider
        ),
      bdkBlockchainProvider = bdkBlockchainProvider
    )

  override val spendingWalletProvider =
    SpendingWalletProviderImpl(
      bdkWalletProvider,
      bdkTransactionMapper,
      bdkWalletSyncer,
      bdkPartiallySignedTransactionBuilder,
      bdkTxBuilderFactory,
      bdkAddressBuilder,
      bdkBumpFeeTxBuilderFactory,
      appSessionManager,
      bitcoinFeeRateEstimator = bitcoinFeeRateEstimator
    )
  override val bitcoinMultiSigDescriptorBuilder = BitcoinMultiSigDescriptorBuilderImpl()
  override val appSpendingWalletProvider =
    AppSpendingWalletProviderImpl(
      spendingWalletProvider,
      appPrivateKeyDao,
      bitcoinMultiSigDescriptorBuilder
    )
  private val watchingWalletProvider =
    WatchingWalletProviderImpl(
      bdkWalletProvider,
      bdkTransactionMapper,
      bdkWalletSyncer,
      bdkPartiallySignedTransactionBuilder,
      bdkTxBuilderFactory,
      bdkAddressBuilder,
      bdkBumpFeeTxBuilderFactory,
      appSessionManager,
      bitcoinFeeRateEstimator
    )
  override val keysetWalletProvider =
    KeysetWalletProviderImpl(
      watchingWalletProvider = watchingWalletProvider,
      descriptorBuilder = bitcoinMultiSigDescriptorBuilder
    )
  override val extendedKeyGenerator =
    ExtendedKeyGeneratorImpl(bdkMnemonicGenerator, bdkDescriptorSecretKeyGenerator)
  private val spendingKeyGenerator = SpendingKeyGeneratorImpl(extendedKeyGenerator)
  private val authKeyGenerator = AppAuthKeyGeneratorImpl(secp256k1KeyGenerator)

  override val onboardingAppKeyKeystore =
    OnboardingAppKeyKeystoreImpl(
      encryptedKeyValueStoreFactory = secureStoreFactory
    )

  override val appKeysGenerator =
    AppKeysGeneratorImpl(
      uuidGenerator,
      spendingKeyGenerator,
      authKeyGenerator,
      appPrivateKeyDao
    )
}
