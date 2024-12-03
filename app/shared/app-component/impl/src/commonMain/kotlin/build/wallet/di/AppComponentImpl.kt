package build.wallet.di

import build.wallet.account.AccountDao
import build.wallet.account.AccountDaoImpl
import build.wallet.account.AccountService
import build.wallet.account.AccountServiceImpl
import build.wallet.account.analytics.AppInstallationDao
import build.wallet.account.analytics.AppInstallationDaoImpl
import build.wallet.activity.TransactionsActivityServiceImpl
import build.wallet.analytics.events.*
import build.wallet.auth.*
import build.wallet.availability.*
import build.wallet.bdk.bindings.*
import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitcoin.AppPrivateKeyDaoImpl
import build.wallet.bitcoin.address.BitcoinAddressServiceImpl
import build.wallet.bitcoin.bdk.*
import build.wallet.bitcoin.blockchain.BitcoinBlockchainImpl
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilderImpl
import build.wallet.bitcoin.descriptor.FrostWalletDescriptorFactory
import build.wallet.bitcoin.export.ExportTransactionsAsCsvSerializerImpl
import build.wallet.bitcoin.export.ExportTransactionsServiceImpl
import build.wallet.bitcoin.export.ExportWatchingDescriptorServiceImpl
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimatorImpl
import build.wallet.bitcoin.fees.MempoolHttpClientImpl
import build.wallet.bitcoin.keys.ExtendedKeyGeneratorImpl
import build.wallet.bitcoin.sync.ElectrumConfigServiceImpl
import build.wallet.bitcoin.sync.ElectrumServerConfigRepositoryImpl
import build.wallet.bitcoin.sync.ElectrumServerSettingProviderImpl
import build.wallet.bitcoin.transactions.BitcoinWalletServiceImpl
import build.wallet.bitcoin.transactions.FeeBumpAllowShrinkingCheckerImpl
import build.wallet.bitcoin.transactions.OutgoingTransactionDetailDaoImpl
import build.wallet.bitcoin.utxo.UtxoConsolidationServiceImpl
import build.wallet.bitcoin.wallet.SpendingWalletProviderImpl
import build.wallet.bitcoin.wallet.WatchingWalletProviderImpl
import build.wallet.bugsnag.BugsnagContextImpl
import build.wallet.configuration.MobilePayFiatConfigDaoImpl
import build.wallet.configuration.MobilePayFiatConfigRepositoryImpl
import build.wallet.configuration.MobilePayFiatConfigServiceImpl
import build.wallet.coroutines.scopes.CoroutineScopes
import build.wallet.crypto.Spake2
import build.wallet.crypto.WsmVerifier
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.datadog.DatadogRumMonitor
import build.wallet.datadog.DatadogTracer
import build.wallet.debug.DebugOptionsServiceImpl
import build.wallet.debug.DefaultDebugOptionsDeciderImpl
import build.wallet.encrypt.*
import build.wallet.f8e.analytics.EventTrackerF8eClientImpl
import build.wallet.f8e.auth.AuthF8eClient
import build.wallet.f8e.auth.AuthF8eClientImpl
import build.wallet.f8e.client.*
import build.wallet.f8e.configuration.GetBdkConfigurationF8eClientImpl
import build.wallet.f8e.debug.NetworkingDebugConfigDao
import build.wallet.f8e.debug.NetworkingDebugConfigDaoImpl
import build.wallet.f8e.debug.NetworkingDebugService
import build.wallet.f8e.debug.NetworkingDebugServiceImpl
import build.wallet.f8e.featureflags.FeatureFlagsF8eClient
import build.wallet.f8e.featureflags.FeatureFlagsF8eClientImpl
import build.wallet.f8e.inheritance.RetrieveInheritanceClaimsF8EClientImpl
import build.wallet.f8e.inheritance.StartInheritanceClaimF8eClientImpl
import build.wallet.f8e.inheritance.UploadInheritanceMaterialF8eClientImpl
import build.wallet.f8e.mobilepay.MobilePayBalanceF8eClientImpl
import build.wallet.f8e.mobilepay.MobilePayFiatConfigF8eClientImpl
import build.wallet.f8e.mobilepay.MobilePaySigningF8eClientImpl
import build.wallet.f8e.mobilepay.MobilePaySpendingLimitF8eClientImpl
import build.wallet.f8e.money.FiatCurrencyDefinitionF8eClientImpl
import build.wallet.f8e.notifications.NotificationTouchpointF8eClientImpl
import build.wallet.f8e.notifications.RegisterWatchAddressF8eClientImpl
import build.wallet.f8e.onboarding.AddDeviceTokenF8eClientImpl
import build.wallet.f8e.partnerships.GetPartnershipTransactionF8eClientImpl
import build.wallet.f8e.partnerships.GetPurchaseOptionsF8eClientImpl
import build.wallet.f8e.partnerships.GetPurchaseQuoteListF8eClientImpl
import build.wallet.f8e.partnerships.GetPurchaseRedirectF8eClientImpl
import build.wallet.f8e.recovery.ListKeysetsF8eClientImpl
import build.wallet.f8e.recovery.RecoveryNotificationVerificationF8eClientImpl
import build.wallet.f8e.relationships.RelationshipsF8eClientFake
import build.wallet.f8e.relationships.RelationshipsF8eClientImpl
import build.wallet.f8e.socrec.SocRecF8eClientFake
import build.wallet.f8e.socrec.SocRecF8eClientImpl
import build.wallet.feature.*
import build.wallet.feature.flags.*
import build.wallet.firmware.*
import build.wallet.frost.ShareGeneratorFactory
import build.wallet.fwup.*
import build.wallet.inappsecurity.BiometricPreferenceImpl
import build.wallet.inheritance.*
import build.wallet.keybox.KeyboxDao
import build.wallet.keybox.KeyboxDaoImpl
import build.wallet.keybox.keys.AppAuthKeyGeneratorImpl
import build.wallet.keybox.keys.AppKeysGeneratorImpl
import build.wallet.keybox.keys.OnboardingAppKeyKeystoreImpl
import build.wallet.keybox.keys.SpendingKeyGeneratorImpl
import build.wallet.keybox.wallet.AppSpendingWalletProviderImpl
import build.wallet.keybox.wallet.KeysetWalletProviderImpl
import build.wallet.keybox.wallet.WatchingWalletDescriptorProviderImpl
import build.wallet.limit.MobilePayServiceImpl
import build.wallet.limit.MobilePayStatusRepositoryImpl
import build.wallet.limit.SpendingLimitDaoImpl
import build.wallet.logging.LogStoreWriterImpl
import build.wallet.logging.LogWriterContextStore
import build.wallet.logging.LogWriterContextStoreImpl
import build.wallet.logging.LoggerInitializer
import build.wallet.logging.dev.LogStore
import build.wallet.memfault.MemfaultClientImpl
import build.wallet.memfault.MemfaultHttpClientImpl
import build.wallet.money.currency.FiatCurrenciesServiceImpl
import build.wallet.money.currency.FiatCurrencyDaoImpl
import build.wallet.money.display.BitcoinDisplayPreferenceDaoImpl
import build.wallet.money.display.BitcoinDisplayPreferenceRepositoryImpl
import build.wallet.money.display.FiatCurrencyPreferenceDaoImpl
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryImpl
import build.wallet.money.exchange.*
import build.wallet.nfc.haptics.NfcHapticsImpl
import build.wallet.notifications.*
import build.wallet.partnerships.PartnershipPurchaseServiceImpl
import build.wallet.partnerships.PartnershipTransactionsDaoImpl
import build.wallet.partnerships.PartnershipTransactionsServiceImpl
import build.wallet.phonenumber.PhoneNumberValidatorImpl
import build.wallet.phonenumber.lib.PhoneNumberLibBindings
import build.wallet.platform.PlatformContext
import build.wallet.platform.app.AppSessionManagerImpl
import build.wallet.platform.config.*
import build.wallet.platform.data.FileDirectoryProvider
import build.wallet.platform.data.FileManager
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.device.DeviceInfoProviderImpl
import build.wallet.platform.haptics.HapticsImpl
import build.wallet.platform.haptics.HapticsPolicyImpl
import build.wallet.platform.hardware.SerialNumberParserImpl
import build.wallet.platform.permissions.PermissionCheckerImpl
import build.wallet.platform.permissions.PushNotificationPermissionStatusProviderImpl
import build.wallet.platform.random.UuidGenerator
import build.wallet.platform.random.UuidGeneratorImpl
import build.wallet.platform.settings.*
import build.wallet.platform.versions.OsVersionInfoProvider
import build.wallet.platform.versions.OsVersionInfoProviderImpl
import build.wallet.pricechart.BitcoinPriceCardPreferenceImpl
import build.wallet.recovery.RecoveryAppAuthPublicKeyProvider
import build.wallet.recovery.RecoveryAppAuthPublicKeyProviderImpl
import build.wallet.recovery.RecoveryDao
import build.wallet.recovery.RecoveryDaoImpl
import build.wallet.recovery.socrec.*
import build.wallet.relationships.*
import build.wallet.serialization.Base32Encoding
import build.wallet.sqldelight.DatabaseIntegrityChecker
import build.wallet.sqldelight.SqlDriverFactory
import build.wallet.sqldelight.SqlDriverFactoryImpl
import build.wallet.store.EncryptedKeyValueStoreFactory
import build.wallet.store.EncryptedKeyValueStoreFactoryImpl
import build.wallet.store.KeyValueStoreFactoryImpl
import build.wallet.time.Delayer
import build.wallet.worker.AppWorkerExecutorImpl
import build.wallet.worker.AppWorkerProviderImpl
import co.touchlab.kermit.LogWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

// TODO(W-583): consider using a DI framework.
@Suppress("LargeClass")
class AppComponentImpl(
  override val appId: AppId,
  override val appVariant: AppVariant,
  override val delayer: Delayer,
  override val deviceOs: DeviceOs,
  override val appVersion: AppVersion,
  override val bdkAddressBuilder: BdkAddressBuilder,
  bdkBlockchainFactory: BdkBlockchainFactory,
  bdkBumpFeeTxBuilderFactory: BdkBumpFeeTxBuilderFactory,
  override val bdkDescriptorSecretKeyGenerator: BdkDescriptorSecretKeyGenerator,
  override val bdkMnemonicGenerator: BdkMnemonicGenerator,
  override val bdkPartiallySignedTransactionBuilder: BdkPartiallySignedTransactionBuilder,
  bdkTxBuilderFactory: BdkTxBuilderFactory,
  bdkWalletFactory: BdkWalletFactory,
  override val shareGeneratorFactory: ShareGeneratorFactory,
  override val datadogRumMonitor: DatadogRumMonitor,
  override val datadogTracer: DatadogTracer,
  deviceTokenConfigProvider: DeviceTokenConfigProvider,
  /** Optional override that can be used to replace implementation with a test/fake one in tests. */
  featureFlagsF8eClientOverride: FeatureFlagsF8eClient? = null,
  override val fileDirectoryProvider: FileDirectoryProvider,
  override val fileManager: FileManager,
  logWritersProvider: (LogWriterContextStore) -> List<LogWriter>,
  override val logStore: LogStore,
  override val messageSigner: MessageSigner,
  override val signatureVerifier: SignatureVerifier,
  override val platformContext: PlatformContext,
  override val secp256k1KeyGenerator: Secp256k1KeyGenerator,
  teltra: Teltra,
  override val firmwareCommsLogBuffer: FirmwareCommsLogBuffer,
  override val hardwareAttestation: HardwareAttestation,
  wsmVerifier: WsmVerifier,
  override val phoneNumberLibBindings: PhoneNumberLibBindings,
  override val recoverySyncFrequency: Duration = 1.minutes,
  override val cryptoBox: CryptoBox,
  override val spake2: Spake2,
  override val symmetricKeyEncryptor: SymmetricKeyEncryptor,
  override val symmetricKeyGenerator: SymmetricKeyGenerator,
  override val f8eAuthSignatureStatusProvider: F8eAuthSignatureStatusProvider =
    F8eAuthSignatureStatusProviderImpl(),
  override val secureStoreFactory: EncryptedKeyValueStoreFactory =
    EncryptedKeyValueStoreFactoryImpl(platformContext, fileManager),
  override val authTokenDao: AuthTokenDao = AuthTokenDaoImpl(secureStoreFactory),
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
  override val uuidGenerator: UuidGenerator = UuidGeneratorImpl(),
  override val databaseIntegrityChecker: DatabaseIntegrityChecker,
  private val databaseDriverFactory: SqlDriverFactory =
    SqlDriverFactoryImpl(
      platformContext,
      fileDirectoryProvider,
      secureStoreFactory,
      uuidGenerator,
      appVariant,
      databaseIntegrityChecker
    ),
  override val bitkeyDatabaseProvider: BitkeyDatabaseProvider =
    BitkeyDatabaseProviderImpl(databaseDriverFactory, CoroutineScopes.AppScope),
  private val networkingDebugConfigDao: NetworkingDebugConfigDao =
    NetworkingDebugConfigDaoImpl(bitkeyDatabaseProvider),
  override val networkingDebugService: NetworkingDebugService =
    NetworkingDebugServiceImpl(networkingDebugConfigDao),
  override val appInstallationDao: AppInstallationDao =
    AppInstallationDaoImpl(bitkeyDatabaseProvider, uuidGenerator),
  override val localeCountryCodeProvider: LocaleCountryCodeProvider =
    LocaleCountryCodeProviderImpl(platformContext),
  private val telephonyCountryCodeProvider: TelephonyCountryCodeProvider =
    TelephonyCountryCodeProviderImpl(platformContext),
  override val countryCodeGuesser: CountryCodeGuesser = CountryCodeGuesserImpl(
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
      networkingDebugService = networkingDebugService,
      appInstallationDao = appInstallationDao,
      countryCodeGuesser = countryCodeGuesser
    ),
  private val internetNetworkReachabilityService: InternetNetworkReachabilityService =
    InternetNetworkReachabilityServiceImpl(),
  override val clock: Clock = Clock.System,
  private val networkReachabilityEventDao: NetworkReachabilityEventDao =
    NetworkReachabilityEventDaoImpl(
      clock = clock,
      databaseProvider = bitkeyDatabaseProvider
    ),
  override val deviceInfoProvider: DeviceInfoProvider = DeviceInfoProviderImpl(),
  private val newNetworkReachabilityProvider: NetworkReachabilityProvider =
    NewNetworkReachabilityProviderImpl(
      f8eNetworkReachabilityService = F8eNetworkReachabilityServiceImpl(
        deviceInfoProvider = deviceInfoProvider,
        unauthenticatedF8eHttpClient = null
      ),
      internetNetworkReachabilityService = internetNetworkReachabilityService,
      networkReachabilityEventDao = networkReachabilityEventDao
    ),
  private val frostWalletDescriptorFactory: FrostWalletDescriptorFactory,
  private val unauthenticatedF8eHttpClientFactory: UnauthenticatedF8eHttpClientFactory =
    UnauthenticatedF8eHttpClientFactory(
      appVariant = appVariant,
      platformInfoProvider = platformInfoProvider,
      appInstallationDao = appInstallationDao,
      countryCodeGuesser = countryCodeGuesser,
      datadogTracer = datadogTracer,
      deviceInfoProvider = deviceInfoProvider,
      networkReachabilityProvider = newNetworkReachabilityProvider,
      networkingDebugService = networkingDebugService
    ),
  override val appCoroutineScope: CoroutineScope = CoroutineScopes.AppScope,
  override val f8eNetworkReachabilityService: F8eNetworkReachabilityService =
    F8eNetworkReachabilityServiceImpl(
      deviceInfoProvider = deviceInfoProvider,
      unauthenticatedF8eHttpClient = UnauthenticatedOnlyF8eHttpClientImpl(
        appCoroutineScope = appCoroutineScope,
        unauthenticatedF8eHttpClientFactory = UnauthenticatedF8eHttpClientFactory(
          appVariant = appVariant,
          platformInfoProvider = platformInfoProvider,
          appInstallationDao = appInstallationDao,
          countryCodeGuesser = countryCodeGuesser,
          datadogTracer = datadogTracer,
          deviceInfoProvider = deviceInfoProvider,
          networkReachabilityProvider = null,
          networkingDebugService = networkingDebugService
        )
      )
    ),
  override val networkReachabilityProvider: NetworkReachabilityProvider =
    NetworkReachabilityProviderImpl(
      f8eNetworkReachabilityService = f8eNetworkReachabilityService,
      internetNetworkReachabilityService = internetNetworkReachabilityService,
      networkReachabilityEventDao = networkReachabilityEventDao
    ),
  private val unauthenticatedOnlyF8eHttpClient: UnauthenticatedF8eHttpClient =
    UnauthenticatedOnlyF8eHttpClientImpl(
      appCoroutineScope = appCoroutineScope,
      unauthenticatedF8eHttpClientFactory = unauthenticatedF8eHttpClientFactory
    ),
  override val authF8eClient: AuthF8eClient =
    AuthF8eClientImpl(unauthenticatedOnlyF8eHttpClient),
  override val accountAuthenticator: AccountAuthenticator =
    AccountAuthenticatorImpl(
      appAuthKeyMessageSigner = appAuthKeyMessageSigner,
      authF8eClient = authF8eClient
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
  override val accountService: AccountService = AccountServiceImpl(accountDao),
  private val appAuthPublicKeyProvider: AppAuthPublicKeyProvider =
    AppAuthPublicKeyProviderImpl(
      accountService = accountService,
      recoveryAppAuthPublicKeyProvider = recoveryAppAuthPublicKeyProvider
    ),
  private val authTokenRefresher: AppAuthTokenRefresher = AppAuthTokenRefresherImpl(
    authTokenDao,
    accountAuthenticator,
    authF8eClient,
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
      deviceInfoProvider = deviceInfoProvider,
      proofOfPossessionPluginProvider = proofOfPossessionPluginProvider,
      unauthenticatedF8eHttpClient = unauthenticatedOnlyF8eHttpClient,
      f8eHttpClientProvider = f8eHttpClientProvider,
      networkReachabilityProvider = networkReachabilityProvider,
      wsmVerifier = wsmVerifier
    ),
  override val exchangeRateF8eClient: ExchangeRateF8eClient =
    ExchangeRateF8eClientImpl(f8eHttpClient = f8eHttpClient),
  override val xChaCha20Poly1305: XChaCha20Poly1305,
  override val xNonceGenerator: XNonceGenerator,
) : AppComponent {
  override val bugsnagContext = BugsnagContextImpl(appCoroutineScope, appInstallationDao)

  override val firmwareDeviceInfoDao =
    FirmwareDeviceInfoDaoImpl(bitkeyDatabaseProvider)

  override val logWriterContextStore =
    LogWriterContextStoreImpl(appInstallationDao, firmwareDeviceInfoDao)
  private val logStoreWriter = LogStoreWriterImpl(logStore, clock)

  val loggerInitializer = LoggerInitializer(
    logWriterContextStore = logWriterContextStore,
    additionalLogWriters = logWritersProvider(logWriterContextStore) + logStoreWriter,
    appVariant = appVariant,
    appId = appId,
    appCoroutineScope = appCoroutineScope
  )

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

  private val deviceTokenF8eClient = AddDeviceTokenF8eClientImpl(f8eHttpClient)
  override val deviceTokenManager =
    DeviceTokenManagerImpl(
      deviceTokenF8eClient,
      deviceTokenConfigProvider,
      keyboxDao
    )

  private val featureFlagDao = FeatureFlagDaoImpl(bitkeyDatabaseProvider)
  private val nfcHapticsOnConnectedIsEnabledFeatureFlag =
    NfcHapticsOnConnectedIsEnabledFeatureFlag(featureFlagDao)

  override val feeBumpIsAvailableFeatureFlag = FeeBumpIsAvailableFeatureFlag(featureFlagDao)

  override val softwareWalletIsEnabledFeatureFlag =
    SoftwareWalletIsEnabledFeatureFlag(featureFlagDao)

  override val utxoConsolidationFeatureFlag = UtxoConsolidationFeatureFlag(featureFlagDao)
  override val utxoMaxConsolidationCountFeatureFlag =
    UtxoMaxConsolidationCountFeatureFlag(featureFlagDao)
  override val speedUpAllowShrinkingFeatureFlag = SpeedUpAllowShrinkingFeatureFlag(featureFlagDao)

  override val firmwareCommsLoggingFeatureFlag = FirmwareCommsLoggingFeatureFlag(
    featureFlagDao = featureFlagDao
  )
  override val asyncNfcSigningFeatureFlag = AsyncNfcSigningFeatureFlag(
    featureFlagDao = featureFlagDao
  )
  override val progressSpinnerForLongNfcOpsFeatureFlag =
    ProgressSpinnerForLongNfcOpsFeatureFlag(featureFlagDao = featureFlagDao)

  override val promptSweepFeatureFlag: PromptSweepFeatureFlag =
    PromptSweepFeatureFlag(featureFlagDao)

  override val mobileTestFeatureFlag =
    MobileTestFeatureFlag(featureFlagDao)

  override val coachmarksGlobalFeatureFlag =
    CoachmarksGlobalFeatureFlag(featureFlagDao)

  override val inheritanceFeatureFlag: InheritanceFeatureFlag =
    InheritanceFeatureFlag(featureFlagDao)

  override val expectedTransactionsPhase2FeatureFlag =
    ExpectedTransactionsPhase2FeatureFlag(featureFlagDao)

  override val mobilePayRevampFeatureFlag = MobilePayRevampFeatureFlag(featureFlagDao)

  override val sellBitcoinFeatureFlag = SellBitcoinFeatureFlag(featureFlagDao)

  override val exportToolsFeatureFlag = ExportToolsFeatureFlag(featureFlagDao)

  override val sellBitcoinQuotesEnabledFeatureFlag =
    SellBitcoinQuotesEnabledFeatureFlag(featureFlagDao)

  override val sellBitcoinMinAmountFeatureFlag = SellBitcoinMinAmountFeatureFlag(featureFlagDao)

  override val sellBitcoinMaxAmountFeatureFlag = SellBitcoinMaxAmountFeatureFlag(featureFlagDao)

  override val featureFlags: List<FeatureFlag<out FeatureFlagValue>> =
    setOf(
      mobileTestFeatureFlag,
      DoubleMobileTestFeatureFlag(featureFlagDao),
      StringFlagMobileTestFeatureFlag(featureFlagDao),
      promptSweepFeatureFlag,
      inheritanceFeatureFlag,
      coachmarksGlobalFeatureFlag,
      utxoConsolidationFeatureFlag,
      asyncNfcSigningFeatureFlag,
      mobilePayRevampFeatureFlag,
      sellBitcoinFeatureFlag,
      speedUpAllowShrinkingFeatureFlag,
      exportToolsFeatureFlag,
      utxoMaxConsolidationCountFeatureFlag,
      progressSpinnerForLongNfcOpsFeatureFlag,
      sellBitcoinQuotesEnabledFeatureFlag,
      sellBitcoinMinAmountFeatureFlag,
      sellBitcoinMaxAmountFeatureFlag,
      expectedTransactionsPhase2FeatureFlag,
      feeBumpIsAvailableFeatureFlag,
      nfcHapticsOnConnectedIsEnabledFeatureFlag,
      softwareWalletIsEnabledFeatureFlag,
      firmwareCommsLoggingFeatureFlag
    ).toList()

  private val appDeviceIdDao = AppDeviceIdDaoImpl(secureStoreFactory, uuidGenerator)
  override val localeLanguageCodeProvider = LocaleLanguageCodeProviderImpl(platformContext)
  override val featureFlagsF8eClient = featureFlagsF8eClientOverride ?: FeatureFlagsF8eClientImpl(
    f8eHttpClient = f8eHttpClient,
    appInstallationDao = appInstallationDao,
    platformInfoProvider = platformInfoProvider,
    localeCountryCodeProvider = localeCountryCodeProvider,
    localeLanguageCodeProvider = localeLanguageCodeProvider
  )

  private val hardwareInfoProvider =
    HardwareInfoProviderImpl(
      appInstallationDao,
      SerialNumberParserImpl(),
      firmwareDeviceInfoDao
    )
  private val analyticsEventProcessor =
    AnalyticsEventProcessorImpl(EventTrackerF8eClientImpl(f8eHttpClient))
  override val analyticsEventPeriodicProcessor =
    AnalyticsEventPeriodicProcessorImpl(
      queue = AnalyticsEventQueueImpl(bitkeyDatabaseProvider),
      processor = analyticsEventProcessor
    )

  override val appSessionManager = AppSessionManagerImpl(clock, uuidGenerator)
  override val eventStore = EventStoreImpl()
  private val defaultDebugOptionsDecider = DefaultDebugOptionsDeciderImpl(appVariant)
  override val debugOptionsService =
    DebugOptionsServiceImpl(bitkeyDatabaseProvider, defaultDebugOptionsDecider)

  override val appFunctionalityService = AppFunctionalityServiceImpl(
    accountService = accountService,
    debugOptionsService = debugOptionsService,
    networkReachabilityEventDao = networkReachabilityEventDao,
    networkReachabilityProvider = networkReachabilityProvider,
    f8eAuthSignatureStatusProvider = f8eAuthSignatureStatusProvider,
    appVariant = appVariant
  )

  override val analyticsTrackingPreference = AnalyticsTrackingPreferenceImpl(
    appVariant = appVariant,
    databaseProvider = bitkeyDatabaseProvider
  )

  override val eventTracker = EventTrackerImpl(
    appCoroutineScope = appCoroutineScope,
    appDeviceIdDao = appDeviceIdDao,
    deviceInfoProvider = deviceInfoProvider,
    accountService = accountService,
    debugOptionsService = debugOptionsService,
    clock = clock,
    countryCodeProvider = localeCountryCodeProvider,
    hardwareInfoProvider = hardwareInfoProvider,
    eventProcessor = analyticsEventProcessor,
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
  override val bitcoinPriceCardPreference = BitcoinPriceCardPreferenceImpl(
    databaseProvider = bitkeyDatabaseProvider,
    eventTracker = eventTracker,
    appCoroutineScope = appCoroutineScope
  )
  override val memfaultClient =
    MemfaultClientImpl(
      MemfaultHttpClientImpl(
        networkReachabilityProvider = networkReachabilityProvider
      )
    )
  override val fwupDataDao = FwupDataDaoImpl(bitkeyDatabaseProvider)
  private val fwupManifestParser = FwupManifestParserImpl()
  private val firmwareDownloader = FirmwareDownloaderImpl(memfaultClient, fileManager)
  override val firmwareMetadataDao = FirmwareMetadataDaoImpl(bitkeyDatabaseProvider)
  override val fwupDataFetcher =
    FwupDataFetcherImpl(fileManager, fwupManifestParser, firmwareDownloader)
  override val fwupProgressCalculator = FwupProgressCalculatorImpl()
  override val pushNotificationPermissionStatusProvider =
    PushNotificationPermissionStatusProviderImpl(platformContext)
  override val permissionChecker =
    PermissionCheckerImpl(platformContext, pushNotificationPermissionStatusProvider)
  private val hapticsPolicy = HapticsPolicyImpl(permissionChecker)
  override val haptics = HapticsImpl(platformContext, hapticsPolicy)
  override val nfcHaptics =
    NfcHapticsImpl(
      haptics,
      nfcHapticsOnConnectedIsEnabledFeatureFlag,
      appCoroutineScope
    )
  private val firmwareTelemetryEventProcessor = FirmwareTelemetryEventProcessorImpl(memfaultClient)
  override val periodicFirmwareTelemetryEventProcessor =
    FirmwareTelemetryEventPeriodicProcessorImpl(
      queue = FirmwareTelemetryEventQueueImpl(bitkeyDatabaseProvider),
      processor = firmwareTelemetryEventProcessor
    )
  private val firmwareCoredumpEventProcessor = FirmwareCoredumpEventProcessorImpl(memfaultClient)
  override val periodicFirmwareCoredumpProcessor =
    FirmwareCoredumpEventPeriodicProcessorImpl(
      queue = FirmwareCoredumpEventQueueImpl(bitkeyDatabaseProvider),
      processor = firmwareCoredumpEventProcessor
    )
  override val firmwareTelemetryUploader =
    FirmwareTelemetryUploaderImpl(
      appCoroutineScope = appCoroutineScope,
      firmwareCoredumpProcessor = firmwareCoredumpEventProcessor,
      firmwareTelemetryProcessor = firmwareTelemetryEventProcessor,
      teltra = teltra
    )

  private val registerWatchAddressQueue =
    RegisterWatchAddressQueueImpl(
      databaseProvider = bitkeyDatabaseProvider
    )

  private val registerWatchAddressService =
    RegisterWatchAddressF8eClientImpl(
      f8eHttpClient = f8eHttpClient
    )

  internal val registerWatchAddressProcessor =
    RegisterWatchAddressProcessorImpl(registerWatchAddressService)

  override val registerWatchAddressPeriodicProcessor = RegisterWatchAddressPeriodicProcessorImpl(
    queue = registerWatchAddressQueue,
    processor = registerWatchAddressProcessor
  )

  private val fiatMobilePayConfigurationDao =
    MobilePayFiatConfigDaoImpl(bitkeyDatabaseProvider)
  private val fiatMobilePayConfigurationF8eClient =
    MobilePayFiatConfigF8eClientImpl(
      f8eHttpClient = f8eHttpClient,
      fiatCurrencyDao = fiatCurrencyDao
    )

  private val fiatCurrencyDefinitionF8eClient = FiatCurrencyDefinitionF8eClientImpl(f8eHttpClient)

  override val fiatCurrenciesService = FiatCurrenciesServiceImpl(
    fiatCurrencyDao = fiatCurrencyDao,
    fiatCurrencyDefinitionF8eClient = fiatCurrencyDefinitionF8eClient,
    debugOptionsService = debugOptionsService
  )

  private val mobilePayFiatConfigRepository =
    MobilePayFiatConfigRepositoryImpl(
      mobilePayFiatConfigDao = fiatMobilePayConfigurationDao,
      mobilePayFiatConfigF8eClient = fiatMobilePayConfigurationF8eClient
    )

  override val mobilePayFiatConfigService = MobilePayFiatConfigServiceImpl(
    mobilePayFiatConfigRepository = mobilePayFiatConfigRepository,
    debugOptionsService = debugOptionsService,
    fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository
  )

  private val featureFlagSyncer = FeatureFlagSyncerImpl(
    accountService = accountService,
    debugOptionsService = debugOptionsService,
    featureFlagsF8eClient = featureFlagsF8eClient,
    clock = clock,
    featureFlags = featureFlags,
    appSessionManager = appSessionManager
  )

  override val featureFlagService = FeatureFlagServiceImpl(
    featureFlags = featureFlags,
    featureFlagSyncer = featureFlagSyncer
  )

  override val firmwareDataService = FirmwareDataServiceImpl(
    firmwareDeviceInfoDao = firmwareDeviceInfoDao,
    fwupDataFetcher = fwupDataFetcher,
    fwupDataDao = fwupDataDao,
    appSessionManager = appSessionManager,
    debugOptionsService = debugOptionsService
  )

  override val phoneNumberValidator = PhoneNumberValidatorImpl(
    countryCodeGuesser = countryCodeGuesser,
    phoneNumberLibBindings = phoneNumberLibBindings
  )

  override val notificationTouchpointF8eClient = NotificationTouchpointF8eClientImpl(
    f8eHttpClient = f8eHttpClient,
    phoneNumberValidator = phoneNumberValidator
  )

  override val notificationTouchpointDao = NotificationTouchpointDaoImpl(
    databaseProvider = bitkeyDatabaseProvider,
    phoneNumberValidator = phoneNumberValidator
  )

  val recoveryNotificationVerificationF8eClient =
    RecoveryNotificationVerificationF8eClientImpl(
      f8eHttpClient = f8eHttpClient
    )

  override val notificationTouchpointService = NotificationTouchpointServiceImpl(
    notificationTouchpointF8eClient = notificationTouchpointF8eClient,
    notificationTouchpointDao = notificationTouchpointDao,
    recoveryNotificationVerificationF8eClient = recoveryNotificationVerificationF8eClient,
    accountService = accountService
  )

  private val exchangeRateDao = ExchangeRateDaoImpl(
    databaseProvider = bitkeyDatabaseProvider
  )

  override val currencyConverter = CurrencyConverterImpl(
    accountService = accountService,
    exchangeRateDao = exchangeRateDao,
    exchangeRateF8eClient = exchangeRateF8eClient
  )

  override val exchangeRateService = ExchangeRateServiceImpl(
    exchangeRateDao = exchangeRateDao,
    exchangeRateF8eClient = exchangeRateF8eClient,
    appSessionManager = appSessionManager,
    keyboxDao = keyboxDao,
    clock = clock
  )

  override val relationshipsDao = RelationshipsDaoImpl(bitkeyDatabaseProvider)

  override val relationshipsEnrollmentAuthenticationDao =
    RelationshipsEnrollmentAuthenticationDaoImpl(
      appPrivateKeyDao = appPrivateKeyDao,
      databaseProvider = bitkeyDatabaseProvider
    )

  override val relationshipsCrypto = RelationshipsCryptoImpl(
    symmetricKeyGenerator = symmetricKeyGenerator,
    xChaCha20Poly1305 = xChaCha20Poly1305,
    xNonceGenerator = xNonceGenerator,
    spake2 = spake2,
    appAuthKeyMessageSigner = appAuthKeyMessageSigner,
    signatureVerifier = signatureVerifier,
    cryptoBox = cryptoBox
  )

  private val base32Encoding = Base32Encoding()

  override val relationshipsCodeBuilder = RelationshipsCodeBuilderImpl(
    base32Encoding = base32Encoding
  )

  private val socRecFake = SocRecF8eClientFake(
    uuidGenerator = uuidGenerator
  )

  private val relationshipsFake = RelationshipsF8eClientFake(
    uuidGenerator = uuidGenerator,
    backgroundScope = appCoroutineScope
  )

  private val socialRecoveryF8eClientImpl = SocRecF8eClientImpl(
    f8eHttpClient = f8eHttpClient
  )

  override val socRecF8eClientProvider = SocRecF8eClientProviderImpl(
    accountService = accountService,
    socRecFake = socRecFake,
    socRecF8eClient = socialRecoveryF8eClientImpl,
    debugOptionsService = debugOptionsService
  )

  override val relationshipsF8eClient = RelationshipsF8eClientImpl(
    f8eHttpClient = f8eHttpClient
  )

  override val relationshipsF8eClientProvider = RelationshipsF8eClientProviderImpl(
    accountService = accountService,
    relationshipsFake = relationshipsFake,
    relationshipsF8eClient = relationshipsF8eClient,
    debugOptionsService = debugOptionsService
  )

  override val socRecStartedChallengeDao = SocRecStartedChallengeDaoImpl(bitkeyDatabaseProvider)

  private val recoveryIncompleteDao = RecoveryIncompleteDaoImpl(bitkeyDatabaseProvider)
  override val postSocRecTaskRepository = PostSocRecTaskRepositoryImpl(recoveryIncompleteDao)

  override val relationshipsService = RelationshipsServiceImpl(
    relationshipsF8eClientProvider = relationshipsF8eClientProvider,
    relationshipsDao = relationshipsDao,
    relationshipsEnrollmentAuthenticationDao = relationshipsEnrollmentAuthenticationDao,
    relationshipsCrypto = relationshipsCrypto,
    relationshipsCodeBuilder = relationshipsCodeBuilder,
    appSessionManager = appSessionManager,
    accountService = accountService,
    appCoroutineScope = appCoroutineScope
  )

  override val socRecService = SocRecServiceImpl(
    postSocRecTaskRepository = postSocRecTaskRepository,
    relationshipsService = relationshipsService,
    appCoroutineScope = appCoroutineScope
  )

  override val endorseTrustedContactsService = EndorseTrustedContactsServiceImpl(
    accountService = accountService,
    relationshipsService = relationshipsService,
    relationshipsDao = relationshipsDao,
    relationshipsEnrollmentAuthenticationDao = relationshipsEnrollmentAuthenticationDao,
    relationshipsCrypto = relationshipsCrypto,
    endorseTrustedContactsF8eClientProvider = { relationshipsF8eClientProvider.get() }
  )

  override val socRecStartedChallengeAuthenticationDao =
    SocRecStartedChallengeAuthenticationDaoImpl(
      appPrivateKeyDao = appPrivateKeyDao,
      databaseProvider = bitkeyDatabaseProvider
    )

  override val socRecChallengeRepository = SocRecChallengeRepositoryImpl(
    socRec = socialRecoveryF8eClientImpl,
    relationshipsCodeBuilder = relationshipsCodeBuilder,
    relationshipsCrypto = relationshipsCrypto,
    socRecFake = socRecFake,
    socRecStartedChallengeDao = socRecStartedChallengeDao,
    socRecStartedChallengeAuthenticationDao = socRecStartedChallengeAuthenticationDao
  )

  override val socialChallengeVerifier = SocialChallengeVerifierImpl(
    socRecChallengeRepository = socRecChallengeRepository,
    relationshipsCrypto = relationshipsCrypto,
    relationshipsCodeBuilder = relationshipsCodeBuilder
  )

  override val inviteCodeLoader = InviteCodeLoaderImpl(
    relationshipsEnrollmentAuthenticationDao = relationshipsEnrollmentAuthenticationDao,
    recoveryCodeBuilder = relationshipsCodeBuilder
  )

  override val outgoingTransactionDetailDao =
    OutgoingTransactionDetailDaoImpl(bitkeyDatabaseProvider)
  private val bdkTransactionMapper = BdkTransactionMapperImpl(
    bdkAddressBuilder,
    outgoingTransactionDetailDao,
    utxoConsolidationFeatureFlag
  )
  private val bdkDatabaseConfigProvider = BdkDatabaseConfigProviderImpl(fileDirectoryProvider)
  private val bdkWalletProvider = BdkWalletProviderImpl(bdkWalletFactory, bdkDatabaseConfigProvider)
  override val electrumServerConfigRepository =
    ElectrumServerConfigRepositoryImpl(bitkeyDatabaseProvider)
  override val electrumServerSettingProvider = ElectrumServerSettingProviderImpl(
    keyboxDao,
    debugOptionsService,
    electrumServerConfigRepository
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
      deviceInfoProvider = deviceInfoProvider,
      electrumServerSettingProvider = electrumServerSettingProvider,
      electrumReachability = electrumReachability,
      networkReachabilityProvider = networkReachabilityProvider
    )

  override val bitcoinFeeRateEstimator =
    BitcoinFeeRateEstimatorImpl(
      mempoolHttpClient = MempoolHttpClientImpl(
        networkReachabilityProvider = networkReachabilityProvider
      ),
      bdkBlockchainProvider = bdkBlockchainProvider
    )

  override val feeBumpAllowShrinkingChecker = FeeBumpAllowShrinkingCheckerImpl(
    allowShrinkingFeatureFlag = speedUpAllowShrinkingFeatureFlag
  )

  override val spendingWalletProvider = SpendingWalletProviderImpl(
    bdkWalletProvider = bdkWalletProvider,
    bdkTransactionMapper = bdkTransactionMapper,
    bdkWalletSyncer = bdkWalletSyncer,
    bdkPsbtBuilder = bdkPartiallySignedTransactionBuilder,
    bdkTxBuilderFactory = bdkTxBuilderFactory,
    bdkAddressBuilder = bdkAddressBuilder,
    bdkBumpFeeTxBuilderFactory = bdkBumpFeeTxBuilderFactory,
    appSessionManager = appSessionManager,
    bitcoinFeeRateEstimator = bitcoinFeeRateEstimator,
    feeBumpAllowShrinkingCheckerImpl = feeBumpAllowShrinkingChecker
  )
  override val bitcoinMultiSigDescriptorBuilder = BitcoinMultiSigDescriptorBuilderImpl()
  override val appSpendingWalletProvider = AppSpendingWalletProviderImpl(
    spendingWalletProvider = spendingWalletProvider,
    appPrivateKeyDao = appPrivateKeyDao,
    descriptorBuilder = bitcoinMultiSigDescriptorBuilder,
    frostWalletDescriptorFactory = frostWalletDescriptorFactory
  )
  private val watchingWalletProvider = WatchingWalletProviderImpl(
    bdkWalletProvider = bdkWalletProvider,
    bdkTransactionMapper = bdkTransactionMapper,
    bdkWalletSyncer = bdkWalletSyncer,
    bdkPsbtBuilder = bdkPartiallySignedTransactionBuilder,
    bdkTxBuilderFactory = bdkTxBuilderFactory,
    bdkAddressBuilder = bdkAddressBuilder,
    bdkBumpFeeTxBuilderFactory = bdkBumpFeeTxBuilderFactory,
    appSessionManager = appSessionManager,
    bitcoinFeeRateEstimator = bitcoinFeeRateEstimator,
    feeBumpAllowShrinkingChecker = feeBumpAllowShrinkingChecker
  )
  private val watchingWalletDescriptorProvider = WatchingWalletDescriptorProviderImpl(
    descriptorBuilder = bitcoinMultiSigDescriptorBuilder
  )

  override val keysetWalletProvider =
    KeysetWalletProviderImpl(
      watchingWalletProvider = watchingWalletProvider,
      watchingWalletDescriptorProvider = watchingWalletDescriptorProvider,
      frostWalletDescriptorFactory = frostWalletDescriptorFactory
    )
  override val extendedKeyGenerator =
    ExtendedKeyGeneratorImpl(bdkMnemonicGenerator, bdkDescriptorSecretKeyGenerator)
  private val spendingKeyGenerator = SpendingKeyGeneratorImpl(extendedKeyGenerator)
  private val authKeyGenerator = AppAuthKeyGeneratorImpl(secp256k1KeyGenerator)

  override val listKeysetsF8eClient = ListKeysetsF8eClientImpl(
    f8eHttpClient = f8eHttpClient,
    uuidGenerator = uuidGenerator
  )

  private val exportTransactionsAsCsvSerializer = ExportTransactionsAsCsvSerializerImpl()
  override val exportTransactionsService = ExportTransactionsServiceImpl(
    accountService = accountService,
    watchingWalletProvider = watchingWalletProvider,
    bitcoinMultiSigDescriptorBuilder = bitcoinMultiSigDescriptorBuilder,
    exportTransactionsAsCsvSerializer = exportTransactionsAsCsvSerializer,
    listKeysetsF8eClient = listKeysetsF8eClient
  )

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

  override val bitcoinBlockchain =
    BitcoinBlockchainImpl(
      bdkBlockchainProvider = bdkBlockchainProvider,
      bdkPsbtBuilder = bdkPartiallySignedTransactionBuilder,
      clock = clock
    )

  override val bitcoinWalletService = BitcoinWalletServiceImpl(
    currencyConverter = currencyConverter,
    accountService = accountService,
    appSpendingWalletProvider = appSpendingWalletProvider,
    fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
    appSessionManager = appSessionManager,
    exchangeRateService = exchangeRateService,
    outgoingTransactionDetailDao = outgoingTransactionDetailDao,
    bitcoinBlockchain = bitcoinBlockchain
  )

  private val getPurchaseQuoteListF8eClient = GetPurchaseQuoteListF8eClientImpl(
    countryCodeGuesser = countryCodeGuesser,
    f8eHttpClient = f8eHttpClient
  )

  private val getPurchaseRedirectF8eClient = GetPurchaseRedirectF8eClientImpl(f8eHttpClient)

  override val partnershipTransactionsService = PartnershipTransactionsServiceImpl(
    expectedTransactionsFlag = expectedTransactionsPhase2FeatureFlag,
    accountService = accountService,
    dao = PartnershipTransactionsDaoImpl(bitkeyDatabaseProvider),
    getPartnershipTransactionF8eClient = GetPartnershipTransactionF8eClientImpl(f8eHttpClient),
    clock = clock,
    appSessionManager = appSessionManager
  )

  private val getPurchaseOptionsF8eClient =
    GetPurchaseOptionsF8eClientImpl(countryCodeGuesser, f8eHttpClient)

  override val transactionsActivityService = TransactionsActivityServiceImpl(
    expectedTransactionsPhase2FeatureFlag = expectedTransactionsPhase2FeatureFlag,
    partnershipTransactionsService = partnershipTransactionsService,
    bitcoinWalletService = bitcoinWalletService
  )

  override val bitcoinAddressService = BitcoinAddressServiceImpl(
    registerWatchAddressProcessor = registerWatchAddressProcessor,
    bitcoinWalletService = bitcoinWalletService,
    accountService = accountService
  )

  override val partnershipPurchaseService = PartnershipPurchaseServiceImpl(
    accountService = accountService,
    bitcoinAddressService = bitcoinAddressService,
    partnershipTransactionsService = partnershipTransactionsService,
    fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
    getPurchaseOptionsF8eClient = getPurchaseOptionsF8eClient,
    getPurchaseQuoteListF8eClient = getPurchaseQuoteListF8eClient,
    getPurchaseRedirectF8eClient = getPurchaseRedirectF8eClient
  )

  override val utxoConsolidationService = UtxoConsolidationServiceImpl(
    accountService = accountService,
    bitcoinWalletService = bitcoinWalletService,
    bitcoinAddressService = bitcoinAddressService,
    bitcoinFeeRateEstimator = bitcoinFeeRateEstimator,
    utxoMaxConsolidationCountFeatureFlag = utxoMaxConsolidationCountFeatureFlag
  )

  override val exportWatchingDescriptorService = ExportWatchingDescriptorServiceImpl(
    accountService = accountService,
    watchingWalletDescriptorProvider = watchingWalletDescriptorProvider
  )

  private val spendingLimitDao =
    SpendingLimitDaoImpl(
      databaseProvider = bitkeyDatabaseProvider
    )

  private val spendingLimitF8eClient = MobilePaySpendingLimitF8eClientImpl(
    f8eHttpClient = f8eHttpClient,
    clock = clock
  )

  private val mobilePayBalanceF8eClient =
    MobilePayBalanceF8eClientImpl(
      f8eHttpClient = f8eHttpClient,
      fiatCurrencyDao = fiatCurrencyDao
    )

  private val mobilePayStatusProvider = MobilePayStatusRepositoryImpl(
    spendingLimitDao = spendingLimitDao,
    mobilePayBalanceF8eClient = mobilePayBalanceF8eClient,
    uuidGenerator = uuidGenerator,
    bitcoinWalletService = bitcoinWalletService
  )

  override val mobilePaySigningF8eClient = MobilePaySigningF8eClientImpl(f8eHttpClient)

  override val mobilePayService = MobilePayServiceImpl(
    eventTracker = eventTracker,
    spendingLimitDao = spendingLimitDao,
    spendingLimitF8eClient = spendingLimitF8eClient,
    mobilePayStatusRepository = mobilePayStatusProvider,
    appSessionManager = appSessionManager,
    bitcoinWalletService = bitcoinWalletService,
    accountService = accountService,
    currencyConverter = currencyConverter,
    fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
    mobilePaySigningF8eClient = mobilePaySigningF8eClient
  )
  private val inheritanceSyncDao = InheritanceSyncDaoImpl(
    databaseProvider = bitkeyDatabaseProvider,
    clock = clock
  )
  private val inheritanceMaterialF8eClient = UploadInheritanceMaterialF8eClientImpl(
    f8eClient = f8eHttpClient
  )

  private val retrieveInheritanceClaimsF8eClient = RetrieveInheritanceClaimsF8EClientImpl(
    f8eHttpClient = f8eHttpClient
  )

  private val inheritanceRelationshipsProvider = InheritanceRelationshipsAdapter(
    relationshipsService = relationshipsService
  )
  private val inheritanceMaterialRepository = InheritanceCryptoImpl(
    appPrivateKeyDao = appPrivateKeyDao,
    relationships = inheritanceRelationshipsProvider,
    crypto = relationshipsCrypto
  )

  private val startInheritanceClaimF8eClient = StartInheritanceClaimF8eClientImpl(
    f8eClient = f8eHttpClient
  )

  override val inheritanceClaimsDao = InheritanceClaimsDaoImpl(
    databaseProvider = bitkeyDatabaseProvider
  )

  override val inheritanceService = InheritanceServiceImpl(
    accountService = accountService,
    relationshipsService = relationshipsService,
    appCoroutineScope = appCoroutineScope,
    inheritanceSyncDao = inheritanceSyncDao,
    inheritanceMaterialF8eClient = inheritanceMaterialF8eClient,
    startInheritanceClaimF8eClient = startInheritanceClaimF8eClient,
    inheritanceCrypto = inheritanceMaterialRepository,
    retrieveInheritanceClaimsF8EClient = retrieveInheritanceClaimsF8eClient,
    inheritanceClaimsDao = inheritanceClaimsDao,
    appSessionManager = appSessionManager,
    inheritanceFeatureFlag = inheritanceFeatureFlag
  )

  private val inheritanceMaterialSyncWorker = InheritanceMaterialSyncWorker(
    inheritanceService = inheritanceService,
    inheritanceRelationshipsProvider = inheritanceRelationshipsProvider,
    keyboxDao = keyboxDao,
    featureFlag = inheritanceFeatureFlag
  )

  override val electrumConfigService = ElectrumConfigServiceImpl(
    electrumServerConfigRepository = electrumServerConfigRepository,
    debugOptionsService = debugOptionsService,
    getBdkConfigurationF8eClient = GetBdkConfigurationF8eClientImpl(
      f8eHttpClient,
      deviceInfoProvider
    )
  )

  private val appWorkerProvider = AppWorkerProviderImpl(
    eventTracker = eventTracker,
    networkingDebugService = networkingDebugService,
    periodicEventProcessor = analyticsEventPeriodicProcessor,
    periodicFirmwareCoredumpProcessor = periodicFirmwareCoredumpProcessor,
    periodicFirmwareTelemetryProcessor = periodicFirmwareTelemetryEventProcessor,
    periodicRegisterWatchAddressProcessor = registerWatchAddressPeriodicProcessor,
    mobilePayFiatConfigSyncWorker = mobilePayFiatConfigService,
    featureFlagSyncWorker = featureFlagService,
    firmwareDataSyncWorker = firmwareDataService,
    notificationTouchpointSyncWorker = notificationTouchpointService,
    bitcoinAddressRegisterWatchAddressWorker = bitcoinAddressService,
    endorseTrustedContactsWorker = endorseTrustedContactsService,
    bitcoinWalletSyncWorker = bitcoinWalletService,
    exchangeRateSyncWorker = exchangeRateService,
    fiatCurrenciesSyncWorker = fiatCurrenciesService,
    syncRelationshipsWorker = relationshipsService,
    mobilePayBalanceSyncWorker = mobilePayService,
    appFunctionalitySyncWorker = appFunctionalityService,
    inheritanceMaterialSyncWorker = inheritanceMaterialSyncWorker,
    inheritanceClaimsSyncWorker = inheritanceService,
    transactionsActivitySyncWorker = transactionsActivityService,
    electrumConfigSyncWorker = electrumConfigService,
    partnershipTransactionsSyncWorker = partnershipTransactionsService
  )

  override val appWorkerExecutor = AppWorkerExecutorImpl(
    appScope = appCoroutineScope,
    workerProvider = appWorkerProvider
  )
}
