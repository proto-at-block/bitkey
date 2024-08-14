// We expose all member fields for access in integration tests.
@file:Suppress("MemberVisibilityCanBePrivate")

package build.wallet.di

import build.wallet.amount.*
import build.wallet.auth.*
import build.wallet.availability.AppFunctionalityStatusProviderImpl
import build.wallet.bitcoin.address.BitcoinAddressParserImpl
import build.wallet.bitcoin.blockchain.BitcoinBlockchainImpl
import build.wallet.bitcoin.explorer.BitcoinExplorerImpl
import build.wallet.bitcoin.fees.BitcoinTransactionBaseCalculatorImpl
import build.wallet.bitcoin.fees.BitcoinTransactionFeeEstimatorImpl
import build.wallet.bitcoin.invoice.BitcoinInvoiceUrlEncoderImpl
import build.wallet.bitcoin.invoice.PaymentDataParserImpl
import build.wallet.bitcoin.lightning.LightningInvoiceParser
import build.wallet.bitcoin.sync.ElectrumConfigServiceImpl
import build.wallet.bitcoin.transactions.BitcoinTransactionBumpabilityCheckerImpl
import build.wallet.bitcoin.transactions.BitcoinTransactionSweepCheckerImpl
import build.wallet.bitcoin.transactions.OutgoingTransactionDetailRepositoryImpl
import build.wallet.bitcoin.transactions.TransactionPriorityPreferenceImpl
import build.wallet.cloud.backup.*
import build.wallet.cloud.backup.csek.CsekDaoImpl
import build.wallet.cloud.backup.csek.CsekGeneratorImpl
import build.wallet.cloud.backup.health.CloudBackupHealthRepositoryImpl
import build.wallet.cloud.backup.health.FullAccountCloudBackupRepairerImpl
import build.wallet.cloud.backup.local.CloudBackupDaoImpl
import build.wallet.cloud.backup.v2.CloudBackupV2RestorerImpl
import build.wallet.cloud.backup.v2.FullAccountFieldsCreatorImpl
import build.wallet.cloud.store.CloudFileStore
import build.wallet.cloud.store.CloudKeyValueStore
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.coachmark.CoachmarkDaoImpl
import build.wallet.coachmark.CoachmarkServiceImpl
import build.wallet.coachmark.CoachmarkVisibilityDecider
import build.wallet.crypto.Spake2
import build.wallet.datadog.DatadogRumMonitor
import build.wallet.email.EmailValidatorImpl
import build.wallet.emergencyaccesskit.*
import build.wallet.encrypt.*
import build.wallet.f8e.ActiveF8eEnvironmentRepositoryImpl
import build.wallet.f8e.configuration.GetBdkConfigurationF8eClientImpl
import build.wallet.f8e.demo.DemoModeF8eClientImpl
import build.wallet.f8e.mobilepay.MobilePayBalanceF8eClientImpl
import build.wallet.f8e.mobilepay.MobilePaySigningF8eClientImpl
import build.wallet.f8e.mobilepay.MobilePaySpendingLimitF8eClientImpl
import build.wallet.f8e.money.FiatCurrencyDefinitionF8eClientImpl
import build.wallet.f8e.onboarding.*
import build.wallet.f8e.onboarding.frost.ActivateSpendingDescriptorF8eClientImpl
import build.wallet.f8e.onboarding.frost.ContinueDistributedKeygenF8eClientImpl
import build.wallet.f8e.onboarding.frost.InitiateDistributedKeygenF8eClientImpl
import build.wallet.f8e.partnerships.*
import build.wallet.f8e.recovery.*
import build.wallet.f8e.socrec.SocRecF8eClientFake
import build.wallet.f8e.socrec.SocRecF8eClientImpl
import build.wallet.f8e.support.SupportTicketF8eClientImpl
import build.wallet.home.GettingStartedTaskDaoImpl
import build.wallet.home.HomeUiBottomSheetDaoImpl
import build.wallet.inappsecurity.HideBalancePreferenceImpl
import build.wallet.inappsecurity.MoneyHomeHiddenStatusProviderImpl
import build.wallet.keybox.AppDataDeleterImpl
import build.wallet.keybox.CloudBackupDeleterImpl
import build.wallet.limit.MobilePayServiceImpl
import build.wallet.limit.MobilePaySpendingPolicyImpl
import build.wallet.limit.MobilePayStatusRepositoryImpl
import build.wallet.limit.SpendingLimitDaoImpl
import build.wallet.logging.log
import build.wallet.money.currency.FiatCurrencyRepositoryImpl
import build.wallet.money.exchange.CurrencyConverterImpl
import build.wallet.money.exchange.ExchangeRateDaoImpl
import build.wallet.money.exchange.ExchangeRateSyncerImpl
import build.wallet.money.formatter.internal.MoneyDisplayFormatterImpl
import build.wallet.money.formatter.internal.MoneyFormatterDefinitionsImpl
import build.wallet.money.input.MoneyInputFormatterImpl
import build.wallet.nfc.FakeHardwareKeyStore
import build.wallet.nfc.NfcReaderCapabilityProviderImpl
import build.wallet.nfc.NfcTransactorImpl
import build.wallet.nfc.interceptors.*
import build.wallet.nfc.platform.NfcCommandsProvider
import build.wallet.nfc.platform.NfcSessionProvider
import build.wallet.nfc.transaction.PairingTransactionProviderImpl
import build.wallet.onboarding.*
import build.wallet.partnerships.PartnershipTransactionsDaoImpl
import build.wallet.partnerships.PartnershipTransactionsRepositoryImpl
import build.wallet.phonenumber.PhoneNumberFormatterImpl
import build.wallet.platform.biometrics.BiometricPrompter
import build.wallet.platform.biometrics.BiometricTextProviderImpl
import build.wallet.platform.clipboard.ClipboardImpl
import build.wallet.platform.links.DeepLinkHandlerImpl
import build.wallet.platform.pdf.PdfAnnotatorFactory
import build.wallet.platform.settings.LocaleIdentifierProviderImpl
import build.wallet.platform.settings.SystemSettingsLauncher
import build.wallet.platform.settings.TelephonyCountryCodeProviderImpl
import build.wallet.platform.sharing.SharingManager
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.pricechart.ChartDataFetcherServiceImpl
import build.wallet.recovery.*
import build.wallet.recovery.socrec.*
import build.wallet.recovery.sweep.SweepGeneratorImpl
import build.wallet.recovery.sweep.SweepPromptRequirementCheckImpl
import build.wallet.recovery.sweep.SweepServiceImpl
import build.wallet.serialization.Base32Encoding
import build.wallet.statemachine.account.ChooseAccountAccessUiStateMachineImpl
import build.wallet.statemachine.account.create.CreateSoftwareWalletUiStateMachineImpl
import build.wallet.statemachine.account.create.full.CreateAccountUiStateMachineImpl
import build.wallet.statemachine.account.create.full.OnboardKeyboxUiStateMachineImpl
import build.wallet.statemachine.account.create.full.OverwriteFullAccountCloudBackupUiStateMachineImpl
import build.wallet.statemachine.account.create.full.ReplaceWithLiteAccountRestoreUiStateMachineImpl
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachineImpl
import build.wallet.statemachine.account.create.full.keybox.create.CreateKeyboxUiStateMachineImpl
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachineV2Impl
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryChannelsSetupPushItemModelProviderImpl
import build.wallet.statemachine.account.create.full.onboard.notifications.UiErrorHintsProviderImpl
import build.wallet.statemachine.account.create.lite.CreateLiteAccountUiStateMachineImpl
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachineImpl
import build.wallet.statemachine.auth.RefreshAuthTokensUiStateMachineImpl
import build.wallet.statemachine.biometric.BiometricPromptUiStateMachineImpl
import build.wallet.statemachine.biometric.BiometricSettingUiStateMachineImpl
import build.wallet.statemachine.cloud.CloudBackupRectificationNavigatorImpl
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupUiStateMachineImpl
import build.wallet.statemachine.cloud.LiteAccountCloudSignInAndBackupUiStateMachineImpl
import build.wallet.statemachine.cloud.RectifiableErrorHandlingUiStateMachineImpl
import build.wallet.statemachine.cloud.health.CloudBackupHealthDashboardUiStateMachineImpl
import build.wallet.statemachine.cloud.health.RepairCloudBackupStateMachineImpl
import build.wallet.statemachine.core.input.EmailInputUiStateMachineImpl
import build.wallet.statemachine.core.input.PhoneNumberInputUiStateMachineImpl
import build.wallet.statemachine.core.input.VerificationCodeInputStateMachineImpl
import build.wallet.statemachine.data.account.create.CreateFullAccountDataStateMachineImpl
import build.wallet.statemachine.data.account.create.activate.ActivateFullAccountDataStateMachineImpl
import build.wallet.statemachine.data.account.create.keybox.CreateKeyboxDataStateMachineImpl
import build.wallet.statemachine.data.account.create.onboard.OnboardKeyboxDataStateMachineImpl
import build.wallet.statemachine.data.app.AppDataStateMachineImpl
import build.wallet.statemachine.data.keybox.*
import build.wallet.statemachine.data.keybox.transactions.FullAccountTransactionsDataStateMachineImpl
import build.wallet.statemachine.data.mobilepay.MobilePayDataStateMachineImpl
import build.wallet.statemachine.data.recovery.conflict.NoLongerRecoveringDataStateMachineImpl
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringDataStateMachineImpl
import build.wallet.statemachine.data.recovery.inprogress.F8eSpendingKeyRotatorImpl
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachineImpl
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryDataStateMachineImpl
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryHaveNotStartedDataStateMachineImpl
import build.wallet.statemachine.data.recovery.lostapp.initiate.InitiatingLostAppRecoveryDataStateMachineImpl
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryDataStateMachineImpl
import build.wallet.statemachine.data.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryDataStateMachineImpl
import build.wallet.statemachine.data.recovery.sweep.SweepDataStateMachineImpl
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationDataStateMachineImpl
import build.wallet.statemachine.demo.DemoModeCodeEntryUiStateMachineImpl
import build.wallet.statemachine.demo.DemoModeConfigUiStateMachineImpl
import build.wallet.statemachine.dev.*
import build.wallet.statemachine.dev.analytics.AnalyticsOptionsUiStateMachineImpl
import build.wallet.statemachine.dev.analytics.AnalyticsUiStateMachineImpl
import build.wallet.statemachine.dev.cloud.CloudDevOptionsStateMachine
import build.wallet.statemachine.dev.debug.NetworkingDebugConfigPickerUiStateMachineImpl
import build.wallet.statemachine.dev.featureFlags.*
import build.wallet.statemachine.dev.logs.LogsUiStateMachineImpl
import build.wallet.statemachine.fwup.FwupNfcSessionUiStateMachineImpl
import build.wallet.statemachine.fwup.FwupNfcUiStateMachineImpl
import build.wallet.statemachine.home.full.HomeUiStateMachineImpl
import build.wallet.statemachine.home.full.bottomsheet.CurrencyChangeMobilePayBottomSheetUpdaterImpl
import build.wallet.statemachine.home.full.bottomsheet.HomeUiBottomSheetStateMachineImpl
import build.wallet.statemachine.home.lite.LiteHomeUiStateMachineImpl
import build.wallet.statemachine.limit.SetSpendingLimitUiStateMachineImpl
import build.wallet.statemachine.limit.picker.SpendingLimitPickerUiStateMachineImpl
import build.wallet.statemachine.money.amount.MoneyAmountEntryUiStateMachineImpl
import build.wallet.statemachine.money.amount.MoneyAmountUiStateMachineImpl
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiStateMachineImpl
import build.wallet.statemachine.money.currency.CurrencyPreferenceUiStateMachineImpl
import build.wallet.statemachine.moneyhome.card.MoneyHomeCardsUiStateMachineImpl
import build.wallet.statemachine.moneyhome.card.backup.CloudBackupHealthCardUiStateMachineImpl
import build.wallet.statemachine.moneyhome.card.bitcoinprice.BitcoinPriceCardUiStateMachineImpl
import build.wallet.statemachine.moneyhome.card.fwup.DeviceUpdateCardUiStateMachineImpl
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedCardUiStateMachineImpl
import build.wallet.statemachine.moneyhome.card.replacehardware.SetupHardwareCardUiStateMachineImpl
import build.wallet.statemachine.moneyhome.card.sweep.StartSweepCardUiStateMachineImpl
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiStateMachineImpl
import build.wallet.statemachine.moneyhome.full.MoneyHomeViewingBalanceUiStateMachineImpl
import build.wallet.statemachine.moneyhome.lite.LiteMoneyHomeUiStateMachineImpl
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineImpl
import build.wallet.statemachine.notifications.NotificationPreferencesUiStateMachineImpl
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationUiStateMachineImpl
import build.wallet.statemachine.notifications.NotificationsPreferencesCachedProviderImpl
import build.wallet.statemachine.partnerships.AddBitcoinUiStateMachineImpl
import build.wallet.statemachine.partnerships.expected.ExpectedTransactionNoticeUiStateMachineImpl
import build.wallet.statemachine.partnerships.purchase.CustomAmountEntryUiStateMachineImpl
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseUiStateMachineImpl
import build.wallet.statemachine.partnerships.transfer.PartnershipsTransferUiStateMachineImpl
import build.wallet.statemachine.platform.nfc.EnableNfcNavigatorImpl
import build.wallet.statemachine.platform.permissions.EnableNotificationsUiStateMachineImpl
import build.wallet.statemachine.platform.permissions.NotificationPermissionRequesterImpl
import build.wallet.statemachine.platform.permissions.PermissionUiStateMachineImpl
import build.wallet.statemachine.pricechart.BitcoinPriceChartUiStateMachineImpl
import build.wallet.statemachine.receive.AddressQrCodeUiStateMachineImpl
import build.wallet.statemachine.recovery.RecoveryInProgressUiStateMachineImpl
import build.wallet.statemachine.recovery.cloud.*
import build.wallet.statemachine.recovery.conflict.NoLongerRecoveringUiStateMachineImpl
import build.wallet.statemachine.recovery.conflict.SomeoneElseIsRecoveringUiStateMachineImpl
import build.wallet.statemachine.recovery.emergencyaccesskit.EmergencyAccessKitRecoveryUiStateMachineImpl
import build.wallet.statemachine.recovery.hardware.HardwareRecoveryStatusCardUiStateMachineImpl
import build.wallet.statemachine.recovery.inprogress.completing.CompletingRecoveryUiStateMachineImpl
import build.wallet.statemachine.recovery.lostapp.LostAppRecoveryHaveNotStartedUiStateMachineImpl
import build.wallet.statemachine.recovery.lostapp.LostAppRecoveryUiStateMachineImpl
import build.wallet.statemachine.recovery.lostapp.initiate.InitiatingLostAppRecoveryUiStateMachineImpl
import build.wallet.statemachine.recovery.losthardware.LostHardwareRecoveryUiStateMachineImpl
import build.wallet.statemachine.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryUiStateMachineImpl
import build.wallet.statemachine.recovery.socrec.LiteTrustedContactManagementUiStateMachineImpl
import build.wallet.statemachine.recovery.socrec.RecoveryContactCardsUiStateMachineImpl
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementUiStateMachineImpl
import build.wallet.statemachine.recovery.socrec.add.AddingTrustedContactUiStateMachineImpl
import build.wallet.statemachine.recovery.socrec.challenge.ChallengeCodeFormatterImpl
import build.wallet.statemachine.recovery.socrec.challenge.RecoveryChallengeUiStateMachineImpl
import build.wallet.statemachine.recovery.socrec.help.HelpingWithRecoveryUiStateMachineImpl
import build.wallet.statemachine.recovery.socrec.inviteflow.InviteTrustedContactFlowUiStateMachineImpl
import build.wallet.statemachine.recovery.socrec.list.full.ListingTrustedContactsUiStateMachineImpl
import build.wallet.statemachine.recovery.socrec.list.lite.LiteListingTrustedContactsUiStateMachineImpl
import build.wallet.statemachine.recovery.socrec.reinvite.ReinviteTrustedContactUiStateMachineImpl
import build.wallet.statemachine.recovery.socrec.remove.RemoveTrustedContactUiStateMachineImpl
import build.wallet.statemachine.recovery.socrec.view.ViewingInvitationUiStateMachineImpl
import build.wallet.statemachine.recovery.socrec.view.ViewingProtectedCustomerUiStateMachineImpl
import build.wallet.statemachine.recovery.socrec.view.ViewingRecoveryContactUiStateMachineImpl
import build.wallet.statemachine.recovery.sweep.SweepUiStateMachineImpl
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiStateMachineImpl
import build.wallet.statemachine.root.AppUiStateMachineImpl
import build.wallet.statemachine.send.*
import build.wallet.statemachine.send.fee.FeeOptionListUiStateMachineImpl
import build.wallet.statemachine.send.fee.FeeOptionUiStateMachineImpl
import build.wallet.statemachine.send.fee.FeeSelectionUiStateMachineImpl
import build.wallet.statemachine.settings.SettingsListUiStateMachineImpl
import build.wallet.statemachine.settings.full.SettingsHomeUiStateMachineImpl
import build.wallet.statemachine.settings.full.device.DeviceSettingsUiStateMachineImpl
import build.wallet.statemachine.settings.full.device.fingerprints.EditingFingerprintUiStateMachineImpl
import build.wallet.statemachine.settings.full.device.fingerprints.EnrollingFingerprintUiStateMachineImpl
import build.wallet.statemachine.settings.full.device.fingerprints.FingerprintNfcCommandsImpl
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsUiStateMachineImpl
import build.wallet.statemachine.settings.full.device.resetdevice.ResettingDeviceUiStateMachineImpl
import build.wallet.statemachine.settings.full.device.resetdevice.complete.ResettingDeviceSuccessUiStateMachineImpl
import build.wallet.statemachine.settings.full.device.resetdevice.confirmation.ResettingDeviceConfirmationUiStateMachineImpl
import build.wallet.statemachine.settings.full.device.resetdevice.intro.ResettingDeviceIntroUiStateMachineImpl
import build.wallet.statemachine.settings.full.device.resetdevice.processing.ResettingDeviceProgressUiStateMachineImpl
import build.wallet.statemachine.settings.full.electrum.CustomElectrumServerSettingUiStateMachineImpl
import build.wallet.statemachine.settings.full.electrum.CustomElectrumServerUiStateMachineImpl
import build.wallet.statemachine.settings.full.electrum.SetElectrumServerUiStateMachineImpl
import build.wallet.statemachine.settings.full.feedback.FeedbackFormUiStateMachineImpl
import build.wallet.statemachine.settings.full.feedback.FeedbackUiStateMachineImpl
import build.wallet.statemachine.settings.full.mobilepay.MobilePaySettingsUiStateMachineImpl
import build.wallet.statemachine.settings.full.mobilepay.MobilePayStatusUiStateMachineImpl
import build.wallet.statemachine.settings.full.mobilepay.SpendingLimitCardUiStateMachineImpl
import build.wallet.statemachine.settings.full.notifications.RecoveryChannelSettingsUiStateMachineImpl
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiStateMachineImpl
import build.wallet.statemachine.settings.lite.LiteSettingsHomeUiStateMachineImpl
import build.wallet.statemachine.start.GettingStartedRoutingStateMachineImpl
import build.wallet.statemachine.status.AppFunctionalityStatusUiStateMachineImpl
import build.wallet.statemachine.status.HomeStatusBannerUiStateMachineImpl
import build.wallet.statemachine.transactions.FeeBumpConfirmationUiStateMachineImpl
import build.wallet.statemachine.transactions.TransactionItemUiStateMachineImpl
import build.wallet.statemachine.transactions.TransactionListUiStateMachineImpl
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiStateMachineImpl
import build.wallet.support.SupportTicketFormValidatorImpl
import build.wallet.support.SupportTicketRepositoryImpl
import build.wallet.time.*
import kotlinx.datetime.Clock

/**
 * [ActivityComponent] that provides real implementations.
 *
 * Should be initialized as a singleton and scoped to the application's lifecycle.
 */
@Suppress("LargeClass")
class ActivityComponentImpl(
  val appComponent: AppComponent,
  val cloudKeyValueStore: CloudKeyValueStore,
  val cloudFileStore: CloudFileStore,
  cloudSignInUiStateMachine: CloudSignInUiStateMachine,
  cloudDevOptionsStateMachine: CloudDevOptionsStateMachine,
  val cloudStoreAccountRepository: CloudStoreAccountRepository,
  datadogRumMonitor: DatadogRumMonitor,
  symmetricKeyEncryptor: SymmetricKeyEncryptor,
  symmetricKeyGenerator: SymmetricKeyGenerator,
  lightningInvoiceParser: LightningInvoiceParser,
  sharingManager: SharingManager,
  systemSettingsLauncher: SystemSettingsLauncher,
  inAppBrowserNavigator: InAppBrowserNavigator,
  nfcCommandsProvider: NfcCommandsProvider,
  nfcSessionProvider: NfcSessionProvider,
  xChaCha20Poly1305: XChaCha20Poly1305,
  xNonceGenerator: XNonceGenerator,
  spake2: Spake2,
  cryptoBox: CryptoBox,
  val pdfAnnotatorFactory: PdfAnnotatorFactory,
  biometricPrompter: BiometricPrompter,
  fakeHardwareKeyStore: FakeHardwareKeyStore,
) : ActivityComponent {
  init {
    log { "App Variant: ${appComponent.appVariant}" }
  }

  val timeZoneFormatter = TimeZoneFormatterImpl()

  val timeZoneProvider = TimeZoneProviderImpl()

  val dateTimeFormatter = DateTimeFormatterImpl()

  val clipboard = ClipboardImpl(appComponent.platformContext)

  val localeIdentifierProvider =
    LocaleIdentifierProviderImpl(
      platformContext = appComponent.platformContext
    )

  val doubleFormatter =
    DoubleFormatterImpl(
      localeIdentifierProvider = localeIdentifierProvider
    )

  val moneyFormatterDefinitions =
    MoneyFormatterDefinitionsImpl(
      doubleFormatter = doubleFormatter
    )

  val moneyDisplayFormatter =
    MoneyDisplayFormatterImpl(
      bitcoinDisplayPreferenceRepository = appComponent.bitcoinDisplayPreferenceRepository,
      moneyFormatterDefinitions = moneyFormatterDefinitions
    )

  val decimalSeparatorProvider =
    DecimalSeparatorProviderImpl(
      localeIdentifierProvider = localeIdentifierProvider
    )

  val decimalNumberCreator =
    DecimalNumberCreatorImpl(
      decimalSeparatorProvider = decimalSeparatorProvider,
      doubleFormatter = doubleFormatter
    )

  val decimalNumberCalculator =
    DecimalNumberCalculatorImpl(
      decimalNumberCreator = decimalNumberCreator,
      decimalSeparatorProvider = decimalSeparatorProvider,
      doubleFormatter = doubleFormatter
    )

  val wholeNumberCalculator = WholeNumberCalculatorImpl()

  val csekDao =
    CsekDaoImpl(
      encryptedKeyValueStoreFactory = appComponent.secureStoreFactory
    )

  val nfcReaderCapabilityProvider =
    NfcReaderCapabilityProviderImpl(appComponent.platformContext)

  val permissionStateMachine = PermissionUiStateMachineImpl()

  val bitcoinAddressParser = BitcoinAddressParserImpl(appComponent.bdkAddressBuilder)

  val bitcoinExplorer = BitcoinExplorerImpl()

  val extendedKeyGenerator = appComponent.extendedKeyGenerator
  val appKeysGenerator = appComponent.appKeysGenerator

  val cancelDelayNotifyRecoveryF8eClient =
    CancelDelayNotifyRecoveryF8eClientImpl(appComponent.f8eHttpClient)

  val initiateAccountDelayNotifyF8eClient =
    InitiateAccountDelayNotifyF8eClientImpl(appComponent.f8eHttpClient)

  val createAccountF8eClient = CreateAccountF8eClientImpl(appComponent.f8eHttpClient)

  val createAccountKeysetF8eClient = CreateAccountKeysetF8eClientImpl(appComponent.f8eHttpClient)

  val setActiveSpendingKeysetF8eClient =
    SetActiveSpendingKeysetF8eClientImpl(appComponent.f8eHttpClient)

  val bitcoinInvoiceUrlEncoder =
    BitcoinInvoiceUrlEncoderImpl(
      bitcoinAddressParser = bitcoinAddressParser,
      lightningInvoiceParser = lightningInvoiceParser
    )

  val amountCalculator =
    AmountCalculatorImpl(
      decimalNumberCalculator = decimalNumberCalculator,
      wholeNumberCalculator = wholeNumberCalculator
    )

  val gettingStartedTaskDao =
    GettingStartedTaskDaoImpl(
      databaseProvider = appComponent.bitkeyDatabaseProvider
    )

  val cloudBackupDao =
    CloudBackupDaoImpl(
      encryptedKeyValueStoreFactory = appComponent.secureStoreFactory
    )

  val cloudBackupRepository =
    CloudBackupRepositoryImpl(
      cloudKeyValueStore = cloudKeyValueStore,
      cloudBackupDao = cloudBackupDao,
      authTokensRepository = appComponent.authTokensRepository
    )

  val socRecCrypto =
    SocRecCryptoImpl(
      symmetricKeyGenerator = symmetricKeyGenerator,
      xChaCha20Poly1305 = xChaCha20Poly1305,
      xNonceGenerator = xNonceGenerator,
      spake2 = spake2,
      appAuthKeyMessageSigner = appComponent.appAuthKeyMessageSigner,
      signatureVerifier = appComponent.signatureVerifier,
      cryptoBox = cryptoBox
    )

  val socRecCryptoFake = SocRecCryptoFake(
    messageSigner = appComponent.messageSigner,
    signatureVerifier = appComponent.signatureVerifier,
    appPrivateKeyDao = appComponent.appPrivateKeyDao
  )

  val socRecKeysDao =
    SocRecKeysDaoImpl(
      databaseProvider = appComponent.bitkeyDatabaseProvider,
      appPrivateKeyDao = appComponent.appPrivateKeyDao
    )

  val socRecKeysRepository = SocRecKeysRepository(socRecCrypto, socRecKeysDao)

  private val fullAccountFieldsCreator =
    FullAccountFieldsCreatorImpl(
      appPrivateKeyDao = appComponent.appPrivateKeyDao,
      csekDao = csekDao,
      symmetricKeyEncryptor = symmetricKeyEncryptor,
      socRecCrypto = socRecCrypto
    )

  val exchangeRateDao =
    ExchangeRateDaoImpl(
      databaseProvider = appComponent.bitkeyDatabaseProvider
    )

  val activeF8eEnvironmentRepository =
    ActiveF8eEnvironmentRepositoryImpl(
      keyboxDao = appComponent.keyboxDao
    )

  val exchangeRateSyncer =
    ExchangeRateSyncerImpl(
      exchangeRateDao = exchangeRateDao,
      exchangeRateF8eClient = appComponent.exchangeRateF8eClient,
      activeF8eEnvironmentRepository = activeF8eEnvironmentRepository,
      appSessionManager = appComponent.appSessionManager
    )

  val currencyConverter =
    CurrencyConverterImpl(
      accountRepository = appComponent.accountRepository,
      exchangeRateDao = exchangeRateDao,
      exchangeRateF8eClient = appComponent.exchangeRateF8eClient
    )

  val durationFormatter = DurationFormatterImpl()

  val getDelayNotifyRecoveryStatusF8eClient =
    GetDelayNotifyRecoveryStatusF8eClientImpl(
      f8eHttpClient = appComponent.f8eHttpClient
    )

  val recoverySyncer =
    RecoverySyncerImpl(
      recoveryDao = appComponent.recoveryDao,
      getRecoveryStatusF8eClient = getDelayNotifyRecoveryStatusF8eClient,
      appSessionManager = appComponent.appSessionManager
    )

  val delayNotifyLostAppRecoveryInitiator =
    LostAppRecoveryInitiatorImpl(
      initiateAccountDelayNotifyF8eClient =
        InitiateAccountDelayNotifyF8eClientImpl(
          f8eHttpClient = appComponent.f8eHttpClient
        ),
      recoveryDao = appComponent.recoveryDao
    )

  val delayNotifyLostAppRecoveryAuthenticator =
    LostAppRecoveryAuthenticatorImpl(
      accountAuthenticator = appComponent.accountAuthenticator,
      authTokenDao = appComponent.authTokenDao,
      deviceTokenManager = appComponent.deviceTokenManager
    )

  val enableNfcNavigator = EnableNfcNavigatorImpl()

  val telephonyCountryCodeProvider = TelephonyCountryCodeProviderImpl(appComponent.platformContext)

  override val nfcTransactor =
    NfcTransactorImpl(
      commandsProvider = nfcCommandsProvider,
      sessionProvider = nfcSessionProvider,
      interceptors =
        listOf(
          retryCommands(),
          iosMessages(),
          collectFirmwareTelemetry(
            appComponent.firmwareDeviceInfoDao,
            appComponent.firmwareTelemetryUploader
          ),
          lockDevice(),
          haptics(appComponent.nfcHaptics),
          timeoutSession(),
          collectMetrics(
            appComponent.datadogRumMonitor, appComponent.datadogTracer,
            appComponent.eventTracker
          ),
          sessionLogger()
        )
    )

  val nfcSessionUIStateMachine =
    NfcSessionUIStateMachineImpl(
      delayer = appComponent.delayer,
      nfcReaderCapabilityProvider = nfcReaderCapabilityProvider,
      enableNfcNavigator = enableNfcNavigator,
      deviceInfoProvider = appComponent.deviceInfoProvider,
      nfcTransactor = nfcTransactor
    )

  val completeDelayNotifyF8eClient = CompleteDelayNotifyF8eClientImpl(appComponent.f8eHttpClient)

  val csekGenerator =
    CsekGeneratorImpl(
      symmetricKeyGenerator = symmetricKeyGenerator
    )

  val bitcoinBlockchain =
    BitcoinBlockchainImpl(
      bdkBlockchainProvider = appComponent.bdkBlockchainProvider,
      bdkPsbtBuilder = appComponent.bdkPartiallySignedTransactionBuilder,
      clock = appComponent.clock
    )

  val mobilePaySigningF8eClient = MobilePaySigningF8eClientImpl(appComponent.f8eHttpClient)

  val paymentDataParser =
    PaymentDataParserImpl(
      bip21InvoiceEncoder = bitcoinInvoiceUrlEncoder,
      bitcoinAddressParser = bitcoinAddressParser,
      lightningInvoiceParser = lightningInvoiceParser
    )

  val bitcoinQrCodeScanStateMachine =
    BitcoinQrCodeScanUiStateMachineImpl(
      paymentDataParser = paymentDataParser
    )

  val bitcoinAddressRecipientStateMachine =
    BitcoinAddressRecipientUiStateMachineImpl(
      paymentDataParser = paymentDataParser,
      keysetWalletProvider = appComponent.keysetWalletProvider
    )

  val cloudBackupRectificationNavigator = CloudBackupRectificationNavigatorImpl()

  val rectifiableErrorHandlingUiStateMachine =
    RectifiableErrorHandlingUiStateMachineImpl(
      cloudBackupRectificationNavigator = cloudBackupRectificationNavigator
    )

  val emergencyAccessKitRepository = EmergencyAccessKitRepositoryImpl(cloudFileStore)

  val emergencyAccessKitDataProvider = EmergencyAccessKitDataProviderImpl(appComponent.appVariant)

  val eakPayloadCreator = EmergencyAccessPayloadCreatorImpl(
    csekDao = csekDao,
    symmetricKeyEncryptor = symmetricKeyEncryptor,
    appPrivateKeyDao = appComponent.appPrivateKeyDao
  )
  val emergencyAccessKitPdfGenerator = EmergencyAccessKitPdfGeneratorImpl(
    apkParametersProvider = EmergencyAccessKitApkParametersProviderImpl(
      emergencyAccessKitDataProvider
    ),
    mobileKeyParametersProvider = EmergencyAccessKitMobileKeyParametersProviderImpl(
      payloadCreator = eakPayloadCreator
    ),
    pdfAnnotatorFactory = pdfAnnotatorFactory,
    templateProvider = EmergencyAccessKitTemplateProviderImpl(
      platformContext = appComponent.platformContext
    ),
    backupDateProvider = EmergencyAccessKitBackupDateProviderImpl(
      clock = appComponent.clock,
      timeZoneProvider = timeZoneProvider
    ),
    dateTimeFormatter = dateTimeFormatter
  )

  val cloudBackupRepairer = FullAccountCloudBackupRepairerImpl(
    cloudBackupRepository = cloudBackupRepository,
    cloudBackupDao = cloudBackupDao,
    emergencyAccessKitPdfGenerator = emergencyAccessKitPdfGenerator,
    emergencyAccessKitRepository = emergencyAccessKitRepository
  )

  val cloudBackupHealthRepository =
    CloudBackupHealthRepositoryImpl(
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      cloudBackupRepository = cloudBackupRepository,
      emergencyAccessKitRepository = emergencyAccessKitRepository,
      fullAccountCloudBackupRepairer = cloudBackupRepairer,
      cloudBackupDao = cloudBackupDao,
      appSessionManager = appComponent.appSessionManager
    )

  val socRecRelationshipsDao = SocRecRelationshipsDaoImpl(appComponent.bitkeyDatabaseProvider)

  val socRecEnrollmentAuthenticationDao = SocRecEnrollmentAuthenticationDaoImpl(
    appComponent.appPrivateKeyDao,
    appComponent.bitkeyDatabaseProvider
  )

  val base32Encoding = Base32Encoding()

  val pakeCodeBuilder = SocialRecoveryCodeBuilderImpl(
    base32Encoding = base32Encoding
  )

  val socialRecoveryF8eClientFake =
    SocRecF8eClientFake(
      uuidGenerator = appComponent.uuidGenerator,
      backgroundScope = appComponent.appCoroutineScope
    )

  val socialRecoveryF8eClientImpl =
    SocRecF8eClientImpl(
      f8eHttpClient = appComponent.f8eHttpClient
    )

  val socialRecoveryF8eClientProvider = SocRecF8eClientProviderImpl(
    accountRepository = appComponent.accountRepository,
    socRecFake = socialRecoveryF8eClientFake,
    socRecF8eClient = socialRecoveryF8eClientImpl,
    debugOptionsService = appComponent.debugOptionsService
  )

  val socRecStartedChallengeAuthenticationDao = SocRecStartedChallengeAuthenticationDaoImpl(
    appComponent.appPrivateKeyDao,
    appComponent.bitkeyDatabaseProvider
  )

  val socRecRelationshipsRepository =
    SocRecRelationshipsRepositoryImpl(
      appScope = appComponent.appCoroutineScope,
      socRecF8eClientProvider = socialRecoveryF8eClientProvider,
      socRecRelationshipsDao = socRecRelationshipsDao,
      socRecEnrollmentAuthenticationDao = socRecEnrollmentAuthenticationDao,
      socRecCrypto = socRecCrypto,
      socialRecoveryCodeBuilder = pakeCodeBuilder,
      appSessionManager = appComponent.appSessionManager
    )

  val fullAccountCloudBackupCreator =
    FullAccountCloudBackupCreatorImpl(
      appPrivateKeyDao = appComponent.appPrivateKeyDao,
      fullAccountFieldsCreator = fullAccountFieldsCreator,
      socRecKeysRepository = socRecKeysRepository,
      socRecRelationshipsRepository = socRecRelationshipsRepository
    )

  val fullAccountCloudSignInAndBackupUiStateMachine =
    FullAccountCloudSignInAndBackupUiStateMachineImpl(
      cloudBackupRepository = cloudBackupRepository,
      cloudSignInUiStateMachine = cloudSignInUiStateMachine,
      fullAccountCloudBackupCreator = fullAccountCloudBackupCreator,
      eventTracker = appComponent.eventTracker,
      rectifiableErrorHandlingUiStateMachine = rectifiableErrorHandlingUiStateMachine,
      deviceInfoProvider = appComponent.deviceInfoProvider,
      csekGenerator = csekGenerator,
      nfcSessionUIStateMachine = nfcSessionUIStateMachine,
      csekDao = csekDao,
      inAppBrowserNavigator = inAppBrowserNavigator,
      emergencyAccessKitPdfGenerator = emergencyAccessKitPdfGenerator,
      emergencyAccessKitRepository = emergencyAccessKitRepository
    )

  val bestEffortFullAccountCloudBackupUploader =
    BestEffortFullAccountCloudBackupUploaderImpl(
      cloudBackupDao = cloudBackupDao,
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      fullAccountCloudBackupCreator = fullAccountCloudBackupCreator,
      cloudBackupRepository = cloudBackupRepository
    )

  val repairMobileKeyBackupUiStateMachine = RepairCloudBackupStateMachineImpl(
    cloudSignInStateMachine = cloudSignInUiStateMachine,
    cloudBackupDao = cloudBackupDao,
    cloudBackupRepository = cloudBackupRepository,
    cloudBackupHealthRepository = cloudBackupHealthRepository,
    deviceInfoProvider = appComponent.deviceInfoProvider,
    csekGenerator = csekGenerator,
    nfcSessionUIStateMachine = nfcSessionUIStateMachine,
    csekDao = csekDao,
    fullAccountCloudBackupCreator = fullAccountCloudBackupCreator,
    emergencyAccessKitPdfGenerator = emergencyAccessKitPdfGenerator,
    emergencyAccessKitRepository = emergencyAccessKitRepository,
    inAppBrowserNavigator = inAppBrowserNavigator
  )

  val hardwareRecoveryStatusCardUiStateMachine =
    HardwareRecoveryStatusCardUiStateMachineImpl(
      clock = appComponent.clock,
      durationFormatter = durationFormatter
    )

  val transactionListStateMachine =
    TransactionListUiStateMachineImpl(
      transactionItemUiStateMachine =
        TransactionItemUiStateMachineImpl(
          currencyConverter = currencyConverter,
          dateTimeFormatter = dateTimeFormatter,
          timeZoneProvider = timeZoneProvider,
          moneyDisplayFormatter = moneyDisplayFormatter
        )
    )

  val spendingLimitDao =
    SpendingLimitDaoImpl(
      databaseProvider = appComponent.bitkeyDatabaseProvider
    )

  val spendingLimitF8eClient =
    MobilePaySpendingLimitF8eClientImpl(
      f8eHttpClient = appComponent.f8eHttpClient
    )

  val mobilePayBalanceF8eClient =
    MobilePayBalanceF8eClientImpl(
      f8eHttpClient = appComponent.f8eHttpClient,
      fiatCurrencyDao = appComponent.fiatCurrencyDao
    )

  val mobilePayStatusProvider =
    MobilePayStatusRepositoryImpl(
      spendingLimitDao = spendingLimitDao,
      mobilePayBalanceF8eClient = mobilePayBalanceF8eClient,
      uuidGenerator = appComponent.uuidGenerator,
      appSpendingWalletProvider = appComponent.appSpendingWalletProvider
    )

  val mobilePayService = MobilePayServiceImpl(
    eventTracker = appComponent.eventTracker,
    spendingLimitDao = spendingLimitDao,
    spendingLimitF8eClient = spendingLimitF8eClient,
    mobilePayStatusRepository = mobilePayStatusProvider
  )

  val moneyAmountUiStateMachine =
    MoneyAmountUiStateMachineImpl(
      currencyConverter = currencyConverter,
      moneyDisplayFormatter = moneyDisplayFormatter
    )

  val moneyInputFormatter =
    MoneyInputFormatterImpl(
      decimalSeparatorProvider = decimalSeparatorProvider,
      doubleFormatter = doubleFormatter,
      moneyFormatterDefinitions = moneyFormatterDefinitions
    )

  val moneyAmountEntryUiStateMachine =
    MoneyAmountEntryUiStateMachineImpl(
      moneyInputFormatter = moneyInputFormatter,
      moneyDisplayFormatter = moneyDisplayFormatter
    )

  val moneyCalculatorUiStateMachine = MoneyCalculatorUiStateMachineImpl(
    amountCalculator = amountCalculator,
    bitcoinDisplayPreferenceRepository = appComponent.bitcoinDisplayPreferenceRepository,
    currencyConverter = currencyConverter,
    moneyAmountEntryUiStateMachine = moneyAmountEntryUiStateMachine,
    decimalNumberCreator = decimalNumberCreator,
    doubleFormatter = doubleFormatter
  )

  val mobilePaySpendingPolicy = MobilePaySpendingPolicyImpl()

  val transferAmountEntryUiStateMachine = TransferAmountEntryUiStateMachineImpl(
    currencyConverter = currencyConverter,
    moneyCalculatorUiStateMachine = moneyCalculatorUiStateMachine,
    mobilePaySpendingPolicy = mobilePaySpendingPolicy,
    moneyDisplayFormatter = moneyDisplayFormatter,
    fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository
  )

  val transactionDetailsCardUiStateMachine =
    TransactionDetailsCardUiStateMachineImpl(
      currencyConverter = currencyConverter,
      moneyDisplayFormatter = moneyDisplayFormatter
    )

  val transferInitiatedUiStateMachine = TransferInitiatedUiStateMachineImpl(
    transactionDetailsCardUiStateMachine = transactionDetailsCardUiStateMachine,
    fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository
  )

  val refreshAuthTokensStateMachine =
    RefreshAuthTokensUiStateMachineImpl(
      authTokensRepository = appComponent.authTokensRepository
    )

  val proofOfPossessionNfcStateMachine =
    ProofOfPossessionNfcStateMachineImpl(
      nfcSessionUIStateMachine = nfcSessionUIStateMachine,
      refreshAuthTokensUiStateMachine = refreshAuthTokensStateMachine
    )

  val spendingLimitPickerUiStateMachine =
    SpendingLimitPickerUiStateMachineImpl(
      currencyConverter = currencyConverter,
      mobilePayFiatConfigService = appComponent.mobilePayFiatConfigService,
      moneyDisplayFormatter = moneyDisplayFormatter,
      proofOfPossessionNfcStateMachine = proofOfPossessionNfcStateMachine
    )

  val addressQrCodeUiStateMachine =
    AddressQrCodeUiStateMachineImpl(
      clipboard = clipboard,
      delayer = appComponent.delayer,
      sharingManager = sharingManager,
      bitcoinInvoiceUrlEncoder = bitcoinInvoiceUrlEncoder,
      bitcoinAddressService = appComponent.bitcoinAddressService
    )

  val notificationPermissionRequester =
    NotificationPermissionRequesterImpl(
      pushNotificationPermissionStatusProvider = appComponent.pushNotificationPermissionStatusProvider
    )

  val enableNotificationsUiStateMachine =
    EnableNotificationsUiStateMachineImpl(
      notificationPermissionRequester = notificationPermissionRequester,
      permissionChecker = appComponent.permissionChecker,
      eventTracker = appComponent.eventTracker
    )

  val verificationCodeInputStateMachine =
    VerificationCodeInputStateMachineImpl(
      clock = appComponent.clock,
      durationFormatter = durationFormatter,
      eventTracker = appComponent.eventTracker
    )

  val recoveryNotificationVerificationUiStateMachine =
    RecoveryNotificationVerificationUiStateMachineImpl(
      verificationCodeInputStateMachine = verificationCodeInputStateMachine
    )

  val initiatingLostAppRecoveryUiStateMachineImpl =
    InitiatingLostAppRecoveryUiStateMachineImpl(
      nfcSessionUIStateMachine = nfcSessionUIStateMachine,
      enableNotificationsUiStateMachine = enableNotificationsUiStateMachine,
      recoveryNotificationVerificationUiStateMachine = recoveryNotificationVerificationUiStateMachine
    )

  val accessCloudBackupUiStateMachine =
    AccessCloudBackupUiStateMachineImpl(
      cloudBackupRepository = cloudBackupRepository,
      cloudSignInUiStateMachine = cloudSignInUiStateMachine,
      rectifiableErrorHandlingUiStateMachine = rectifiableErrorHandlingUiStateMachine,
      deviceInfoProvider = appComponent.deviceInfoProvider,
      inAppBrowserNavigator = inAppBrowserNavigator
    )

  private val cloudBackupV2Restorer =
    CloudBackupV2RestorerImpl(
      csekDao = csekDao,
      symmetricKeyEncryptor = symmetricKeyEncryptor,
      appPrivateKeyDao = appComponent.appPrivateKeyDao,
      socRecKeysDao = socRecKeysDao,
      uuidGenerator = appComponent.uuidGenerator
    )

  val cloudBackupRestorer =
    FullAccountCloudBackupRestorerImpl(
      cloudBackupV2Restorer = cloudBackupV2Restorer
    )

  val challengeCodeFormatter = ChallengeCodeFormatterImpl()

  val socRecPendingChallengeDao = SocRecStartedChallengeDaoImpl(appComponent.bitkeyDatabaseProvider)

  val socRecChallengeRepository =
    SocRecChallengeRepositoryImpl(
      socRec = socialRecoveryF8eClientImpl,
      socRecCodeBuilder = pakeCodeBuilder,
      socRecCrypto = socRecCrypto,
      socRecFake = socialRecoveryF8eClientFake,
      socRecStartedChallengeDao = socRecPendingChallengeDao,
      socRecStartedChallengeAuthenticationDao = socRecStartedChallengeAuthenticationDao
    )

  val recoveryChallengeUiStateMachine =
    RecoveryChallengeUiStateMachineImpl(
      crypto = socRecCrypto,
      enableNotificationsUiStateMachine = enableNotificationsUiStateMachine,
      deviceTokenManager = appComponent.deviceTokenManager,
      challengeCodeFormatter = challengeCodeFormatter,
      permissionChecker = appComponent.permissionChecker
    )

  val outgoingTransactionDetailRepository =
    OutgoingTransactionDetailRepositoryImpl(
      outgoingTransactionDetailDao = appComponent.outgoingTransactionDetailDao
    )

  val listKeysetsF8eClient =
    ListKeysetsF8eClientImpl(
      f8eHttpClient = appComponent.f8eHttpClient,
      uuidGenerator = appComponent.uuidGenerator
    )

  val sweepGenerator =
    SweepGeneratorImpl(
      listKeysetsF8eClient = listKeysetsF8eClient,
      bitcoinFeeRateEstimator = appComponent.bitcoinFeeRateEstimator,
      appPrivateKeyDao = appComponent.appPrivateKeyDao,
      keysetWalletProvider = appComponent.keysetWalletProvider,
      registerWatchAddressProcessor = appComponent.registerWatchAddressProcessor
    )

  val sweepService = SweepServiceImpl(
    sweepGenerator = sweepGenerator
  )

  val sweepDataStateMachine =
    SweepDataStateMachineImpl(
      bitcoinBlockchain = bitcoinBlockchain,
      sweepService = sweepService,
      mobilePaySigningF8eClient = mobilePaySigningF8eClient,
      appSpendingWalletProvider = appComponent.appSpendingWalletProvider,
      exchangeRateSyncer = exchangeRateSyncer,
      outgoingTransactionDetailRepository = outgoingTransactionDetailRepository
    )

  val sweepUiStateMachine = SweepUiStateMachineImpl(
    nfcSessionUIStateMachine = nfcSessionUIStateMachine,
    moneyAmountUiStateMachine = moneyAmountUiStateMachine,
    fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository,
    sweepDataStateMachine = sweepDataStateMachine
  )

  val recoveryIncompleteDao = RecoveryIncompleteDaoImpl(appComponent.bitkeyDatabaseProvider)
  val postSocRecTaskRepository = PostSocRecTaskRepositoryImpl(recoveryIncompleteDao)

  val socRecService = SocRecServiceImpl(
    postSocRecTaskRepository = postSocRecTaskRepository
  )

  val completingRecoveryUiStateMachine =
    CompletingRecoveryUiStateMachineImpl(
      proofOfPossessionNfcStateMachine = proofOfPossessionNfcStateMachine,
      fullAccountCloudSignInAndBackupUiStateMachine = fullAccountCloudSignInAndBackupUiStateMachine,
      sweepUiStateMachine = sweepUiStateMachine,
      nfcSessionUIStateMachine = nfcSessionUIStateMachine,
      postSocRecTaskRepository = postSocRecTaskRepository,
      recoverySyncer = recoverySyncer
    )

  val recoveryInProgressUiStateMachine =
    RecoveryInProgressUiStateMachineImpl(
      completingRecoveryUiStateMachine,
      proofOfPossessionNfcStateMachine,
      durationFormatter,
      appComponent.clock,
      eventTracker = appComponent.eventTracker,
      recoveryNotificationVerificationUiStateMachine = recoveryNotificationVerificationUiStateMachine
    )

  val addingTcsUiStateMachine =
    AddingTrustedContactUiStateMachineImpl(
      proofOfPossessionNfcStateMachine = proofOfPossessionNfcStateMachine,
      clipboard = clipboard,
      sharingManager = sharingManager,
      eventTracker = appComponent.eventTracker
    )

  val reinviteTrustedContactUiStateMachine =
    ReinviteTrustedContactUiStateMachineImpl(
      proofOfPossessionNfcStateMachine = proofOfPossessionNfcStateMachine,
      clipboard = clipboard,
      sharingManager = sharingManager
    )

  val removeTrustedContactUiStateMachine =
    RemoveTrustedContactUiStateMachineImpl(
      proofOfPossessionNfcStateMachine = proofOfPossessionNfcStateMachine,
      clock = appComponent.clock
    )

  val inviteCodeLoader = InviteCodeLoaderImpl(
    socRecEnrollmentAuthenticationDao = socRecEnrollmentAuthenticationDao,
    recoveryCodeBuilder = pakeCodeBuilder
  )

  val viewingInvitationUiStateMachine =
    ViewingInvitationUiStateMachineImpl(
      removeTrustedContactsUiStateMachine = removeTrustedContactUiStateMachine,
      reinviteTrustedContactUiStateMachine = reinviteTrustedContactUiStateMachine,
      sharingManager = sharingManager,
      clock = appComponent.clock,
      inviteCodeLoader = inviteCodeLoader
    )

  val viewingRecoveryContactUiStateMachine =
    ViewingRecoveryContactUiStateMachineImpl(
      removeTrustedContactUiStateMachine = removeTrustedContactUiStateMachine
    )

  val viewingProtectedCustomerUiStateMachine = ViewingProtectedCustomerUiStateMachineImpl()

  val helpingWithRecoveryUiStateMachine =
    HelpingWithRecoveryUiStateMachineImpl(
      delayer = appComponent.delayer,
      socialChallengeVerifier =
        SocialChallengeVerifierImpl(
          socRecChallengeRepository = socRecChallengeRepository,
          socRecCrypto = socRecCrypto,
          socialRecoveryCodeBuilder = pakeCodeBuilder
        ),
      socRecKeysRepository = socRecKeysRepository
    )

  val listingTcsUiStateMachine =
    ListingTrustedContactsUiStateMachineImpl(
      viewingRecoveryContactUiStateMachine = viewingRecoveryContactUiStateMachine,
      viewingInvitationUiStateMachine = viewingInvitationUiStateMachine,
      viewingProtectedCustomerUiStateMachine = viewingProtectedCustomerUiStateMachine,
      helpingWithRecoveryUiStateMachine = helpingWithRecoveryUiStateMachine,
      clock = appComponent.clock
    )

  val trustedContactEnrollmentUiStateMachine =
    TrustedContactEnrollmentUiStateMachineImpl(
      deviceInfoProvider = appComponent.deviceInfoProvider,
      socRecKeysRepository = socRecKeysRepository,
      eventTracker = appComponent.eventTracker
    )

  val trustedContactManagementUiStateMachine =
    TrustedContactManagementUiStateMachineImpl(
      addingTrustedContactUiStateMachine = addingTcsUiStateMachine,
      listingTrustedContactsUiStateMachine = listingTcsUiStateMachine,
      trustedContactEnrollmentUiStateMachine = trustedContactEnrollmentUiStateMachine,
      deviceInfoProvider = appComponent.deviceInfoProvider
    )

  val notificationsPreferencesCachedProvider = NotificationsPreferencesCachedProviderImpl(
    notificationTouchpointF8eClient = appComponent.notificationTouchpointF8eClient,
    keyValueStoreFactory = appComponent.keyValueStoreFactory
  )

  val upgradeAccountF8eClient = UpgradeAccountF8eClientImpl(appComponent.f8eHttpClient)

  val fullAccountCreator = FullAccountCreatorImpl(
    keyboxDao = appComponent.keyboxDao,
    createFullAccountF8eClient = createAccountF8eClient,
    accountAuthenticator = appComponent.accountAuthenticator,
    authTokenDao = appComponent.authTokenDao,
    deviceTokenManager = appComponent.deviceTokenManager,
    uuidGenerator = appComponent.uuidGenerator,
    notificationTouchpointF8eClient = appComponent.notificationTouchpointF8eClient,
    notificationTouchpointDao = appComponent.notificationTouchpointDao
  )

  val onboardingKeyboxStepStateDao =
    OnboardingKeyboxStepStateDaoImpl(
      databaseProvider = appComponent.bitkeyDatabaseProvider
    )

  val phoneNumberFormatter = PhoneNumberFormatterImpl(
    phoneNumberLibBindings = appComponent.phoneNumberLibBindings
  )

  val phoneNumberInputStateMachine =
    PhoneNumberInputUiStateMachineImpl(
      phoneNumberFormatter = phoneNumberFormatter,
      phoneNumberValidator = appComponent.phoneNumberValidator
    )

  val emailValidator = EmailValidatorImpl()

  val emailInputStateMachine =
    EmailInputUiStateMachineImpl(
      emailValidator = emailValidator
    )

  val uiErrorHintProvider = UiErrorHintsProviderImpl(
    keystoreFactory = appComponent.secureStoreFactory,
    appScope = appComponent.appCoroutineScope
  )

  val notificationTouchpointInputAndVerificationUiStateMachine =
    NotificationTouchpointInputAndVerificationUiStateMachineImpl(
      delayer = appComponent.delayer,
      emailInputUiStateMachine = emailInputStateMachine,
      notificationTouchpointDao = appComponent.notificationTouchpointDao,
      notificationTouchpointF8eClient = appComponent.notificationTouchpointF8eClient,
      phoneNumberInputUiStateMachine = phoneNumberInputStateMachine,
      proofOfPossessionNfcStateMachine = proofOfPossessionNfcStateMachine,
      uiErrorHintSubmitter = uiErrorHintProvider,
      verificationCodeInputStateMachine = verificationCodeInputStateMachine
    )

  val pushItemModelProviderV2 =
    RecoveryChannelsSetupPushItemModelProviderImpl(
      pushNotificationPermissionStatusProvider = appComponent.pushNotificationPermissionStatusProvider,
      systemSettingsLauncher = systemSettingsLauncher
    )

  val notificationPreferencesUiStateMachine =
    NotificationPreferencesUiStateMachineImpl(
      permissionChecker = appComponent.permissionChecker,
      notificationsPreferencesCachedProvider = notificationsPreferencesCachedProvider,
      systemSettingsLauncher = systemSettingsLauncher,
      notificationPermissionRequester = notificationPermissionRequester,
      inAppBrowserNavigator = inAppBrowserNavigator,
      eventTracker = appComponent.eventTracker
    )

  val recoveryChannelSettingsUiStateMachineImpl =
    RecoveryChannelSettingsUiStateMachineImpl(
      permissionChecker = appComponent.permissionChecker,
      notificationsPreferencesCachedProvider = notificationsPreferencesCachedProvider,
      notificationTouchpointInputAndVerificationUiStateMachine = notificationTouchpointInputAndVerificationUiStateMachine,
      inAppBrowserNavigator = inAppBrowserNavigator,
      telephonyCountryCodeProvider = telephonyCountryCodeProvider,
      proofOfPossessionNfcStateMachine = proofOfPossessionNfcStateMachine,
      systemSettingsLauncher = systemSettingsLauncher,
      eventTracker = appComponent.eventTracker,
      notificationPermissionRequester = notificationPermissionRequester,
      uiErrorHintsProvider = uiErrorHintProvider,
      notificationTouchpointService = appComponent.notificationTouchpointService
    )

  val notificationPreferencesSetupUiStateMachineV2 =
    NotificationPreferencesSetupUiStateMachineV2Impl(
      eventTracker = appComponent.eventTracker,
      notificationPermissionRequester = notificationPermissionRequester,
      notificationTouchpointInputAndVerificationUiStateMachine = notificationTouchpointInputAndVerificationUiStateMachine,
      notificationTouchpointService = appComponent.notificationTouchpointService,
      notificationPreferencesUiStateMachine = notificationPreferencesUiStateMachine,
      onboardingKeyboxStepStateDao = onboardingKeyboxStepStateDao,
      pushItemModelProvider = pushItemModelProviderV2,
      inAppBrowserNavigator = inAppBrowserNavigator,
      telephonyCountryCodeProvider = telephonyCountryCodeProvider,
      uiErrorHintsProvider = uiErrorHintProvider
    )

  val onboardingKeyboxSealedCsekDao =
    OnboardingKeyboxSealedCsekDaoImpl(
      encryptedKeyValueStoreFactory = appComponent.secureStoreFactory
    )

  val onboardingKeyboxHwAuthPublicKeyDao =
    OnboardingKeyboxHardwareKeysDaoImpl(
      databaseProvider = appComponent.bitkeyDatabaseProvider
    )

  val onboardingF8eClient =
    OnboardingF8eClientImpl(
      f8eHttpClient = appComponent.f8eHttpClient
    )

  val pairingTransactionProvider =
    PairingTransactionProviderImpl(
      csekGenerator = csekGenerator,
      csekDao = csekDao,
      uuidGenerator = appComponent.uuidGenerator,
      appInstallationDao = appComponent.appInstallationDao,
      hardwareAttestation = appComponent.hardwareAttestation
    )

  val helpCenterUiStateMachine =
    HelpCenterUiStateMachineImpl(
      inAppBrowserNavigator = inAppBrowserNavigator
    )

  val pairNewHardwareUiStateMachine =
    PairNewHardwareUiStateMachineImpl(
      eventTracker = appComponent.eventTracker,
      pairingTransactionProvider = pairingTransactionProvider,
      nfcSessionUIStateMachine = nfcSessionUIStateMachine,
      helpCenterUiStateMachine = helpCenterUiStateMachine
    )

  private val createKeyboxUiStateMachine =
    CreateKeyboxUiStateMachineImpl(
      pairNewHardwareUiStateMachine = pairNewHardwareUiStateMachine
    )

  val fiatCurrencyDefinitionF8eClient =
    FiatCurrencyDefinitionF8eClientImpl(appComponent.f8eHttpClient)
  val fiatCurrencyRepository =
    FiatCurrencyRepositoryImpl(
      appScope = appComponent.appCoroutineScope,
      fiatCurrencyDao = appComponent.fiatCurrencyDao,
      fiatCurrencyDefinitionF8eClient = fiatCurrencyDefinitionF8eClient
    )

  val hideBalancePreference = HideBalancePreferenceImpl(
    appCoroutineScope = appComponent.appCoroutineScope,
    databaseProvider = appComponent.bitkeyDatabaseProvider,
    eventTracker = appComponent.eventTracker
  )

  val currencyPreferenceUiStateMachine =
    CurrencyPreferenceUiStateMachineImpl(
      bitcoinDisplayPreferenceRepository = appComponent.bitcoinDisplayPreferenceRepository,
      fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository,
      eventTracker = appComponent.eventTracker,
      currencyConverter = currencyConverter,
      fiatCurrencyRepository = fiatCurrencyRepository,
      moneyDisplayFormatter = moneyDisplayFormatter,
      hideBalancePreference = hideBalancePreference,
      bitcoinPriceChartFeatureFlag = appComponent.bitcoinPriceChartFeatureFlag,
      bitcoinPriceCardPreference = appComponent.bitcoinPriceCardPreference
    )

  val onboardKeyboxUiStateMachine =
    OnboardKeyboxUiStateMachineImpl(
      fullAccountCloudSignInAndBackupUiStateMachine = fullAccountCloudSignInAndBackupUiStateMachine,
      notificationPreferencesSetupUiStateMachineV2 = notificationPreferencesSetupUiStateMachineV2
    )

  val deleteOnboardingFullAccountF8eClient =
    DeleteOnboardingFullAccountF8eClientImpl(appComponent.f8eHttpClient)

  val onboardingFullAccountDeleter =
    OnboardingFullAccountDeleterImpl(
      accountRepository = appComponent.accountRepository,
      keyboxDao = appComponent.keyboxDao,
      deleteOnboardingFullAccountF8eClient = deleteOnboardingFullAccountF8eClient
    )

  val liteToFullAccountUpgrader =
    LiteToFullAccountUpgraderImpl(
      keyboxDao = appComponent.keyboxDao,
      accountAuthenticator = appComponent.accountAuthenticator,
      authTokenDao = appComponent.authTokenDao,
      deviceTokenManager = appComponent.deviceTokenManager,
      upgradeAccountF8eClient = upgradeAccountF8eClient,
      uuidGenerator = appComponent.uuidGenerator
    )

  val liteAccountCloudBackupRestorer =
    LiteAccountCloudBackupRestorerImpl(
      appPrivateKeyDao = appComponent.appPrivateKeyDao,
      socRecKeysDao = socRecKeysDao,
      accountAuthenticator = appComponent.accountAuthenticator,
      authTokenDao = appComponent.authTokenDao,
      cloudBackupDao = cloudBackupDao,
      accountRepository = appComponent.accountRepository
    )

  val liteAccountBackupToFullAccountUpgrader =
    LiteAccountBackupToFullAccountUpgraderImpl(
      liteAccountCloudBackupRestorer = liteAccountCloudBackupRestorer,
      onboardingAppKeyKeystore = appComponent.onboardingAppKeyKeystore,
      onboardingKeyboxHardwareKeysDao = onboardingKeyboxHwAuthPublicKeyDao,
      uuidGenerator = appComponent.uuidGenerator,
      liteToFullAccountUpgrader = liteToFullAccountUpgrader
    )

  private val replaceWithLiteAccountRestoreUiStateMachine =
    ReplaceWithLiteAccountRestoreUiStateMachineImpl(
      proofOfPossessionNfcStateMachine = proofOfPossessionNfcStateMachine,
      onboardingFullAccountDeleter = onboardingFullAccountDeleter,
      liteAccountBackupToFullAccountUpgrader = liteAccountBackupToFullAccountUpgrader
    )

  val overwriteFullAccountCloudBackupUiStateMachine =
    OverwriteFullAccountCloudBackupUiStateMachineImpl(
      onboardingFullAccountDeleter = onboardingFullAccountDeleter,
      proofOfPossessionNfcStateMachine = proofOfPossessionNfcStateMachine
    )

  private val createAccountUiStateMachine =
    CreateAccountUiStateMachineImpl(
      createKeyboxUiStateMachine = createKeyboxUiStateMachine,
      onboardKeyboxUiStateMachine = onboardKeyboxUiStateMachine,
      replaceWithLiteAccountRestoreUiStateMachine = replaceWithLiteAccountRestoreUiStateMachine,
      overwriteFullAccountCloudBackupUiStateMachine = overwriteFullAccountCloudBackupUiStateMachine
    )

  val fwupNfcSessionUiStateMachine =
    FwupNfcSessionUiStateMachineImpl(
      deviceInfoProvider = appComponent.deviceInfoProvider,
      enableNfcNavigator = enableNfcNavigator,
      eventTracker = appComponent.eventTracker,
      delayer = appComponent.delayer,
      fwupProgressCalculator = appComponent.fwupProgressCalculator,
      nfcReaderCapabilityProvider = nfcReaderCapabilityProvider,
      nfcTransactor = nfcTransactor,
      fwupDataDao = appComponent.fwupDataDao,
      firmwareDataService = appComponent.firmwareDataService
    )

  val fwupNfcUiStateMachine =
    FwupNfcUiStateMachineImpl(
      deviceInfoProvider = appComponent.deviceInfoProvider,
      fwupNfcSessionUiStateMachine = fwupNfcSessionUiStateMachine
    )

  val coachmarkService = CoachmarkServiceImpl(
    coachmarkDao = CoachmarkDaoImpl(appComponent.bitkeyDatabaseProvider),
    accountRepository = appComponent.accountRepository,
    coachmarkVisibilityDecider = CoachmarkVisibilityDecider(
      clock = Clock.System
    ),
    coachmarksGlobalFeatureFlag = appComponent.coachmarksGlobalFeatureFlag,
    eventTracker = appComponent.eventTracker,
    clock = Clock.System
  )

  val infoOptionsStateMachine =
    InfoOptionsUiStateMachineImpl(
      appVariant = appComponent.appVariant,
      accountRepository = appComponent.accountRepository,
      clipboard = clipboard,
      appInstallationDao = appComponent.appInstallationDao,
      appVersion = appComponent.appVersion,
      osVersionInfoProvider = appComponent.osVersionInfoProvider
    )

  val bitkeyOptionsStateMachine =
    BitkeyDeviceOptionsUiStateMachineImpl(
      appVariant = appComponent.appVariant
    )

  val firmwareMetadataStateMachine =
    FirmwareMetadataUiStateMachineImpl(
      firmwareDeviceInfoDao = appComponent.firmwareDeviceInfoDao,
      firmwareMetadataDao = appComponent.firmwareMetadataDao,
      nfcSessionUIStateMachine = nfcSessionUIStateMachine
    )

  val accountConfigStateMachine = AccountConfigUiStateMachineImpl(
    appVariant = appComponent.appVariant,
    debugOptionsService = appComponent.debugOptionsService
  )

  val f8eEnvironmentPickerStateMachine =
    F8eEnvironmentPickerUiStateMachineImpl(
      appVariant = appComponent.appVariant,
      debugOptionsService = appComponent.debugOptionsService
    )

  val bitcoinNetworkPickerStateMachine = BitcoinNetworkPickerUiStateMachineImpl(
    appVariant = appComponent.appVariant,
    debugOptionsService = appComponent.debugOptionsService
  )

  val setSpendingLimitUiStateMachine = SetSpendingLimitUiStateMachineImpl(
    spendingLimitPickerUiStateMachine = spendingLimitPickerUiStateMachine,
    timeZoneProvider = timeZoneProvider,
    moneyDisplayFormatter = moneyDisplayFormatter,
    fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository
  )

  val spendingLimitCardStateMachine =
    SpendingLimitCardUiStateMachineImpl(
      moneyDisplayFormatter = moneyDisplayFormatter,
      timeZoneFormatter = timeZoneFormatter,
      localeIdentifierProvider = localeIdentifierProvider
    )

  val mobilePayStatusUiStateMachine =
    MobilePayStatusUiStateMachineImpl(
      moneyDisplayFormatter = moneyDisplayFormatter,
      spendingLimitCardUiStateMachine = spendingLimitCardStateMachine,
      fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository
    )

  val analyticOptionsUiStateMachine =
    AnalyticsOptionsUiStateMachineImpl(
      appVariant = appComponent.appVariant
    )

  val analyticsStateMachine =
    AnalyticsUiStateMachineImpl(
      eventStore = appComponent.eventStore,
      analyticsTrackingPreference = appComponent.analyticsTrackingPreference
    )

  val logsStateMachine =
    LogsUiStateMachineImpl(
      dateTimeFormatter = dateTimeFormatter,
      logStore = appComponent.logStore,
      timeZoneProvider = timeZoneProvider
    )

  val delayNotifyHardwareRecoveryStarter =
    LostHardwareRecoveryStarterImpl(
      initiateAccountDelayNotifyF8eClient = initiateAccountDelayNotifyF8eClient,
      recoveryDao = appComponent.recoveryDao
    )

  val mobilePaySettingsUiStateMachine =
    MobilePaySettingsUiStateMachineImpl(
      mobilePayStatusUiStateMachine = mobilePayStatusUiStateMachine,
      setSpendingLimitUiStateMachine = setSpendingLimitUiStateMachine
    )

  val transactionPriorityPreference =
    TransactionPriorityPreferenceImpl(
      databaseProvider = appComponent.bitkeyDatabaseProvider
    )

  val feeOptionUiStateMachine =
    FeeOptionUiStateMachineImpl(
      currencyConverter = currencyConverter,
      moneyDisplayFormatter = moneyDisplayFormatter
    )

  val feeOptionListUiStateMachine = FeeOptionListUiStateMachineImpl(
    feeOptionUiStateMachine = feeOptionUiStateMachine,
    fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository
  )

  val transferConfirmationStateMachine =
    TransferConfirmationUiStateMachineImpl(
      bitcoinBlockchain = bitcoinBlockchain,
      mobilePaySigningF8eClient = mobilePaySigningF8eClient,
      transactionDetailsCardUiStateMachine = transactionDetailsCardUiStateMachine,
      nfcSessionUIStateMachine = nfcSessionUIStateMachine,
      transactionPriorityPreference = transactionPriorityPreference,
      feeOptionListUiStateMachine = feeOptionListUiStateMachine,
      appSpendingWalletProvider = appComponent.appSpendingWalletProvider,
      outgoingTransactionDetailRepository = outgoingTransactionDetailRepository,
      fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository
    )

  val bitcoinTransactionFeeEstimator =
    BitcoinTransactionFeeEstimatorImpl(
      bitcoinFeeRateEstimator = appComponent.bitcoinFeeRateEstimator,
      appSpendingWalletProvider = appComponent.appSpendingWalletProvider,
      datadogRumMonitor = datadogRumMonitor
    )

  val feeSelectionStateMachine =
    FeeSelectionUiStateMachineImpl(
      bitcoinTransactionFeeEstimator = bitcoinTransactionFeeEstimator,
      transactionPriorityPreference = transactionPriorityPreference,
      feeOptionListUiStateMachine = feeOptionListUiStateMachine,
      transactionBaseCalculator = BitcoinTransactionBaseCalculatorImpl()
    )

  val sendStateMachine =
    SendUiStateMachineImpl(
      bitcoinAddressRecipientUiStateMachine = bitcoinAddressRecipientStateMachine,
      transferAmountEntryUiStateMachine = transferAmountEntryUiStateMachine,
      transferConfirmationUiStateMachine = transferConfirmationStateMachine,
      transferInitiatedUiStateMachine = transferInitiatedUiStateMachine,
      bitcoinQrCodeUiScanStateMachine = bitcoinQrCodeScanStateMachine,
      permissionUiStateMachine = permissionStateMachine,
      feeSelectionUiStateMachine = feeSelectionStateMachine,
      exchangeRateSyncer = exchangeRateSyncer,
      clock = appComponent.clock,
      networkReachabilityProvider = appComponent.networkReachabilityProvider,
      fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository
    )

  val transactionDetailStateMachine =
    build.wallet.statemachine.transactions.TransactionDetailsUiStateMachineImpl(
      bitcoinExplorer = bitcoinExplorer,
      bitcoinTransactionBumpabilityChecker = BitcoinTransactionBumpabilityCheckerImpl(
        sweepChecker = BitcoinTransactionSweepCheckerImpl()
      ),
      clock = appComponent.clock,
      currencyConverter = currencyConverter,
      dateTimeFormatter = dateTimeFormatter,
      durationFormatter = durationFormatter,
      eventTracker = appComponent.eventTracker,
      feeBumpEnabled = appComponent.feeBumpIsAvailableFeatureFlag,
      moneyDisplayFormatter = moneyDisplayFormatter,
      timeZoneProvider = timeZoneProvider,
      fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository,
      feeBumpConfirmationUiStateMachine = FeeBumpConfirmationUiStateMachineImpl(
        fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository,
        transactionDetailsCardUiStateMachine = transactionDetailsCardUiStateMachine,
        exchangeRateSyncer = exchangeRateSyncer,
        nfcSessionUIStateMachine = nfcSessionUIStateMachine,
        bitcoinBlockchain = bitcoinBlockchain,
        outgoingTransactionDetailRepository = outgoingTransactionDetailRepository,
        transferInitiatedUiStateMachine = transferInitiatedUiStateMachine
      ),
      feeRateEstimator = appComponent.bitcoinFeeRateEstimator,
      appSpendingWalletProvider = appComponent.appSpendingWalletProvider
    )

  val homeUiBottomSheetDao = HomeUiBottomSheetDaoImpl(appComponent.bitkeyDatabaseProvider)

  val authKeyRotationAttemptDao = AuthKeyRotationAttemptDaoImpl(
    databaseProvider = appComponent.bitkeyDatabaseProvider
  )

  val appDataDeleter =
    AppDataDeleterImpl(
      appVariant = appComponent.appVariant,
      appPrivateKeyDao = appComponent.appPrivateKeyDao,
      accountRepository = appComponent.accountRepository,
      authTokenDao = appComponent.authTokenDao,
      gettingStartedTaskDao = gettingStartedTaskDao,
      keyboxDao = appComponent.keyboxDao,
      notificationTouchpointDao = appComponent.notificationTouchpointDao,
      onboardingKeyboxSealedCsekDao = onboardingKeyboxSealedCsekDao,
      onboardingKeyboxStepStateDao = onboardingKeyboxStepStateDao,
      onboardingKeyboxHardwareKeysDao = onboardingKeyboxHwAuthPublicKeyDao,
      mobilePayService = mobilePayService,
      outgoingTransactionDetailDao = appComponent.outgoingTransactionDetailDao,
      fwupDataDao = appComponent.fwupDataDao,
      firmwareDeviceInfoDao = appComponent.firmwareDeviceInfoDao,
      firmwareMetadataDao = appComponent.firmwareMetadataDao,
      transactionPriorityPreference = transactionPriorityPreference,
      onboardingAppKeyKeystore = appComponent.onboardingAppKeyKeystore,
      fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository,
      homeUiBottomSheetDao = homeUiBottomSheetDao,
      bitcoinDisplayPreferenceRepository = appComponent.bitcoinDisplayPreferenceRepository,
      cloudBackupDao = cloudBackupDao,
      socRecKeysDao = socRecKeysDao,
      socRecRelationshipsRepository = socRecRelationshipsRepository,
      socRecStartedChallengeDao = socRecPendingChallengeDao,
      csekDao = csekDao,
      authKeyRotationAttemptDao = authKeyRotationAttemptDao,
      recoveryDao = appComponent.recoveryDao,
      authSignatureStatusProvider = appComponent.f8eAuthSignatureStatusProvider,
      hideBalancePreference = hideBalancePreference,
      biometricPreference = appComponent.biometricPreference
    )

  val deviceUpdateCardUiStateMachine =
    DeviceUpdateCardUiStateMachineImpl(
      eventTracker = appComponent.eventTracker,
      firmwareDataService = appComponent.firmwareDataService
    )

  val gettingStartedCardStateMachine =
    GettingStartedCardUiStateMachineImpl(
      gettingStartedTaskDao = gettingStartedTaskDao,
      eventTracker = appComponent.eventTracker
    )

  val pendingInvitationsCardUiStateMachine =
    RecoveryContactCardsUiStateMachineImpl(
      clock = appComponent.clock
    )

  val replaceHardwareCardUiStateMachine =
    SetupHardwareCardUiStateMachineImpl(
      postSocRecTaskRepository = postSocRecTaskRepository,
      firmwareDataService = appComponent.firmwareDataService
    )

  val cloudBackupHealthCardUiStateMachine = CloudBackupHealthCardUiStateMachineImpl(
    cloudBackupHealthRepository = cloudBackupHealthRepository
  )

  val sweepPromptRequirementCheck = SweepPromptRequirementCheckImpl(
    promptSweepFeatureFlag = appComponent.promptSweepFeatureFlag,
    sweepGenerator = sweepGenerator
  )

  val startSweepCardUiStateMachine = StartSweepCardUiStateMachineImpl(
    sweepPromptRequirementCheck = sweepPromptRequirementCheck,
    appSessionManager = appComponent.appSessionManager
  )

  val bitcoinPriceCardUiStateMachine = BitcoinPriceCardUiStateMachineImpl(
    bitcoinPriceCardPreference = appComponent.bitcoinPriceCardPreference
  )

  val cardListStateMachine =
    MoneyHomeCardsUiStateMachineImpl(
      deviceUpdateCardUiStateMachine = deviceUpdateCardUiStateMachine,
      gettingStartedCardUiStateMachine = gettingStartedCardStateMachine,
      hardwareRecoveryStatusCardUiStateMachine = hardwareRecoveryStatusCardUiStateMachine,
      recoveryContactCardsUiStateMachine = pendingInvitationsCardUiStateMachine,
      setupHardwareCardUiStateMachine = replaceHardwareCardUiStateMachine,
      cloudBackupHealthCardUiStateMachine = cloudBackupHealthCardUiStateMachine,
      startSweepCardUiStateMachine = startSweepCardUiStateMachine,
      bitcoinPriceCardUiStateMachine = bitcoinPriceCardUiStateMachine
    )

  val deepLinkHandler =
    DeepLinkHandlerImpl(
      platformContext = appComponent.platformContext
    )

  val partnershipsTransactionsDao =
    PartnershipTransactionsDaoImpl(appComponent.bitkeyDatabaseProvider)

  val getPartnershipTransactionF8eClient = GetPartnershipTransactionF8eClientImpl(
    client = appComponent.f8eHttpClient
  )

  val partnershipTransactionsStatusRepository = PartnershipTransactionsRepositoryImpl(
    dao = partnershipsTransactionsDao,
    uuidGenerator = appComponent.uuidGenerator,
    clock = appComponent.clock,
    getPartnershipTransactionF8eClient = getPartnershipTransactionF8eClient
  )

  val getTransferPartnerListF8eClient = GetTransferPartnerListF8eClientImpl(
    appComponent.countryCodeGuesser,
    appComponent.f8eHttpClient
  )

  val partnershipsTransferUiStateMachine =
    PartnershipsTransferUiStateMachineImpl(
      getTransferPartnerListF8eClient = getTransferPartnerListF8eClient,
      getTransferRedirectF8eClient = GetTransferRedirectF8eClientImpl(appComponent.f8eHttpClient),
      partnershipsRepository = partnershipTransactionsStatusRepository,
      eventTracker = appComponent.eventTracker,
      bitcoinAddressService = appComponent.bitcoinAddressService
    )

  val partnershipsPurchaseUiStateMachine =
    PartnershipsPurchaseUiStateMachineImpl(
      moneyDisplayFormatter = moneyDisplayFormatter,
      getPurchaseOptionsF8eClient = GetPurchaseOptionsF8eClientImpl(
        appComponent.countryCodeGuesser,
        appComponent.f8eHttpClient
      ),
      getPurchaseQuoteListF8eClient = GetPurchaseQuoteListF8eClientImpl(
        appComponent.countryCodeGuesser,
        appComponent.f8eHttpClient
      ),
      getPurchaseRedirectF8eClient = GetPurchaseRedirectF8eClientImpl(appComponent.f8eHttpClient),
      partnershipsRepository = partnershipTransactionsStatusRepository,
      fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository,
      eventTracker = appComponent.eventTracker,
      exchangeRateSyncer = exchangeRateSyncer,
      currencyConverter = currencyConverter,
      bitcoinAddressService = appComponent.bitcoinAddressService
    )

  val addBitcoinUiStateMachine =
    AddBitcoinUiStateMachineImpl(
      partnershipsTransferUiStateMachine = partnershipsTransferUiStateMachine,
      partnershipsPurchaseUiStateMachine = partnershipsPurchaseUiStateMachine
    )

  val initiatingLostHardwareRecoveryUiStateMachine =
    InitiatingLostHardwareRecoveryUiStateMachineImpl(
      pairNewHardwareUiStateMachine = pairNewHardwareUiStateMachine,
      eventTracker = appComponent.eventTracker,
      recoveryNotificationVerificationUiStateMachine = recoveryNotificationVerificationUiStateMachine,
      proofOfPossessionNfcStateMachine = proofOfPossessionNfcStateMachine
    )

  val lostHardwareRecoveryUiStateMachine =
    LostHardwareRecoveryUiStateMachineImpl(
      initiatingLostHardwareRecoveryUiStateMachine = initiatingLostHardwareRecoveryUiStateMachine,
      recoveryInProgressUiStateMachine = recoveryInProgressUiStateMachine
    )

  val rotateAuthKeysF8eClient: RotateAuthKeysF8eClient = RotateAuthKeysF8eClientImpl(
    f8eHttpClient = appComponent.f8eHttpClient,
    signer = appComponent.appAuthKeyMessageSigner
  )

  val trustedContactKeyAuthenticator = TrustedContactKeyAuthenticatorImpl(
    socRecRelationshipsRepository = socRecRelationshipsRepository,
    socRecRelationshipsDao = socRecRelationshipsDao,
    socRecEnrollmentAuthenticationDao = socRecEnrollmentAuthenticationDao,
    socRecCrypto = socRecCrypto,
    endorseTrustedContactsF8eClientProvider = { socialRecoveryF8eClientProvider.get() }
  )

  val fullAccountAuthKeyRotationService: FullAccountAuthKeyRotationService =
    FullAccountAuthKeyRotationServiceImpl(
      authKeyRotationAttemptDao = authKeyRotationAttemptDao,
      rotateAuthKeysF8eClient = rotateAuthKeysF8eClient,
      keyboxDao = appComponent.keyboxDao,
      accountAuthenticator = appComponent.accountAuthenticator,
      bestEffortFullAccountCloudBackupUploader = bestEffortFullAccountCloudBackupUploader,
      socRecRelationshipsRepository = socRecRelationshipsRepository,
      trustedContactKeyAuthenticator = trustedContactKeyAuthenticator
    )

  val rotateAuthUIStateMachine = RotateAuthKeyUIStateMachineImpl(
    appKeysGenerator = appComponent.appKeysGenerator,
    proofOfPossessionNfcStateMachine = proofOfPossessionNfcStateMachine,
    fullAccountAuthKeyRotationService = fullAccountAuthKeyRotationService,
    inAppBrowserNavigator = inAppBrowserNavigator
  )

  val inviteTrustedContactFlowUiStateMachine =
    InviteTrustedContactFlowUiStateMachineImpl(
      addingTrustedContactUiStateMachine = addingTcsUiStateMachine,
      gettingStartedTaskDao = gettingStartedTaskDao,
      socRecRelationshipsRepository = socRecRelationshipsRepository
    )

  val appFunctionalityStatusProvider =
    AppFunctionalityStatusProviderImpl(
      networkReachabilityEventDao = appComponent.networkReachabilityEventDao,
      networkReachabilityProvider = appComponent.networkReachabilityProvider,
      f8eAuthSignatureStatusProvider = appComponent.f8eAuthSignatureStatusProvider,
      appVariant = appComponent.appVariant
    )

  val editingFingerprintUiStateMachine = EditingFingerprintUiStateMachineImpl()

  val fingerprintNfcCommands = FingerprintNfcCommandsImpl()
  val enrollingFingerprintUiStateMachine = EnrollingFingerprintUiStateMachineImpl(
    nfcSessionUIStateMachine = nfcSessionUIStateMachine,
    fingerprintNfcCommands = fingerprintNfcCommands
  )

  val managingFingerprintsUiStateMachine = ManagingFingerprintsUiStateMachineImpl(
    editingFingerprintUiStateMachine = editingFingerprintUiStateMachine,
    nfcSessionUIStateMachine = nfcSessionUIStateMachine,
    enrollingFingerprintUiStateMachine = enrollingFingerprintUiStateMachine,
    gettingStartedTaskDao = gettingStartedTaskDao,
    eventTracker = appComponent.eventTracker
  )

  val moneyHomeHiddenStatusProvider = MoneyHomeHiddenStatusProviderImpl(
    hideBalancePreference = hideBalancePreference,
    appSessionManager = appComponent.appSessionManager,
    appCoroutineScope = appComponent.appCoroutineScope
  )

  val moneyHomeViewingBalanceUiStateMachine =
    MoneyHomeViewingBalanceUiStateMachineImpl(
      addBitcoinUiStateMachine = addBitcoinUiStateMachine,
      appFunctionalityStatusProvider = appFunctionalityStatusProvider,
      deepLinkHandler = deepLinkHandler,
      moneyDisplayFormatter = moneyDisplayFormatter,
      gettingStartedTaskDao = gettingStartedTaskDao,
      moneyHomeCardsUiStateMachine = cardListStateMachine,
      transactionListUiStateMachine = transactionListStateMachine,
      viewingInvitationUiStateMachine = viewingInvitationUiStateMachine,
      viewingRecoveryContactUiStateMachine = viewingRecoveryContactUiStateMachine,
      eventTracker = appComponent.eventTracker,
      fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository,
      haptics = appComponent.haptics,
      moneyHomeHiddenStatusProvider = moneyHomeHiddenStatusProvider,
      sweepPromptRequirementCheck = sweepPromptRequirementCheck,
      coachmarkService = coachmarkService,
      firmwareDataService = appComponent.firmwareDataService
    )
  val customAmountEntryUiStateMachine =
    CustomAmountEntryUiStateMachineImpl(
      moneyCalculatorUiStateMachine = moneyCalculatorUiStateMachine,
      moneyDisplayFormatter = moneyDisplayFormatter,
      fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository
    )

  val exchangeRateChartFetcher = ChartDataFetcherServiceImpl(
    exchangeRateF8eClient = appComponent.exchangeRateF8eClient,
    fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository
  )

  val bitcoinPriceDetailsUiStateMachine = BitcoinPriceChartUiStateMachineImpl(
    moneyDisplayFormatter = moneyDisplayFormatter,
    dateTimeFormatter = dateTimeFormatter,
    fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository,
    haptics = appComponent.haptics,
    chartDataFetcher = exchangeRateChartFetcher,
    eventTracker = appComponent.eventTracker,
    currencyConverter = currencyConverter
  )

  val moneyHomeStateMachine =
    MoneyHomeUiStateMachineImpl(
      addressQrCodeUiStateMachine = addressQrCodeUiStateMachine,
      sendUiStateMachine = sendStateMachine,
      transactionDetailsUiStateMachine = transactionDetailStateMachine,
      transactionListUiStateMachine = transactionListStateMachine,
      fwupNfcUiStateMachine = fwupNfcUiStateMachine,
      lostHardwareUiStateMachine = lostHardwareRecoveryUiStateMachine,
      setSpendingLimitUiStateMachine = setSpendingLimitUiStateMachine,
      inviteTrustedContactFlowUiStateMachine = inviteTrustedContactFlowUiStateMachine,
      inAppBrowserNavigator = inAppBrowserNavigator,
      clipboard = clipboard,
      paymentDataParser = paymentDataParser,
      recoveryIncompleteRepository = postSocRecTaskRepository,
      moneyHomeViewingBalanceUiStateMachine = moneyHomeViewingBalanceUiStateMachine,
      customAmountEntryUiStateMachine = customAmountEntryUiStateMachine,
      repairCloudBackupStateMachine = repairMobileKeyBackupUiStateMachine,
      fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository,
      managingFingerprintsUiStateMachine = managingFingerprintsUiStateMachine,
      sweepUiStateMachine = sweepUiStateMachine,
      bitcoinPriceChartUiStateMachine = bitcoinPriceDetailsUiStateMachine,
      socRecService = socRecService
    )

  val appStateDeleterOptionsUiStateMachine =
    AppStateDeleterOptionsUiStateMachineImpl(appComponent.appVariant)

  val booleanFlagItemStateMachine = BooleanFlagItemUiStateMachineImpl()

  val featureFlagsStateMachine = FeatureFlagsStateMachineImpl(
    featureFlagService = appComponent.featureFlagService,
    booleanFlagItemUiStateMachine = booleanFlagItemStateMachine,
    doubleFlagItemUiStateMachine = DoubleFlagItemUiStateMachineImpl(),
    stringFlagItemUiStateMachine = StringFlagItemUiStateMachineImpl()
  )

  val featureFlagsOptionsStateMachine =
    FeatureFlagsOptionsUiStateMachineImpl(
      appVariant = appComponent.appVariant
    )

  val cloudBackupDeleter =
    CloudBackupDeleterImpl(
      appVariant = appComponent.appVariant,
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      cloudBackupRepository = cloudBackupRepository
    )

  val onboardingAppKeyDeletionUiStateMachine =
    OnboardingAppKeyDeletionUiStateMachineImpl(
      appVariant = appComponent.appVariant,
      onboardingAppKeyKeystore = appComponent.onboardingAppKeyKeystore
    )

  val networkingDebugConfigPickerUiStateMachine =
    NetworkingDebugConfigPickerUiStateMachineImpl(
      appComponent.networkingDebugConfigRepository
    )

  private val onboardingConfigStateMachine = OnboardingConfigStateMachineImpl(
    appVariant = appComponent.appVariant,
    debugOptionsService = appComponent.debugOptionsService
  )

  val debugMenuListStateMachine =
    DebugMenuListStateMachineImpl(
      accountConfigUiStateMachine = accountConfigStateMachine,
      appDataDeleter = appDataDeleter,
      appStateDeleterOptionsUiStateMachine = appStateDeleterOptionsUiStateMachine,
      appVariant = appComponent.appVariant,
      analyticsOptionsUiStateMachine = analyticOptionsUiStateMachine,
      bitcoinNetworkPickerUiStateMachine = bitcoinNetworkPickerStateMachine,
      bitkeyDeviceOptionsUiStateMachine = bitkeyOptionsStateMachine,
      cloudBackupDeleter = cloudBackupDeleter,
      f8eEnvironmentPickerUiStateMachine = f8eEnvironmentPickerStateMachine,
      featureFlagsOptionsUiStateMachine = featureFlagsOptionsStateMachine,
      infoOptionsUiStateMachine = infoOptionsStateMachine,
      onboardingAppKeyDeletionUiStateMachine = onboardingAppKeyDeletionUiStateMachine,
      onboardingConfigStateMachine = onboardingConfigStateMachine,
      cloudSignUiStateMachine = cloudSignInUiStateMachine,
      coachmarkService = coachmarkService,
      debugOptionsService = appComponent.debugOptionsService
    )

  val debugMenuStateMachine =
    DebugMenuStateMachineImpl(
      analyticsUiStateMachine = analyticsStateMachine,
      debugMenuListStateMachine = debugMenuListStateMachine,
      f8eCustomUrlStateMachine = F8eCustomUrlStateMachineImpl(appComponent.debugOptionsService),
      featureFlagsStateMachine = featureFlagsStateMachine,
      firmwareMetadataUiStateMachine = firmwareMetadataStateMachine,
      fwupNfcUiStateMachine = fwupNfcUiStateMachine,
      logsUiStateMachine = logsStateMachine,
      networkingDebugConfigPickerUiStateMachine = networkingDebugConfigPickerUiStateMachine,
      nfcSessionUIStateMachine = nfcSessionUIStateMachine,
      cloudDevOptionsStateMachine = cloudDevOptionsStateMachine,
      firmwareDataService = appComponent.firmwareDataService
    )

  val demoModeF8eClient = DemoModeF8eClientImpl(
    appComponent.f8eHttpClient
  )
  val demoModeCodeEntryUiStateMachine = DemoModeCodeEntryUiStateMachineImpl(
    delayer = appComponent.delayer,
    demoModeF8eClient = demoModeF8eClient,
    debugOptionsService = appComponent.debugOptionsService
  )
  val demoModeConfigUiStateMachine = DemoModeConfigUiStateMachineImpl(
    demoModeCodeEntryUiStateMachine = demoModeCodeEntryUiStateMachine,
    debugOptionsService = appComponent.debugOptionsService
  )

  val electrumConfigService = ElectrumConfigServiceImpl(
    electrumServerConfigRepository = appComponent.electrumServerConfigRepository,
    debugOptionsService = appComponent.debugOptionsService,
    getBdkConfigurationF8eClient = GetBdkConfigurationF8eClientImpl(
      appComponent.f8eHttpClient,
      appComponent.deviceInfoProvider
    )
  )

  val customElectrumServerUiStateMachine = CustomElectrumServerUiStateMachineImpl(
    electrumConfigService = electrumConfigService
  )

  val setElectrumServerUiStateMachine =
    SetElectrumServerUiStateMachineImpl(
      delayer = appComponent.delayer,
      electrumServerSettingProvider = appComponent.electrumServerSettingProvider,
      electrumReachability = appComponent.electrumReachability
    )

  val customElectrumServerSettingUiStateMachine =
    CustomElectrumServerSettingUiStateMachineImpl(
      customElectrumServerUIStateMachine = customElectrumServerUiStateMachine,
      setElectrumServerUiStateMachine = setElectrumServerUiStateMachine,
      electrumConfigService = electrumConfigService
    )

  val resettingDeviceUiStateMachine = ResettingDeviceUiStateMachineImpl(
    resettingDeviceIntroUiStateMachine = ResettingDeviceIntroUiStateMachineImpl(
      nfcSessionUIStateMachine = nfcSessionUIStateMachine,
      signatureVerifier = appComponent.signatureVerifier,
      moneyDisplayFormatter = moneyDisplayFormatter,
      fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository,
      currencyConverter = currencyConverter,
      mobilePayService = mobilePayService,
      authF8eClient = appComponent.authF8eClient
    ),
    resettingDeviceConfirmationUiStateMachine = ResettingDeviceConfirmationUiStateMachineImpl(
      nfcSessionUIStateMachine = nfcSessionUIStateMachine,
      firmwareDeviceInfoDao = appComponent.firmwareDeviceInfoDao
    ),
    resettingDeviceSuccessUiStateMachine = ResettingDeviceSuccessUiStateMachineImpl(),
    resettingDeviceProgressUiStateMachine = ResettingDeviceProgressUiStateMachineImpl()
  )

  val deviceSettingsUiStateMachine =
    DeviceSettingsUiStateMachineImpl(
      lostHardwareRecoveryUiStateMachine = lostHardwareRecoveryUiStateMachine,
      nfcSessionUIStateMachine = nfcSessionUIStateMachine,
      fwupNfcUiStateMachine = fwupNfcUiStateMachine,
      dateTimeFormatter = dateTimeFormatter,
      timeZoneProvider = timeZoneProvider,
      durationFormatter = durationFormatter,
      firmwareDeviceInfoDao = appComponent.firmwareDeviceInfoDao,
      appFunctionalityStatusProvider = appFunctionalityStatusProvider,
      managingFingerprintsUiStateMachine = managingFingerprintsUiStateMachine,
      resettingDeviceUiStateMachine = resettingDeviceUiStateMachine,
      coachmarkService = coachmarkService,
      firmwareDataService = appComponent.firmwareDataService
    )

  val customerFeedbackF8eClient =
    SupportTicketF8eClientImpl(
      f8eHttpClient = appComponent.f8eHttpClient
    )

  val supportTicketRepository =
    SupportTicketRepositoryImpl(
      supportTicketF8eClient = customerFeedbackF8eClient,
      logStore = appComponent.logStore,
      appInstallationDao = appComponent.appInstallationDao,
      firmwareDeviceInfoDao = appComponent.firmwareDeviceInfoDao,
      platformInfoProvider = appComponent.platformInfoProvider,
      allFeatureFlags = appComponent.allFeatureFlags
    )

  val supportTicketFormValidator =
    SupportTicketFormValidatorImpl(
      emailValidator = emailValidator
    )

  val feedbackFormUiStateMachine =
    FeedbackFormUiStateMachineImpl(
      delayer = appComponent.delayer,
      supportTicketRepository = supportTicketRepository,
      supportTicketFormValidator = supportTicketFormValidator,
      dateTimeFormatter = dateTimeFormatter,
      inAppBrowserNavigator = inAppBrowserNavigator
    )

  val feedbackUiStateMachine =
    FeedbackUiStateMachineImpl(
      supportTicketRepository = supportTicketRepository,
      feedbackFormUiStateMachine = feedbackFormUiStateMachine
    )

  val settingsListUiStateMachine =
    SettingsListUiStateMachineImpl(
      appFunctionalityStatusProvider = appFunctionalityStatusProvider,
      cloudBackupHealthRepository = cloudBackupHealthRepository,
      coachmarkService = coachmarkService
    )
  val cloudBackupHealthDashboardUiStateMachine = CloudBackupHealthDashboardUiStateMachineImpl(
    uuidGenerator = appComponent.uuidGenerator,
    cloudBackupHealthRepository = cloudBackupHealthRepository,
    dateTimeFormatter = dateTimeFormatter,
    timeZoneProvider = timeZoneProvider,
    repairCloudBackupStateMachine = repairMobileKeyBackupUiStateMachine,
    cloudBackupDao = cloudBackupDao,
    emergencyAccessKitPdfGenerator = emergencyAccessKitPdfGenerator,
    sharingManager = sharingManager
  )

  val biometricSettingUiStateMachine = BiometricSettingUiStateMachineImpl(
    biometricPreference = appComponent.biometricPreference,
    biometricTextProvider = BiometricTextProviderImpl(),
    nfcSessionUIStateMachine = nfcSessionUIStateMachine,
    biometricPrompter = biometricPrompter,
    signatureVerifier = appComponent.signatureVerifier,
    settingsLauncher = systemSettingsLauncher,
    coachmarkService = coachmarkService
  )

  val settingsStateMachine =
    SettingsHomeUiStateMachineImpl(
      appVariant = appComponent.appVariant,
      mobilePaySettingsUiStateMachine = mobilePaySettingsUiStateMachine,
      notificationPreferencesUiStateMachine = notificationPreferencesUiStateMachine,
      recoveryChannelSettingsUiStateMachine = recoveryChannelSettingsUiStateMachineImpl,
      currencyPreferenceUiStateMachine = currencyPreferenceUiStateMachine,
      customElectrumServerSettingUiStateMachine = customElectrumServerSettingUiStateMachine,
      deviceSettingsUiStateMachine = deviceSettingsUiStateMachine,
      feedbackUiStateMachine = feedbackUiStateMachine,
      helpCenterUiStateMachine = helpCenterUiStateMachine,
      trustedContactManagementUiStateMachine = trustedContactManagementUiStateMachine,
      settingsListUiStateMachine = settingsListUiStateMachine,
      cloudBackupHealthDashboardUiStateMachine = cloudBackupHealthDashboardUiStateMachine,
      rotateAuthKeyUIStateMachine = rotateAuthUIStateMachine,
      debugMenuStateMachine = debugMenuStateMachine,
      biometricSettingUiStateMachine = biometricSettingUiStateMachine,
      firmwareDataService = appComponent.firmwareDataService
    )

  val homeUiBottomSheetStateMachine =
    HomeUiBottomSheetStateMachineImpl(
      homeUiBottomSheetDao = homeUiBottomSheetDao,
      fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository
    )

  val currencyChangeMobilePayBottomSheetUpdater =
    CurrencyChangeMobilePayBottomSheetUpdaterImpl(
      homeUiBottomSheetDao = homeUiBottomSheetDao
    )

  val homeStatusBannerUiStateMachine =
    HomeStatusBannerUiStateMachineImpl(
      appFunctionalityStatusProvider = appFunctionalityStatusProvider,
      dateTimeFormatter = dateTimeFormatter,
      timeZoneProvider = timeZoneProvider,
      clock = appComponent.clock,
      eventTracker = appComponent.eventTracker
    )

  val appFunctionalityStatusUiStateMachine =
    AppFunctionalityStatusUiStateMachineImpl(
      dateTimeFormatter = dateTimeFormatter,
      timeZoneProvider = timeZoneProvider,
      clock = appComponent.clock
    )

  val getPartnerF8eClient = GetPartnerF8eClientImpl(
    client = appComponent.f8eHttpClient
  )

  val expectedTransactionNoticeUiStateMachine = ExpectedTransactionNoticeUiStateMachineImpl(
    dateTimeFormatter = dateTimeFormatter,
    transactionsStatusRepository = partnershipTransactionsStatusRepository,
    delayer = Delayer.Default
  )

  val homeUiStateMachine =
    HomeUiStateMachineImpl(
      appFunctionalityStatusUiStateMachine = appFunctionalityStatusUiStateMachine,
      currencyChangeMobilePayBottomSheetUpdater = currencyChangeMobilePayBottomSheetUpdater,
      homeStatusBannerUiStateMachine = homeStatusBannerUiStateMachine,
      homeUiBottomSheetStateMachine = homeUiBottomSheetStateMachine,
      moneyHomeUiStateMachine = moneyHomeStateMachine,
      settingsHomeUiStateMachine = settingsStateMachine,
      setSpendingLimitUiStateMachine = setSpendingLimitUiStateMachine,
      trustedContactEnrollmentUiStateMachine = trustedContactEnrollmentUiStateMachine,
      socRecRelationshipsRepository = socRecRelationshipsRepository,
      cloudBackupHealthRepository = cloudBackupHealthRepository,
      appFunctionalityStatusProvider = appFunctionalityStatusProvider,
      expectedTransactionNoticeUiStateMachine = expectedTransactionNoticeUiStateMachine,
      deepLinkHandler = deepLinkHandler,
      inAppBrowserNavigator = inAppBrowserNavigator,
      clock = appComponent.clock,
      timeZoneProvider = timeZoneProvider,
      fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository
    )

  private val softwareAccountCreator = SoftwareAccountCreatorImpl(
    createSoftwareAccountF8eClient = createAccountF8eClient,
    accountAuthenticator = appComponent.accountAuthenticator,
    authTokenDao = appComponent.authTokenDao
  )

  val initiateDistributedKeygenF8eClient = InitiateDistributedKeygenF8eClientImpl(
    f8eHttpClient = appComponent.f8eHttpClient
  )
  val continueDistributedKeygenF8eClient = ContinueDistributedKeygenF8eClientImpl(
    f8eHttpClient = appComponent.f8eHttpClient
  )

  val activateSpendingDescriptorF8eClient = ActivateSpendingDescriptorF8eClientImpl(
    f8eHttpClient = appComponent.f8eHttpClient
  )

  val createSoftwareWalletService = CreateSoftwareWalletServiceImpl(
    softwareAccountCreator = softwareAccountCreator,
    initiateDistributedKeygenF8eClient = initiateDistributedKeygenF8eClient,
    continueDistributedKeygenF8eClient = continueDistributedKeygenF8eClient,
    activateSpendingDescriptorF8eClient = activateSpendingDescriptorF8eClient,
    fakeHardwareKeyStore = fakeHardwareKeyStore,
    appKeysGenerator = appKeysGenerator,
    debugOptionsService = appComponent.debugOptionsService,
    softwareWalletIsEnabledFeatureFlag = appComponent.softwareWalletIsEnabledFeatureFlag,
    accountRepository = appComponent.accountRepository
  )

  val createSoftwareWalletUiStateMachine = CreateSoftwareWalletUiStateMachineImpl(
    createSoftwareWalletService = createSoftwareWalletService
  )

  val chooseAccountAccessUiStateMachine =
    ChooseAccountAccessUiStateMachineImpl(
      appVariant = appComponent.appVariant,
      demoModeConfigUiStateMachine = demoModeConfigUiStateMachine,
      debugMenuStateMachine = debugMenuStateMachine,
      deviceInfoProvider = appComponent.deviceInfoProvider,
      emergencyAccessKitDataProvider = emergencyAccessKitDataProvider,
      softwareWalletIsEnabledFeatureFlag = appComponent.softwareWalletIsEnabledFeatureFlag,
      createSoftwareWalletUiStateMachine = createSoftwareWalletUiStateMachine
    )

  val mobilePayDataStateMachine = MobilePayDataStateMachineImpl(
    mobilePayService = mobilePayService,
    currencyConverter = currencyConverter,
    fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository
  )

  val fullAccountTransactionsDataStateMachine = FullAccountTransactionsDataStateMachineImpl(
    currencyConverter = currencyConverter,
    fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository,
    appSessionManager = appComponent.appSessionManager,
    exchangeRateSyncer = exchangeRateSyncer
  )

  val recoveryNotificationVerificationF8eClient =
    RecoveryNotificationVerificationF8eClientImpl(
      f8eHttpClient = appComponent.f8eHttpClient
    )

  val recoveryNotificationVerificationDataStateMachine =
    RecoveryNotificationVerificationDataStateMachineImpl(
      notificationTouchpointF8eClient = appComponent.notificationTouchpointF8eClient,
      recoveryNotificationVerificationF8eClient = recoveryNotificationVerificationF8eClient
    )

  val initiatingLostAppRecoveryStateMachine =
    InitiatingLostAppRecoveryDataStateMachineImpl(
      appKeysGenerator = appKeysGenerator,
      authF8eClient = appComponent.authF8eClient,
      listKeysetsF8eClient = listKeysetsF8eClient,
      lostAppRecoveryInitiator = delayNotifyLostAppRecoveryInitiator,
      lostAppRecoveryAuthenticator = delayNotifyLostAppRecoveryAuthenticator,
      recoveryNotificationVerificationDataStateMachine = recoveryNotificationVerificationDataStateMachine,
      cancelDelayNotifyRecoveryF8eClient = cancelDelayNotifyRecoveryF8eClient,
      delayer = appComponent.delayer,
      uuidGenerator = appComponent.uuidGenerator
    )

  val lostAppRecoveryHaveNotStartedDataStateMachine =
    LostAppRecoveryHaveNotStartedDataStateMachineImpl(
      initiatingLostAppRecoveryDataStateMachine = initiatingLostAppRecoveryStateMachine
    )

  val lostAppRecoveryCanceler =
    RecoveryCancelerImpl(
      cancelDelayNotifyRecoveryF8eClient = cancelDelayNotifyRecoveryF8eClient,
      recoverySyncer = recoverySyncer
    )

  val recoveryAuthCompleter =
    RecoveryAuthCompleterImpl(
      appAuthKeyMessageSigner = appComponent.appAuthKeyMessageSigner,
      completeDelayNotifyF8eClient = completeDelayNotifyF8eClient,
      accountAuthenticator = appComponent.accountAuthenticator,
      recoverySyncer = recoverySyncer,
      authTokenDao = appComponent.authTokenDao,
      delayer = appComponent.delayer,
      socRecRelationshipsRepository = socRecRelationshipsRepository
    )

  val f8eSpendingKeyRotator =
    F8eSpendingKeyRotatorImpl(
      createAccountKeysetF8eClient = createAccountKeysetF8eClient,
      setActiveSpendingKeysetF8eClient = setActiveSpendingKeysetF8eClient
    )

  val recoveryInProgressDataStateMachine =
    RecoveryInProgressDataStateMachineImpl(
      recoveryCanceler = lostAppRecoveryCanceler,
      clock = appComponent.clock,
      csekGenerator = csekGenerator,
      csekDao = csekDao,
      recoveryAuthCompleter = recoveryAuthCompleter,
      f8eSpendingKeyRotator = f8eSpendingKeyRotator,
      uuidGenerator = appComponent.uuidGenerator,
      recoverySyncer = recoverySyncer,
      recoveryNotificationVerificationDataStateMachine = recoveryNotificationVerificationDataStateMachine,
      accountAuthenticator = appComponent.accountAuthenticator,
      recoveryDao = appComponent.recoveryDao,
      delayer = appComponent.delayer,
      deviceTokenManager = appComponent.deviceTokenManager,
      socRecRelationshipsRepository = socRecRelationshipsRepository,
      trustedContactKeyAuthenticator = trustedContactKeyAuthenticator
    )

  val createKeyboxDataStateMachine = CreateKeyboxDataStateMachineImpl(
    fullAccountCreator = fullAccountCreator,
    appKeysGenerator = appKeysGenerator,
    onboardingKeyboxSealedCsekDao = onboardingKeyboxSealedCsekDao,
    onboardingKeyboxHardwareKeysDao = onboardingKeyboxHwAuthPublicKeyDao,
    uuidGenerator = appComponent.uuidGenerator,
    onboardingAppKeyKeystore = appComponent.onboardingAppKeyKeystore,
    liteToFullAccountUpgrader = liteToFullAccountUpgrader,
    debugOptionsService = appComponent.debugOptionsService
  )

  val onboardKeyboxDataStateMachine =
    OnboardKeyboxDataStateMachineImpl(
      onboardingKeyboxSealedCsekDao = onboardingKeyboxSealedCsekDao,
      onboardingKeyboxStepStateDao = onboardingKeyboxStepStateDao,
      debugOptionsService = appComponent.debugOptionsService
    )

  val activateFullAccountDataStateMachine =
    ActivateFullAccountDataStateMachineImpl(
      eventTracker = appComponent.eventTracker,
      gettingStartedTaskDao = gettingStartedTaskDao,
      keyboxDao = appComponent.keyboxDao,
      onboardingKeyboxStepStateDao = onboardingKeyboxStepStateDao,
      onboardingF8eClient = onboardingF8eClient,
      onboardingAppKeyKeystore = appComponent.onboardingAppKeyKeystore,
      onboardingKeyboxHardwareKeysDao = onboardingKeyboxHwAuthPublicKeyDao
    )

  val createFullAccountDataStateMachine =
    CreateFullAccountDataStateMachineImpl(
      activateFullAccountDataStateMachine = activateFullAccountDataStateMachine,
      createKeyboxDataStateMachine = createKeyboxDataStateMachine,
      onboardKeyboxDataStateMachine = onboardKeyboxDataStateMachine,
      appDataDeleter = appDataDeleter,
      onboardingKeyboxStepStateDao = onboardingKeyboxStepStateDao
    )

  val lostAppRecoveryDataStateMachine =
    LostAppRecoveryDataStateMachineImpl(
      recoveryInProgressDataStateMachine = recoveryInProgressDataStateMachine,
      lostAppRecoveryHaveNotStartedDataStateMachine = lostAppRecoveryHaveNotStartedDataStateMachine
    )

  val noActiveAccountDataStateMachine =
    NoActiveAccountDataStateMachineImpl(
      createFullAccountDataStateMachine = createFullAccountDataStateMachine,
      lostAppRecoveryDataStateMachine = lostAppRecoveryDataStateMachine,
      eventTracker = appComponent.eventTracker,
      keyboxDao = appComponent.keyboxDao
    )

  val initiatingLostHardwareRecoveryDataStateMachine =
    InitiatingLostHardwareRecoveryDataStateMachineImpl(
      appKeysGenerator = appKeysGenerator,
      delayer = appComponent.delayer,
      lostHardwareRecoveryStarter = delayNotifyHardwareRecoveryStarter,
      recoveryNotificationVerificationDataStateMachine = recoveryNotificationVerificationDataStateMachine,
      cancelDelayNotifyRecoveryF8eClient = cancelDelayNotifyRecoveryF8eClient
    )

  val lostHardwareRecoveryDataStateMachine =
    LostHardwareRecoveryDataStateMachineImpl(
      initiatingLostHardwareRecoveryDataStateMachine = initiatingLostHardwareRecoveryDataStateMachine,
      recoveryInProgressDataStateMachine = recoveryInProgressDataStateMachine
    )

  val fullAccountCloudBackupRestorationUiStateMachine =
    FullAccountCloudBackupRestorationUiStateMachineImpl(
      appSpendingWalletProvider = appComponent.appSpendingWalletProvider,
      backupRestorer = cloudBackupRestorer,
      eventTracker = appComponent.eventTracker,
      deviceTokenManager = appComponent.deviceTokenManager,
      csekDao = csekDao,
      accountAuthenticator = appComponent.accountAuthenticator,
      authTokenDao = appComponent.authTokenDao,
      appPrivateKeyDao = appComponent.appPrivateKeyDao,
      nfcSessionUIStateMachine = nfcSessionUIStateMachine,
      keyboxDao = appComponent.keyboxDao,
      recoverySyncer = recoverySyncer,
      deviceInfoProvider = appComponent.deviceInfoProvider,
      uuidGenerator = appComponent.uuidGenerator,
      cloudBackupDao = cloudBackupDao,
      recoveryChallengeStateMachine = recoveryChallengeUiStateMachine,
      socRecChallengeRepository = socRecChallengeRepository,
      socialRelationshipsRepository = socRecRelationshipsRepository,
      postSocRecTaskRepository = postSocRecTaskRepository,
      socRecStartedChallengeDao = socRecPendingChallengeDao,
      fullAccountAuthKeyRotationService = fullAccountAuthKeyRotationService
    )

  val lostAppRecoveryHaveNotStartedUiStateMachine =
    LostAppRecoveryHaveNotStartedUiStateMachineImpl(
      initiatingLostAppRecoveryUiStateMachine = initiatingLostAppRecoveryUiStateMachineImpl,
      fullAccountCloudBackupRestorationUiStateMachine = fullAccountCloudBackupRestorationUiStateMachine
    )

  val recoveringKeyboxUiStateMachine =
    LostAppRecoveryUiStateMachineImpl(
      lostAppRecoveryHaveNotStartedDataStateMachine = lostAppRecoveryHaveNotStartedUiStateMachine,
      recoveryInProgressUiStateMachine = recoveryInProgressUiStateMachine
    )

  val cloudBackupRefresher =
    TrustedContactCloudBackupRefresherImpl(
      socRecRelationshipsRepository = socRecRelationshipsRepository,
      cloudBackupDao = cloudBackupDao,
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      cloudBackupRepository = cloudBackupRepository,
      fullAccountCloudBackupCreator = fullAccountCloudBackupCreator,
      eventTracker = appComponent.eventTracker,
      clock = appComponent.clock
    )

  val hasActiveFullAccountDataStateMachine = HasActiveFullAccountDataStateMachineImpl(
    mobilePayDataStateMachine = mobilePayDataStateMachine,
    fullAccountTransactionsDataStateMachine = fullAccountTransactionsDataStateMachine,
    lostHardwareRecoveryDataStateMachine = lostHardwareRecoveryDataStateMachine,
    appSpendingWalletProvider = appComponent.appSpendingWalletProvider,
    exchangeRateSyncer = exchangeRateSyncer,
    trustedContactCloudBackupRefresher = cloudBackupRefresher,
    fullAccountAuthKeyRotationService = fullAccountAuthKeyRotationService,
    trustedContactKeyAuthenticator = trustedContactKeyAuthenticator
  )

  val noLongerRecoveringUiStateMachine = NoLongerRecoveringUiStateMachineImpl()
  val noLongerRecoveringDataStateMachine =
    NoLongerRecoveringDataStateMachineImpl(
      recoveryDao = appComponent.recoveryDao
    )

  val someoneElseIsRecoveringUiStateMachine =
    SomeoneElseIsRecoveringUiStateMachineImpl(
      proofOfPossessionNfcStateMachine = proofOfPossessionNfcStateMachine,
      recoveryNotificationVerificationUiStateMachine = recoveryNotificationVerificationUiStateMachine
    )

  val someoneElseIsRecoveringDataStateMachine =
    SomeoneElseIsRecoveringDataStateMachineImpl(
      cancelDelayNotifyRecoveryF8eClient = cancelDelayNotifyRecoveryF8eClient,
      recoveryNotificationVerificationDataStateMachine = recoveryNotificationVerificationDataStateMachine,
      recoverySyncer = recoverySyncer
    )

  val hasActiveLiteAccountDataStateMachine =
    HasActiveLiteAccountDataStateMachineImpl(
      createFullAccountDataStateMachine = createFullAccountDataStateMachine,
      keyboxDao = appComponent.keyboxDao
    )

  val accountDataStateMachine = AccountDataStateMachineImpl(
    hasActiveFullAccountDataStateMachine = hasActiveFullAccountDataStateMachine,
    hasActiveLiteAccountDataStateMachine = hasActiveLiteAccountDataStateMachine,
    noActiveAccountDataStateMachine = noActiveAccountDataStateMachine,
    accountRepository = appComponent.accountRepository,
    recoverySyncer = recoverySyncer,
    noLongerRecoveringDataStateMachine = noLongerRecoveringDataStateMachine,
    someoneElseIsRecoveringDataStateMachine = someoneElseIsRecoveringDataStateMachine,
    recoverySyncFrequency = appComponent.recoverySyncFrequency,
    debugOptionsService = appComponent.debugOptionsService
  )

  val appDataStateMachine = AppDataStateMachineImpl(
    featureFlagService = appComponent.featureFlagService,
    accountDataStateMachine = accountDataStateMachine,
    fiatCurrencyRepository = fiatCurrencyRepository,
    debugOptionsService = appComponent.debugOptionsService
  )

  val liteAccountCreator =
    LiteAccountCreatorImpl(
      accountAuthenticator = appComponent.accountAuthenticator,
      accountRepository = appComponent.accountRepository,
      appKeysGenerator = appComponent.appKeysGenerator,
      authTokenDao = appComponent.authTokenDao,
      createLiteAccountF8eClient = createAccountF8eClient
    )

  val liteAccountCloudBackupCreator =
    LiteAccountCloudBackupCreatorImpl(
      socRecKeysRepository = socRecKeysRepository,
      appPrivateKeyDao = appComponent.appPrivateKeyDao
    )

  val liteAccountCloudSignInAndBackupUiStateMachine =
    LiteAccountCloudSignInAndBackupUiStateMachineImpl(
      cloudBackupRepository = cloudBackupRepository,
      cloudSignInUiStateMachine = cloudSignInUiStateMachine,
      liteAccountCloudBackupCreator = liteAccountCloudBackupCreator,
      deviceInfoProvider = appComponent.deviceInfoProvider,
      eventTracker = appComponent.eventTracker,
      rectifiableErrorHandlingUiStateMachine = rectifiableErrorHandlingUiStateMachine,
      inAppBrowserNavigator = inAppBrowserNavigator
    )

  val createLiteAccountUiStateMachine =
    CreateLiteAccountUiStateMachineImpl(
      liteAccountCreator = liteAccountCreator,
      trustedContactEnrollmentUiStateMachine = trustedContactEnrollmentUiStateMachine,
      socRecRelationshipsRepository = socRecRelationshipsRepository,
      liteAccountCloudSignInAndBackupUiStateMachine = liteAccountCloudSignInAndBackupUiStateMachine,
      deviceInfoProvider = appComponent.deviceInfoProvider,
      eventTracker = appComponent.eventTracker,
      debugOptionsService = appComponent.debugOptionsService
    )

  val liteMoneyHomeUiStateMachine =
    LiteMoneyHomeUiStateMachineImpl(
      inAppBrowserNavigator = inAppBrowserNavigator,
      viewingProtectedCustomerUiStateMachine = viewingProtectedCustomerUiStateMachine,
      helpingWithRecoveryUiStateMachine = helpingWithRecoveryUiStateMachine
    )
  val liteListingTrustedContactsUiStateMachine =
    LiteListingTrustedContactsUiStateMachineImpl(
      viewingProtectedCustomerUiStateMachine = viewingProtectedCustomerUiStateMachine
    )
  val liteTrustedContactManagementUiStateMachine =
    LiteTrustedContactManagementUiStateMachineImpl(
      liteListingTrustedContactsUiStateMachine = liteListingTrustedContactsUiStateMachine,
      trustedContactEnrollmentUiStateMachine = trustedContactEnrollmentUiStateMachine,
      helpingWithRecoveryUiStateMachine = helpingWithRecoveryUiStateMachine
    )
  val liteSettingsHomeUiStateMachine =
    LiteSettingsHomeUiStateMachineImpl(
      appVariant = appComponent.appVariant,
      currencyPreferenceUiStateMachine = currencyPreferenceUiStateMachine,
      helpCenterUiStateMachine = helpCenterUiStateMachine,
      liteTrustedContactManagementUiStateMachine = liteTrustedContactManagementUiStateMachine,
      settingsListUiStateMachine = settingsListUiStateMachine,
      feedbackUiStateMachine = feedbackUiStateMachine,
      debugMenuStateMachine = debugMenuStateMachine
    )
  val liteHomeUiStateMachine =
    LiteHomeUiStateMachineImpl(
      homeStatusBannerUiStateMachine = homeStatusBannerUiStateMachine,
      liteMoneyHomeUiStateMachine = liteMoneyHomeUiStateMachine,
      liteSettingsHomeUiStateMachine = liteSettingsHomeUiStateMachine,
      liteTrustedContactManagementUiStateMachine = liteTrustedContactManagementUiStateMachine,
      socRecRelationshipsRepository = socRecRelationshipsRepository,
      eventTracker = appComponent.eventTracker
    )

  val gettingStartedRoutingStateMachine =
    GettingStartedRoutingStateMachineImpl(
      accessCloudBackupUiStateMachine = accessCloudBackupUiStateMachine
    )

  val liteAccountCloudBackupRestorationUiStateMachine =
    LiteAccountCloudBackupRestorationUiStateMachineImpl(
      liteAccountCloudBackupRestorer = liteAccountCloudBackupRestorer
    )

  val emergencyAccessKitRecoveryUiStateMachine =
    EmergencyAccessKitRecoveryUiStateMachineImpl(
      clipboard = clipboard,
      payloadDecoder = EmergencyAccessKitPayloadDecoderImpl,
      permissionUiStateMachine = permissionStateMachine,
      emergencyAccessPayloadRestorer = EmergencyAccessPayloadRestorerImpl(
        csekDao = csekDao,
        symmetricKeyEncryptor = symmetricKeyEncryptor,
        appPrivateKeyDao = appComponent.appPrivateKeyDao
      ),
      csekDao = csekDao,
      keyboxDao = appComponent.keyboxDao,
      nfcSessionUIStateMachine = nfcSessionUIStateMachine,
      uuidGenerator = appComponent.uuidGenerator,
      debugOptionsService = appComponent.debugOptionsService
    )

  override val appUiStateMachine =
    AppUiStateMachineImpl(
      appVariant = appComponent.appVariant,
      delayer = appComponent.delayer,
      debugMenuStateMachine = debugMenuStateMachine,
      eventTracker = appComponent.eventTracker,
      lostAppRecoveryUiStateMachine = recoveringKeyboxUiStateMachine,
      homeUiStateMachine = homeUiStateMachine,
      liteHomeUiStateMachine = liteHomeUiStateMachine,
      chooseAccountAccessUiStateMachine = chooseAccountAccessUiStateMachine,
      createAccountUiStateMachine = createAccountUiStateMachine,
      appDataStateMachine = appDataStateMachine,
      noLongerRecoveringUiStateMachine = noLongerRecoveringUiStateMachine,
      someoneElseIsRecoveringUiStateMachine = someoneElseIsRecoveringUiStateMachine,
      gettingStartedRoutingStateMachine = gettingStartedRoutingStateMachine,
      createLiteAccountUiStateMachine = createLiteAccountUiStateMachine,
      liteAccountCloudBackupRestorationUiStateMachine = liteAccountCloudBackupRestorationUiStateMachine,
      emergencyAccessKitRecoveryUiStateMachine = emergencyAccessKitRecoveryUiStateMachine,
      authKeyRotationUiStateMachine = rotateAuthUIStateMachine,
      appWorkerExecutor = appComponent.appWorkerExecutor,
      resettingDeviceUiStateMachine = resettingDeviceUiStateMachine
    )

  override val biometricPromptUiStateMachine = BiometricPromptUiStateMachineImpl(
    appSessionManager = appComponent.appSessionManager,
    biometricPrompter = biometricPrompter,
    biometricPreference = appComponent.biometricPreference
  )
}
