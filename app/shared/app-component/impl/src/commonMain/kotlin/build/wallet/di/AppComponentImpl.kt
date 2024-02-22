package build.wallet.di

import build.wallet.account.AccountDaoImpl
import build.wallet.account.AccountRepositoryImpl
import build.wallet.account.analytics.AppInstallationDaoImpl
import build.wallet.analytics.events.AnalyticsTrackingEnabledFeatureFlag
import build.wallet.analytics.events.AppDeviceIdDaoImpl
import build.wallet.analytics.events.EventQueueImpl
import build.wallet.analytics.events.EventSenderImpl
import build.wallet.analytics.events.EventStoreImpl
import build.wallet.analytics.events.EventTrackerImpl
import build.wallet.analytics.events.HardwareInfoProviderImpl
import build.wallet.analytics.events.PlatformInfoProviderImpl
import build.wallet.analytics.events.SessionIdProviderImpl
import build.wallet.auth.AccountAuthenticatorImpl
import build.wallet.auth.AppAuthKeyMessageSignerImpl
import build.wallet.auth.AppAuthPublicKeyProviderImpl
import build.wallet.auth.AppAuthTokenRefresherImpl
import build.wallet.auth.AuthTokenDaoImpl
import build.wallet.auth.AuthTokensRepositoryImpl
import build.wallet.auth.InactiveDeviceIsEnabledFeatureFlag
import build.wallet.availability.F8eAuthSignatureStatusProvider
import build.wallet.availability.F8eAuthSignatureStatusProviderImpl
import build.wallet.availability.F8eNetworkReachabilityServiceImpl
import build.wallet.availability.InternetNetworkReachabilityServiceImpl
import build.wallet.availability.NetworkReachabilityEventDaoImpl
import build.wallet.availability.NetworkReachabilityProviderImpl
import build.wallet.bdk.bindings.BdkAddressBuilder
import build.wallet.bdk.bindings.BdkBlockchainFactory
import build.wallet.bdk.bindings.BdkBumpFeeTxBuilderFactory
import build.wallet.bdk.bindings.BdkDescriptorSecretKeyGenerator
import build.wallet.bdk.bindings.BdkMnemonicGenerator
import build.wallet.bdk.bindings.BdkPartiallySignedTransactionBuilder
import build.wallet.bdk.bindings.BdkTxBuilderFactory
import build.wallet.bdk.bindings.BdkWalletFactory
import build.wallet.bitcoin.AppPrivateKeyDaoImpl
import build.wallet.bitcoin.bdk.BdkBlockchainProviderImpl
import build.wallet.bitcoin.bdk.BdkDatabaseConfigProviderImpl
import build.wallet.bitcoin.bdk.BdkTransactionMapperImpl
import build.wallet.bitcoin.bdk.BdkWalletProviderImpl
import build.wallet.bitcoin.bdk.BdkWalletSyncerImpl
import build.wallet.bitcoin.bdk.ElectrumReachabilityImpl
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilderImpl
import build.wallet.bitcoin.keys.ExtendedKeyGeneratorImpl
import build.wallet.bitcoin.lightning.LightningIsAvailableFeatureFlag
import build.wallet.bitcoin.sync.ElectrumServerConfigRepositoryImpl
import build.wallet.bitcoin.sync.ElectrumServerSettingProviderImpl
import build.wallet.bitcoin.transactions.BitcoinTransactionAppSignerImpl
import build.wallet.bitcoin.transactions.TransactionDetailDaoImpl
import build.wallet.bitcoin.wallet.SpendingWalletProviderImpl
import build.wallet.bitcoin.wallet.WatchingWalletProviderImpl
import build.wallet.bugsnag.BugsnagContextImpl
import build.wallet.cloud.backup.CloudBackupHealthFeatureFlag
import build.wallet.coroutines.scopes.CoroutineScopes
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.datadog.DatadogRumMonitor
import build.wallet.datadog.DatadogTracer
import build.wallet.deposit.PurchaseFlowIsEnabledFeatureFlag
import build.wallet.encrypt.MessageSigner
import build.wallet.encrypt.Secp256k1KeyGenerator
import build.wallet.f8e.analytics.EventTrackerServiceImpl
import build.wallet.f8e.auth.AuthenticationServiceImpl
import build.wallet.f8e.client.DatadogTracerPluginProvider
import build.wallet.f8e.client.F8eHttpClientImpl
import build.wallet.f8e.client.F8eHttpClientProvider
import build.wallet.f8e.client.ProofOfPossessionPluginProvider
import build.wallet.f8e.client.UnauthenticatedOnlyF8eHttpClientImpl
import build.wallet.f8e.debug.NetworkingDebugConfigDaoImpl
import build.wallet.f8e.debug.NetworkingDebugConfigRepositoryImpl
import build.wallet.f8e.onboarding.AddDeviceTokenServiceImpl
import build.wallet.feature.FeatureFlagDaoImpl
import build.wallet.feature.FeatureFlagInitializerImpl
import build.wallet.featureflag.AndroidTransitionsIsEnabledFeatureFlag
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
import build.wallet.keybox.KeyboxDaoImpl
import build.wallet.keybox.config.TemplateKeyboxConfigDaoImpl
import build.wallet.keybox.keys.AppAuthKeyGeneratorImpl
import build.wallet.keybox.keys.AppKeysGeneratorImpl
import build.wallet.keybox.keys.OnboardingAppKeyKeystoreImpl
import build.wallet.keybox.keys.SpendingKeyGeneratorImpl
import build.wallet.keybox.wallet.AppSpendingWalletProviderImpl
import build.wallet.keybox.wallet.KeysetWalletProviderImpl
import build.wallet.ktor.result.client.KtorLogLevelPolicyImpl
import build.wallet.ldk.LdkNodeServiceMock
import build.wallet.logging.LogWriterContextStore
import build.wallet.logging.LogWriterContextStoreImpl
import build.wallet.logging.dev.LogStoreImpl
import build.wallet.logging.dev.LogStoreWriterImpl
import build.wallet.logging.initializeLogger
import build.wallet.memfault.MemfaultHttpClientImpl
import build.wallet.memfault.MemfaultServiceImpl
import build.wallet.money.MultipleFiatCurrencyEnabledFeatureFlag
import build.wallet.money.currency.FiatCurrencyDaoImpl
import build.wallet.money.display.BitcoinDisplayPreferenceDaoImpl
import build.wallet.money.display.BitcoinDisplayPreferenceRepositoryImpl
import build.wallet.money.display.FiatCurrencyPreferenceDaoImpl
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryImpl
import build.wallet.nfc.haptics.NfcHapticsImpl
import build.wallet.nfc.haptics.NfcHapticsIsEnabledFeatureFlag
import build.wallet.nfc.haptics.NfcHapticsOnConnectedIsEnabledFeatureFlag
import build.wallet.nfc.haptics.NfcHapticsOnFailureIsEnabledFeatureFlag
import build.wallet.nfc.haptics.NfcHapticsOnSuccessIsEnabledFeatureFlag
import build.wallet.nfc.mask.NfcMaskIsEnabledFeatureFlag
import build.wallet.notifications.DeviceTokenManagerImpl
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
import build.wallet.platform.random.UuidImpl
import build.wallet.platform.settings.LocaleCountryCodeProviderImpl
import build.wallet.platform.settings.LocaleCurrencyCodeProviderImpl
import build.wallet.platform.versions.OsVersionInfoProviderImpl
import build.wallet.queueprocessor.BatcherProcessorImpl
import build.wallet.recovery.RecoveryAppAuthPublicKeyProviderImpl
import build.wallet.recovery.RecoveryDaoImpl
import build.wallet.recovery.emergencyaccess.EmergencyAccessFeatureFlag
import build.wallet.sqldelight.SqlDriverFactoryImpl
import build.wallet.statemachine.send.FeeBumpIsAvailableFeatureFlag
import build.wallet.statemachine.settings.full.feedback.FeedbackFormNewUiEnabledFeatureFlag
import build.wallet.store.EncryptedKeyValueStoreFactoryImpl
import build.wallet.store.KeyValueStoreFactoryImpl
import co.touchlab.kermit.LogWriter
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class AppComponentImpl(
  override val appId: AppId,
  override val appVariant: AppVariant,
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
  override val messageSigner: MessageSigner,
  override val platformContext: PlatformContext,
  override val secp256k1KeyGenerator: Secp256k1KeyGenerator,
  teltra: Teltra,
  override val hardwareAttestation: HardwareAttestation,
  override val recoverySyncFrequency: Duration = 1.minutes,
  override val f8eAuthSignatureStatusProvider: F8eAuthSignatureStatusProvider =
    F8eAuthSignatureStatusProviderImpl(),
) : AppComponent {
  override val appCoroutineScope = CoroutineScopes.AppScope
  override val clock = Clock.System
  override val secureStoreFactory = EncryptedKeyValueStoreFactoryImpl(platformContext, fileManager)
  override val uuid = UuidImpl()
  private val databaseDriverFactory =
    SqlDriverFactoryImpl(
      platformContext,
      fileDirectoryProvider,
      secureStoreFactory,
      uuid,
      appVariant
    )
  override val bitkeyDatabaseProvider = BitkeyDatabaseProviderImpl(databaseDriverFactory)
  override val appInstallationDao =
    AppInstallationDaoImpl(bitkeyDatabaseProvider, uuid)
  override val bugsnagContext = BugsnagContextImpl(appCoroutineScope, appInstallationDao)
  override val logWriterContextStore = LogWriterContextStoreImpl(appInstallationDao)
  override val logStore = LogStoreImpl()
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

  override val keyboxDao = KeyboxDaoImpl(bitkeyDatabaseProvider)
  private val accountDao = AccountDaoImpl(bitkeyDatabaseProvider)
  override val accountRepository = AccountRepositoryImpl(accountDao)
  override val keyValueStoreFactory = KeyValueStoreFactoryImpl(platformContext, fileManager)
  override val ktorLogLevelPolicy = KtorLogLevelPolicyImpl(appVariant)

  override val osVersionInfoProvider = OsVersionInfoProviderImpl()
  override val platformInfoProvider =
    PlatformInfoProviderImpl(
      platformContext,
      appId,
      appVersion,
      osVersionInfoProvider
    )

  private val bitcoinDisplayPreferenceDao = BitcoinDisplayPreferenceDaoImpl(bitkeyDatabaseProvider)
  override val bitcoinDisplayPreferenceRepository =
    BitcoinDisplayPreferenceRepositoryImpl(
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
      fiatCurrencyDao = fiatCurrencyDao,
      fiatCurrencyPreferenceDao = fiatCurrencyPreferenceDao,
      localeCurrencyCodeProvider = localeCurrencyCodeProvider
    )

  val networkingDebugConfigDao = NetworkingDebugConfigDaoImpl(bitkeyDatabaseProvider)
  override val networkingDebugConfigRepository =
    NetworkingDebugConfigRepositoryImpl(networkingDebugConfigDao)

  val f8eHttpClientProvider =
    F8eHttpClientProvider(
      appId = appId,
      appVersion = appVersion,
      platformInfoProvider = platformInfoProvider,
      ktorLogLevelPolicy = ktorLogLevelPolicy,
      datadogTracerPluginProvider = DatadogTracerPluginProvider(datadogTracer = datadogTracer),
      networkingDebugConfigRepository = networkingDebugConfigRepository
    )

  private val f8eNetworkReachabilityService =
    F8eNetworkReachabilityServiceImpl(
      unauthenticatedF8eHttpClient =
        UnauthenticatedOnlyF8eHttpClientImpl(
          f8eHttpClientProvider = f8eHttpClientProvider,
          networkReachabilityProvider = null
        )
    )
  private val internetNetworkReachabilityService = InternetNetworkReachabilityServiceImpl()

  override val networkReachabilityEventDao =
    NetworkReachabilityEventDaoImpl(
      clock = clock,
      databaseProvider = bitkeyDatabaseProvider
    )

  override val networkReachabilityProvider =
    NetworkReachabilityProviderImpl(
      f8eNetworkReachabilityService = f8eNetworkReachabilityService,
      internetNetworkReachabilityService = internetNetworkReachabilityService,
      networkReachabilityEventDao = networkReachabilityEventDao
    )

  val unauthenticatedOnlyF8eHttpClient =
    UnauthenticatedOnlyF8eHttpClientImpl(
      f8eHttpClientProvider = f8eHttpClientProvider,
      networkReachabilityProvider = networkReachabilityProvider
    )

  val authenticationService = AuthenticationServiceImpl(unauthenticatedOnlyF8eHttpClient)

  override val authTokenDao = AuthTokenDaoImpl(secureStoreFactory)
  override val appPrivateKeyDao = AppPrivateKeyDaoImpl(secureStoreFactory)
  override val appAuthKeyMessageSigner =
    AppAuthKeyMessageSignerImpl(
      appPrivateKeyDao,
      messageSigner
    )

  override val accountAuthenticator =
    AccountAuthenticatorImpl(
      appAuthKeyMessageSigner = appAuthKeyMessageSigner,
      authenticationService = authenticationService
    )

  override val recoveryDao =
    RecoveryDaoImpl(
      databaseProvider = bitkeyDatabaseProvider
    )

  val recoveryAppAuthPublicKeyProvider =
    RecoveryAppAuthPublicKeyProviderImpl(
      recoveryDao = recoveryDao
    )

  val appAuthPublicKeyProvider =
    AppAuthPublicKeyProviderImpl(
      accountRepository = accountRepository,
      recoveryAppAuthPublicKeyProvider = recoveryAppAuthPublicKeyProvider
    )

  val authTokenRefresher =
    AppAuthTokenRefresherImpl(
      authTokenDao,
      accountAuthenticator,
      authenticationService,
      appAuthPublicKeyProvider,
      f8eAuthSignatureStatusProvider
    )

  override val authTokensRepository = AuthTokensRepositoryImpl(authTokenDao, authTokenRefresher)

  private val proofOfPossessionPluginProvider =
    ProofOfPossessionPluginProvider(authTokensRepository, appAuthKeyMessageSigner, keyboxDao)

  override val f8eHttpClient =
    F8eHttpClientImpl(
      authTokensRepository = authTokensRepository,
      proofOfPossessionPluginProvider = proofOfPossessionPluginProvider,
      unauthenticatedF8eHttpClient = unauthenticatedOnlyF8eHttpClient,
      f8eHttpClientProvider = f8eHttpClientProvider,
      networkReachabilityProvider = networkReachabilityProvider
    )

  private val deviceTokenService = AddDeviceTokenServiceImpl(f8eHttpClient)
  override val deviceTokenManager =
    DeviceTokenManagerImpl(
      deviceTokenService,
      deviceTokenConfigProvider,
      keyboxDao
    )

  private val featureFlagDao = FeatureFlagDaoImpl(bitkeyDatabaseProvider)
  override val lightningIsAvailableFeatureFlag =
    LightningIsAvailableFeatureFlag(featureFlagDao)
  private val nfcHapticsIsEnabledFeatureFlag = NfcHapticsIsEnabledFeatureFlag(featureFlagDao)
  private val nfcHapticsOnConnectedIsEnabledFeatureFlag =
    NfcHapticsOnConnectedIsEnabledFeatureFlag(featureFlagDao)
  private val nfcHapticsOnFailureIsEnabledFeatureFlag =
    NfcHapticsOnFailureIsEnabledFeatureFlag(featureFlagDao)
  private val nfcHapticsOnSuccessIsEnabledFeatureFlag =
    NfcHapticsOnSuccessIsEnabledFeatureFlag(featureFlagDao)
  private val nfcMaskIsEnabledFeatureFlag =
    NfcMaskIsEnabledFeatureFlag(appVariant, featureFlagDao)
  private val analyticsTrackingEnabledFeatureFlag =
    AnalyticsTrackingEnabledFeatureFlag(
      appVariant = appVariant,
      featureFlagDao = featureFlagDao
    )
  override val purchaseFlowIsEnabledFeatureFlag =
    PurchaseFlowIsEnabledFeatureFlag(featureFlagDao)
  override val multipleFiatCurrencyEnabledFeatureFlag =
    MultipleFiatCurrencyEnabledFeatureFlag(featureFlagDao)
  override val emergencyAccessFeatureFlag: EmergencyAccessFeatureFlag =
    EmergencyAccessFeatureFlag(featureFlagDao)
  override val androidTransitionsIsEnabledFeatureFlag =
    AndroidTransitionsIsEnabledFeatureFlag(featureFlagDao, appVariant)
  override val feeBumpIsAvailableFeatureFlag = FeeBumpIsAvailableFeatureFlag(featureFlagDao)
  override val feedbackFormNewUiEnabledFeatureFlag =
    FeedbackFormNewUiEnabledFeatureFlag(featureFlagDao)
  override val cloudBackupHealthFeatureFlag = CloudBackupHealthFeatureFlag(featureFlagDao, appVariant)
  override val inactiveDeviceIsEnabledFeatureFlag = InactiveDeviceIsEnabledFeatureFlag(featureFlagDao)

  override val allFeatureFlags =
    setOf(
      multipleFiatCurrencyEnabledFeatureFlag,
      emergencyAccessFeatureFlag,
      cloudBackupHealthFeatureFlag,
      purchaseFlowIsEnabledFeatureFlag,
      androidTransitionsIsEnabledFeatureFlag,
      lightningIsAvailableFeatureFlag,
      nfcHapticsIsEnabledFeatureFlag,
      nfcHapticsOnConnectedIsEnabledFeatureFlag,
      nfcHapticsOnFailureIsEnabledFeatureFlag,
      nfcHapticsOnSuccessIsEnabledFeatureFlag,
      nfcMaskIsEnabledFeatureFlag,
      analyticsTrackingEnabledFeatureFlag,
      feeBumpIsAvailableFeatureFlag,
      feedbackFormNewUiEnabledFeatureFlag,
      inactiveDeviceIsEnabledFeatureFlag
    ).toList()
  override val featureFlagInitializer = FeatureFlagInitializerImpl(allFeatureFlags)
  private val appDeviceIdDao = AppDeviceIdDaoImpl(secureStoreFactory, uuid)
  override val deviceInfoProvider = DeviceInfoProviderImpl()
  override val localeCountryCodeProvider = LocaleCountryCodeProviderImpl(platformContext)
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

  override val sessionIdProvider = SessionIdProviderImpl(clock, uuid)
  override val eventStore = EventStoreImpl()
  override val templateKeyboxConfigDao =
    TemplateKeyboxConfigDaoImpl(
      appVariant,
      bitkeyDatabaseProvider
    )

  override val eventTracker =
    EventTrackerImpl(
      appCoroutineScope = appCoroutineScope,
      appDeviceIdDao = appDeviceIdDao,
      deviceInfoProvider = deviceInfoProvider,
      keyboxDao = keyboxDao,
      templateKeyboxConfigDao = templateKeyboxConfigDao,
      clock = clock,
      countryCodeProvider = localeCountryCodeProvider,
      hardwareInfoProvider = hardwareInfoProvider,
      eventProcessor = periodicEventProcessor,
      appInstallationDao = appInstallationDao,
      platformInfoProvider = platformInfoProvider,
      sessionIdProvider = sessionIdProvider,
      eventStore = eventStore,
      analyticsTrackingEnabledFeatureFlag = analyticsTrackingEnabledFeatureFlag,
      bitcoinDisplayPreferenceRepository = bitcoinDisplayPreferenceRepository,
      localeCurrencyCodeProvider = localeCurrencyCodeProvider,
      fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository
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
      nfcHapticsIsEnabledFeatureFlag,
      nfcHapticsOnConnectedIsEnabledFeatureFlag,
      nfcHapticsOnFailureIsEnabledFeatureFlag,
      nfcHapticsOnSuccessIsEnabledFeatureFlag,
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
  override val transactionDetailDao = TransactionDetailDaoImpl(bitkeyDatabaseProvider)
  private val bdkTransactionMapper =
    BdkTransactionMapperImpl(bdkAddressBuilder, transactionDetailDao)
  private val bdkDatabaseConfigProvider = BdkDatabaseConfigProviderImpl(fileDirectoryProvider)
  private val bdkWalletProvider = BdkWalletProviderImpl(bdkWalletFactory, bdkDatabaseConfigProvider)
  override val electrumServerDao = ElectrumServerConfigRepositoryImpl(bitkeyDatabaseProvider)
  override val electrumServerSettingProvider =
    ElectrumServerSettingProviderImpl(
      keyboxDao,
      templateKeyboxConfigDao,
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
  override val spendingWalletProvider =
    SpendingWalletProviderImpl(
      bdkWalletProvider,
      bdkTransactionMapper,
      bdkWalletSyncer,
      bdkPartiallySignedTransactionBuilder,
      bdkTxBuilderFactory,
      bdkAddressBuilder,
      bdkBumpFeeTxBuilderFactory
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
      bdkBumpFeeTxBuilderFactory
    )
  override val keysetWalletProvider =
    KeysetWalletProviderImpl(
      watchingWalletProvider = watchingWalletProvider,
      descriptorBuilder = bitcoinMultiSigDescriptorBuilder
    )
  override val bitcoinTransactionAppSigner =
    BitcoinTransactionAppSignerImpl(appSpendingWalletProvider)
  override val ldkNodeService =
    LdkNodeServiceMock()
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
      uuid,
      spendingKeyGenerator,
      authKeyGenerator,
      appPrivateKeyDao
    )
}
