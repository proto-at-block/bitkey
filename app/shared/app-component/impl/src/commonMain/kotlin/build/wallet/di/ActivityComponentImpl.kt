// We expose all member fields for access in integration tests.
@file:Suppress("MemberVisibilityCanBePrivate")

package build.wallet.di

import build.wallet.amount.*
import build.wallet.auth.*
import build.wallet.bitcoin.address.BitcoinAddressParserImpl
import build.wallet.bitcoin.explorer.BitcoinExplorerImpl
import build.wallet.bitcoin.fees.BitcoinTransactionBaseCalculatorImpl
import build.wallet.bitcoin.fees.BitcoinTransactionFeeEstimatorImpl
import build.wallet.bitcoin.invoice.BitcoinInvoiceUrlEncoderImpl
import build.wallet.bitcoin.invoice.PaymentDataParserImpl
import build.wallet.bitcoin.lightning.LightningInvoiceParser
import build.wallet.bitcoin.sync.ElectrumConfigServiceImpl
import build.wallet.bitcoin.transactions.BitcoinTransactionBumpabilityCheckerImpl
import build.wallet.bitcoin.transactions.BitcoinTransactionSweepCheckerImpl
import build.wallet.bitcoin.transactions.FeeBumpAllowShrinkingCheckerImpl
import build.wallet.bitcoin.transactions.TransactionPriorityPreferenceImpl
import build.wallet.bootstrap.LoadAppServiceImpl
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
import build.wallet.datadog.DatadogRumMonitor
import build.wallet.email.EmailValidatorImpl
import build.wallet.emergencyaccesskit.*
import build.wallet.f8e.configuration.GetBdkConfigurationF8eClientImpl
import build.wallet.f8e.demo.DemoModeF8eClientImpl
import build.wallet.f8e.onboarding.*
import build.wallet.f8e.onboarding.frost.ActivateSpendingDescriptorF8eClientImpl
import build.wallet.f8e.onboarding.frost.ContinueDistributedKeygenF8eClientImpl
import build.wallet.f8e.onboarding.frost.InitiateDistributedKeygenF8eClientImpl
import build.wallet.f8e.partnerships.*
import build.wallet.f8e.recovery.*
import build.wallet.f8e.support.SupportTicketF8eClientImpl
import build.wallet.home.GettingStartedTaskDaoImpl
import build.wallet.home.HomeUiBottomSheetDaoImpl
import build.wallet.inappsecurity.HideBalancePreferenceImpl
import build.wallet.inappsecurity.MoneyHomeHiddenStatusProviderImpl
import build.wallet.keybox.AppDataDeleterImpl
import build.wallet.keybox.CloudBackupDeleterImpl
import build.wallet.logging.log
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
import build.wallet.recovery.sweep.SweepGeneratorImpl
import build.wallet.recovery.sweep.SweepServiceImpl
import build.wallet.relationships.RelationshipsCryptoFake
import build.wallet.relationships.RelationshipsKeysDaoImpl
import build.wallet.relationships.RelationshipsKeysRepository
import build.wallet.statemachine.account.ChooseAccountAccessUiStateMachineImpl
import build.wallet.statemachine.account.create.CreateSoftwareWalletUiStateMachineImpl
import build.wallet.statemachine.account.create.full.CreateAccountUiStateMachineImpl
import build.wallet.statemachine.account.create.full.OnboardKeyboxUiStateMachineImpl
import build.wallet.statemachine.account.create.full.OverwriteFullAccountCloudBackupUiStateMachineImpl
import build.wallet.statemachine.account.create.full.ReplaceWithLiteAccountRestoreUiStateMachineImpl
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachineImpl
import build.wallet.statemachine.account.create.full.keybox.create.CreateKeyboxUiStateMachineImpl
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachineImpl
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
import build.wallet.statemachine.data.keybox.*
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringDataStateMachineImpl
import build.wallet.statemachine.data.recovery.inprogress.F8eSpendingKeyRotatorImpl
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachineImpl
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryDataStateMachineImpl
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryHaveNotStartedDataStateMachineImpl
import build.wallet.statemachine.data.recovery.lostapp.initiate.InitiatingLostAppRecoveryDataStateMachineImpl
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryDataStateMachineImpl
import build.wallet.statemachine.data.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryDataStateMachineImpl
import build.wallet.statemachine.data.recovery.sweep.SweepDataStateMachineImpl
import build.wallet.statemachine.demo.DemoModeCodeEntryUiStateMachineImpl
import build.wallet.statemachine.demo.DemoModeConfigUiStateMachineImpl
import build.wallet.statemachine.dev.*
import build.wallet.statemachine.dev.analytics.AnalyticsOptionsUiStateMachineImpl
import build.wallet.statemachine.dev.analytics.AnalyticsUiStateMachineImpl
import build.wallet.statemachine.dev.cloud.CloudDevOptionsStateMachine
import build.wallet.statemachine.dev.debug.NetworkingDebugConfigPickerUiStateMachineImpl
import build.wallet.statemachine.dev.featureFlags.*
import build.wallet.statemachine.dev.logs.LogsUiStateMachineImpl
import build.wallet.statemachine.dev.wallet.BitcoinWalletDebugUiStateMachineImpl
import build.wallet.statemachine.export.ExportToolsUiStateMachineImpl
import build.wallet.statemachine.fwup.FwupNfcSessionUiStateMachineImpl
import build.wallet.statemachine.fwup.FwupNfcUiStateMachineImpl
import build.wallet.statemachine.home.full.HomeUiStateMachineImpl
import build.wallet.statemachine.home.full.bottomsheet.CurrencyChangeMobilePayBottomSheetUpdaterImpl
import build.wallet.statemachine.home.full.bottomsheet.HomeUiBottomSheetStateMachineImpl
import build.wallet.statemachine.home.lite.LiteHomeUiStateMachineImpl
import build.wallet.statemachine.inheritance.InheritanceManagementUiStateMachineImpl
import build.wallet.statemachine.inheritance.InviteBeneficiaryUiStateMachineImpl
import build.wallet.statemachine.inheritance.claims.start.StartClaimUiStateMachineImpl
import build.wallet.statemachine.limit.SetSpendingLimitUiStateMachineImpl
import build.wallet.statemachine.limit.picker.SpendingLimitPickerUiStateMachineImpl
import build.wallet.statemachine.money.amount.MoneyAmountEntryUiStateMachineImpl
import build.wallet.statemachine.money.amount.MoneyAmountUiStateMachineImpl
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiStateMachineImpl
import build.wallet.statemachine.money.currency.AppearancePreferenceUiStateMachineImpl
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
import build.wallet.statemachine.partnerships.sell.PartnershipsSellConfirmationUiStateMachineImpl
import build.wallet.statemachine.partnerships.sell.PartnershipsSellOptionsUiStateMachineImpl
import build.wallet.statemachine.partnerships.sell.PartnershipsSellUiStateMachineImpl
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
import build.wallet.statemachine.send.amountentry.TransferCardUiStateMachineImpl
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
import build.wallet.statemachine.transactions.TransactionDetailsUiStateMachineImpl
import build.wallet.statemachine.transactions.TransactionItemUiStateMachineImpl
import build.wallet.statemachine.transactions.TransactionListUiStateMachineImpl
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiStateMachineImpl
import build.wallet.statemachine.utxo.UtxoConsolidationUiStateMachineImpl
import build.wallet.support.SupportTicketFormValidatorImpl
import build.wallet.support.SupportTicketRepositoryImpl
import build.wallet.time.*

/**
 * [ActivityComponent] that provides real implementations.
 *
 * Should be initialized as a singleton and scoped to the application's lifecycle.
 */
@Suppress("LargeClass")
class ActivityComponentImpl(
  val appComponent: AppComponentImpl,
  val cloudKeyValueStore: CloudKeyValueStore,
  val cloudFileStore: CloudFileStore,
  cloudSignInUiStateMachine: CloudSignInUiStateMachine,
  cloudDevOptionsStateMachine: CloudDevOptionsStateMachine,
  val cloudStoreAccountRepository: CloudStoreAccountRepository,
  datadogRumMonitor: DatadogRumMonitor,
  lightningInvoiceParser: LightningInvoiceParser,
  sharingManager: SharingManager,
  systemSettingsLauncher: SystemSettingsLauncher,
  inAppBrowserNavigator: InAppBrowserNavigator,
  nfcCommandsProvider: NfcCommandsProvider,
  nfcSessionProvider: NfcSessionProvider,
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

  val relationshipsCryptoFake = RelationshipsCryptoFake(
    messageSigner = appComponent.messageSigner,
    signatureVerifier = appComponent.signatureVerifier,
    appPrivateKeyDao = appComponent.appPrivateKeyDao
  )

  val relationshipsKeysDao =
    RelationshipsKeysDaoImpl(
      databaseProvider = appComponent.bitkeyDatabaseProvider,
      appPrivateKeyDao = appComponent.appPrivateKeyDao
    )

  val relationshipsKeysRepository = RelationshipsKeysRepository(appComponent.relationshipsCrypto, relationshipsKeysDao)

  private val fullAccountFieldsCreator =
    FullAccountFieldsCreatorImpl(
      appPrivateKeyDao = appComponent.appPrivateKeyDao,
      csekDao = csekDao,
      symmetricKeyEncryptor = appComponent.symmetricKeyEncryptor,
      relationshipsCrypto = appComponent.relationshipsCrypto
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
            appComponent.firmwareTelemetryUploader,
            appComponent.firmwareCommsLogBuffer,
            appComponent.firmwareCommsLoggingFeatureFlag
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
      nfcTransactor = nfcTransactor,
      asyncNfcSigningFeatureFlag = appComponent.asyncNfcSigningFeatureFlag,
      progressSpinnerForLongNfcOpsFeatureFlag = appComponent.progressSpinnerForLongNfcOpsFeatureFlag
    )

  val completeDelayNotifyF8eClient = CompleteDelayNotifyF8eClientImpl(appComponent.f8eHttpClient)

  val csekGenerator = CsekGeneratorImpl(
    symmetricKeyGenerator = appComponent.symmetricKeyGenerator
  )

  val paymentDataParser =
    PaymentDataParserImpl(
      bip21InvoiceEncoder = bitcoinInvoiceUrlEncoder,
      bitcoinAddressParser = bitcoinAddressParser,
      lightningInvoiceParser = lightningInvoiceParser
    )

  val bitcoinQrCodeScanStateMachine =
    BitcoinQrCodeScanUiStateMachineImpl(
      paymentDataParser = paymentDataParser,
      transactionsService = appComponent.transactionsService,
      utxoConsolidationFeatureFlag = appComponent.utxoConsolidationFeatureFlag
    )

  val bitcoinAddressRecipientStateMachine =
    BitcoinAddressRecipientUiStateMachineImpl(
      paymentDataParser = paymentDataParser,
      keysetWalletProvider = appComponent.keysetWalletProvider,
      utxoConsolidationFeatureFlag = appComponent.utxoConsolidationFeatureFlag
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
    symmetricKeyEncryptor = appComponent.symmetricKeyEncryptor,
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

  val fullAccountCloudBackupCreator =
    FullAccountCloudBackupCreatorImpl(
      appPrivateKeyDao = appComponent.appPrivateKeyDao,
      fullAccountFieldsCreator = fullAccountFieldsCreator,
      relationshipsKeysRepository = relationshipsKeysRepository,
      relationshipsService = appComponent.relationshipsService
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
          currencyConverter = appComponent.currencyConverter,
          dateTimeFormatter = dateTimeFormatter,
          timeZoneProvider = timeZoneProvider,
          moneyDisplayFormatter = moneyDisplayFormatter
        )
    )

  val moneyAmountUiStateMachine =
    MoneyAmountUiStateMachineImpl(
      currencyConverter = appComponent.currencyConverter,
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
    currencyConverter = appComponent.currencyConverter,
    moneyAmountEntryUiStateMachine = moneyAmountEntryUiStateMachine,
    decimalNumberCreator = decimalNumberCreator,
    doubleFormatter = doubleFormatter
  )

  val transferCardUiStateMachine = TransferCardUiStateMachineImpl(
    mobilePayRevampFeatureFlag = appComponent.mobilePayRevampFeatureFlag,
    appFunctionalityService = appComponent.appFunctionalityService,
    mobilePayService = appComponent.mobilePayService
  )

  val transferAmountEntryUiStateMachine = TransferAmountEntryUiStateMachineImpl(
    currencyConverter = appComponent.currencyConverter,
    moneyCalculatorUiStateMachine = moneyCalculatorUiStateMachine,
    moneyDisplayFormatter = moneyDisplayFormatter,
    fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository,
    transactionsService = appComponent.transactionsService,
    transferCardUiStateMachine = transferCardUiStateMachine,
    appFunctionalityService = appComponent.appFunctionalityService
  )

  val transactionDetailsCardUiStateMachine = TransactionDetailsCardUiStateMachineImpl(
    currencyConverter = appComponent.currencyConverter,
    fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository,
    moneyDisplayFormatter = moneyDisplayFormatter
  )

  val transferInitiatedUiStateMachine = TransferInitiatedUiStateMachineImpl(
    transactionDetailsCardUiStateMachine = transactionDetailsCardUiStateMachine
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
      currencyConverter = appComponent.currencyConverter,
      mobilePayFiatConfigService = appComponent.mobilePayFiatConfigService,
      exchangeRateService = appComponent.exchangeRateService,
      moneyDisplayFormatter = moneyDisplayFormatter,
      proofOfPossessionNfcStateMachine = proofOfPossessionNfcStateMachine,
      moneyCalculatorUiStateMachine = moneyCalculatorUiStateMachine,
      fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository,
      mobilePayRevampFeatureFlag = appComponent.mobilePayRevampFeatureFlag
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
      verificationCodeInputStateMachine = verificationCodeInputStateMachine,
      notificationTouchpointService = appComponent.notificationTouchpointService
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

  private val cloudBackupV2Restorer = CloudBackupV2RestorerImpl(
    csekDao = csekDao,
    symmetricKeyEncryptor = appComponent.symmetricKeyEncryptor,
    appPrivateKeyDao = appComponent.appPrivateKeyDao,
    relationshipsKeysDao = relationshipsKeysDao,
    uuidGenerator = appComponent.uuidGenerator
  )

  val cloudBackupRestorer = FullAccountCloudBackupRestorerImpl(
    cloudBackupV2Restorer = cloudBackupV2Restorer
  )

  val challengeCodeFormatter = ChallengeCodeFormatterImpl()

  val recoveryChallengeUiStateMachine = RecoveryChallengeUiStateMachineImpl(
    crypto = appComponent.relationshipsCrypto,
    enableNotificationsUiStateMachine = enableNotificationsUiStateMachine,
    deviceTokenManager = appComponent.deviceTokenManager,
    challengeCodeFormatter = challengeCodeFormatter,
    permissionChecker = appComponent.permissionChecker
  )

  val sweepGenerator =
    SweepGeneratorImpl(
      listKeysetsF8eClient = appComponent.listKeysetsF8eClient,
      bitcoinFeeRateEstimator = appComponent.bitcoinFeeRateEstimator,
      appPrivateKeyDao = appComponent.appPrivateKeyDao,
      keysetWalletProvider = appComponent.keysetWalletProvider,
      registerWatchAddressProcessor = appComponent.registerWatchAddressProcessor
    )

  val sweepService = SweepServiceImpl(
    accountService = appComponent.accountService,
    appSessionManager = appComponent.appSessionManager,
    promptSweepFeatureFlag = appComponent.promptSweepFeatureFlag,
    sweepGenerator = sweepGenerator
  )

  val sweepDataStateMachine = SweepDataStateMachineImpl(
    sweepService = sweepService,
    mobilePaySigningF8eClient = appComponent.mobilePaySigningF8eClient,
    appSpendingWalletProvider = appComponent.appSpendingWalletProvider,
    transactionsService = appComponent.transactionsService
  )

  val sweepUiStateMachine = SweepUiStateMachineImpl(
    nfcSessionUIStateMachine = nfcSessionUIStateMachine,
    moneyAmountUiStateMachine = moneyAmountUiStateMachine,
    fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository,
    sweepDataStateMachine = sweepDataStateMachine,
    inAppBrowserNavigator = inAppBrowserNavigator
  )

  val completingRecoveryUiStateMachine =
    CompletingRecoveryUiStateMachineImpl(
      proofOfPossessionNfcStateMachine = proofOfPossessionNfcStateMachine,
      fullAccountCloudSignInAndBackupUiStateMachine = fullAccountCloudSignInAndBackupUiStateMachine,
      sweepUiStateMachine = sweepUiStateMachine,
      nfcSessionUIStateMachine = nfcSessionUIStateMachine,
      postSocRecTaskRepository = appComponent.postSocRecTaskRepository,
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
      sharingManager = sharingManager,
      relationshipsService = appComponent.relationshipsService
    )

  val removeTrustedContactUiStateMachine =
    RemoveTrustedContactUiStateMachineImpl(
      proofOfPossessionNfcStateMachine = proofOfPossessionNfcStateMachine,
      clock = appComponent.clock,
      relationshipsService = appComponent.relationshipsService
    )

  val viewingInvitationUiStateMachine =
    ViewingInvitationUiStateMachineImpl(
      removeTrustedContactsUiStateMachine = removeTrustedContactUiStateMachine,
      reinviteTrustedContactUiStateMachine = reinviteTrustedContactUiStateMachine,
      sharingManager = sharingManager,
      clock = appComponent.clock,
      inviteCodeLoader = appComponent.inviteCodeLoader
    )

  val viewingRecoveryContactUiStateMachine =
    ViewingRecoveryContactUiStateMachineImpl(
      removeTrustedContactUiStateMachine = removeTrustedContactUiStateMachine
    )

  val viewingProtectedCustomerUiStateMachine = ViewingProtectedCustomerUiStateMachineImpl(
    relationshipsService = appComponent.relationshipsService
  )

  val helpingWithRecoveryUiStateMachine = HelpingWithRecoveryUiStateMachineImpl(
    delayer = appComponent.delayer,
    socialChallengeVerifier = appComponent.socialChallengeVerifier,
    relationshipsKeysRepository = relationshipsKeysRepository
  )

  val listingTcsUiStateMachine =
    ListingTrustedContactsUiStateMachineImpl(
      viewingRecoveryContactUiStateMachine = viewingRecoveryContactUiStateMachine,
      viewingInvitationUiStateMachine = viewingInvitationUiStateMachine,
      viewingProtectedCustomerUiStateMachine = viewingProtectedCustomerUiStateMachine,
      helpingWithRecoveryUiStateMachine = helpingWithRecoveryUiStateMachine,
      clock = appComponent.clock,
      socRecService = appComponent.socRecService
    )

  val trustedContactEnrollmentUiStateMachine =
    TrustedContactEnrollmentUiStateMachineImpl(
      deviceInfoProvider = appComponent.deviceInfoProvider,
      relationshipsKeysRepository = relationshipsKeysRepository,
      eventTracker = appComponent.eventTracker,
      relationshipsService = appComponent.relationshipsService
    )

  val trustedContactManagementUiStateMachine =
    TrustedContactManagementUiStateMachineImpl(
      addingTrustedContactUiStateMachine = addingTcsUiStateMachine,
      listingTrustedContactsUiStateMachine = listingTcsUiStateMachine,
      trustedContactEnrollmentUiStateMachine = trustedContactEnrollmentUiStateMachine,
      deviceInfoProvider = appComponent.deviceInfoProvider,
      relationshipsService = appComponent.relationshipsService
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

  val notificationPreferencesSetupUiStateMachine =
    NotificationPreferencesSetupUiStateMachineImpl(
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

  val hideBalancePreference = HideBalancePreferenceImpl(
    appCoroutineScope = appComponent.appCoroutineScope,
    databaseProvider = appComponent.bitkeyDatabaseProvider,
    eventTracker = appComponent.eventTracker
  )

  val appearancePreferenceUiStateMachine =
    AppearancePreferenceUiStateMachineImpl(
      bitcoinDisplayPreferenceRepository = appComponent.bitcoinDisplayPreferenceRepository,
      fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository,
      eventTracker = appComponent.eventTracker,
      currencyConverter = appComponent.currencyConverter,
      fiatCurrenciesService = appComponent.fiatCurrenciesService,
      moneyDisplayFormatter = moneyDisplayFormatter,
      hideBalancePreference = hideBalancePreference,
      bitcoinPriceCardPreference = appComponent.bitcoinPriceCardPreference,
      transactionsService = appComponent.transactionsService
    )

  val onboardKeyboxUiStateMachine =
    OnboardKeyboxUiStateMachineImpl(
      fullAccountCloudSignInAndBackupUiStateMachine = fullAccountCloudSignInAndBackupUiStateMachine,
      notificationPreferencesSetupUiStateMachine = notificationPreferencesSetupUiStateMachine
    )

  val deleteOnboardingFullAccountF8eClient =
    DeleteOnboardingFullAccountF8eClientImpl(appComponent.f8eHttpClient)

  val onboardingFullAccountDeleter =
    OnboardingFullAccountDeleterImpl(
      accountService = appComponent.accountService,
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
      relationshipsKeysDao = relationshipsKeysDao,
      accountAuthenticator = appComponent.accountAuthenticator,
      authTokenDao = appComponent.authTokenDao,
      cloudBackupDao = cloudBackupDao,
      accountService = appComponent.accountService
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
      fwupNfcSessionUiStateMachine = fwupNfcSessionUiStateMachine,
      inAppBrowserNavigator = inAppBrowserNavigator
    )

  val coachmarkService = CoachmarkServiceImpl(
    coachmarkDao = CoachmarkDaoImpl(appComponent.bitkeyDatabaseProvider),
    accountService = appComponent.accountService,
    coachmarkVisibilityDecider = CoachmarkVisibilityDecider(
      clock = appComponent.clock
    ),
    coachmarksGlobalFeatureFlag = appComponent.coachmarksGlobalFeatureFlag,
    eventTracker = appComponent.eventTracker,
    clock = appComponent.clock
  )

  val infoOptionsStateMachine =
    InfoOptionsUiStateMachineImpl(
      appVariant = appComponent.appVariant,
      accountService = appComponent.accountService,
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
    fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository,
    mobilePayService = appComponent.mobilePayService
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
      fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository,
      mobilePayService = appComponent.mobilePayService,
      mobilePayRevampFeatureFlag = appComponent.mobilePayRevampFeatureFlag
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
      currencyConverter = appComponent.currencyConverter,
      moneyDisplayFormatter = moneyDisplayFormatter
    )

  val feeOptionListUiStateMachine = FeeOptionListUiStateMachineImpl(
    feeOptionUiStateMachine = feeOptionUiStateMachine,
    fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository,
    transactionsService = appComponent.transactionsService
  )

  val transferConfirmationStateMachine =
    TransferConfirmationUiStateMachineImpl(
      transactionDetailsCardUiStateMachine = transactionDetailsCardUiStateMachine,
      nfcSessionUIStateMachine = nfcSessionUIStateMachine,
      transactionPriorityPreference = transactionPriorityPreference,
      feeOptionListUiStateMachine = feeOptionListUiStateMachine,
      transactionsService = appComponent.transactionsService,
      mobilePayService = appComponent.mobilePayService,
      appFunctionalityService = appComponent.appFunctionalityService
    )

  val bitcoinTransactionFeeEstimator = BitcoinTransactionFeeEstimatorImpl(
    bitcoinFeeRateEstimator = appComponent.bitcoinFeeRateEstimator,
    transactionsService = appComponent.transactionsService,
    datadogRumMonitor = datadogRumMonitor
  )

  val feeSelectionStateMachine =
    FeeSelectionUiStateMachineImpl(
      bitcoinTransactionFeeEstimator = bitcoinTransactionFeeEstimator,
      transactionPriorityPreference = transactionPriorityPreference,
      feeOptionListUiStateMachine = feeOptionListUiStateMachine,
      transactionBaseCalculator = BitcoinTransactionBaseCalculatorImpl(),
      transactionsService = appComponent.transactionsService,
      accountService = appComponent.accountService
    )

  private val utxoConsolidationUiStateMachine = UtxoConsolidationUiStateMachineImpl(
    accountService = appComponent.accountService,
    utxoConsolidationService = appComponent.utxoConsolidationService,
    fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository,
    currencyConverter = appComponent.currencyConverter,
    moneyDisplayFormatter = moneyDisplayFormatter,
    nfcSessionUiStateMachine = nfcSessionUIStateMachine,
    dateTimeFormatter = dateTimeFormatter,
    timeZoneProvider = timeZoneProvider
  )

  val sendStateMachine = SendUiStateMachineImpl(
    bitcoinAddressRecipientUiStateMachine = bitcoinAddressRecipientStateMachine,
    transferAmountEntryUiStateMachine = transferAmountEntryUiStateMachine,
    transferConfirmationUiStateMachine = transferConfirmationStateMachine,
    transferInitiatedUiStateMachine = transferInitiatedUiStateMachine,
    bitcoinQrCodeUiScanStateMachine = bitcoinQrCodeScanStateMachine,
    permissionUiStateMachine = permissionStateMachine,
    feeSelectionUiStateMachine = feeSelectionStateMachine,
    exchangeRateService = appComponent.exchangeRateService,
    clock = appComponent.clock,
    fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository
  )

  val feeBumpAllowShrinkingChecker = FeeBumpAllowShrinkingCheckerImpl(
    allowShrinkingFeatureFlag = appComponent.speedUpAllowShrinkingFeatureFlag
  )

  val transactionDetailStateMachine =
    TransactionDetailsUiStateMachineImpl(
      bitcoinExplorer = bitcoinExplorer,
      bitcoinTransactionBumpabilityChecker = BitcoinTransactionBumpabilityCheckerImpl(
        sweepChecker = BitcoinTransactionSweepCheckerImpl(),
        feeBumpAllowShrinkingChecker = feeBumpAllowShrinkingChecker
      ),
      clock = appComponent.clock,
      currencyConverter = appComponent.currencyConverter,
      dateTimeFormatter = dateTimeFormatter,
      durationFormatter = durationFormatter,
      eventTracker = appComponent.eventTracker,
      feeBumpEnabled = appComponent.feeBumpIsAvailableFeatureFlag,
      moneyDisplayFormatter = moneyDisplayFormatter,
      timeZoneProvider = timeZoneProvider,
      fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository,
      feeBumpConfirmationUiStateMachine = FeeBumpConfirmationUiStateMachineImpl(
        transactionDetailsCardUiStateMachine = transactionDetailsCardUiStateMachine,
        exchangeRateService = appComponent.exchangeRateService,
        nfcSessionUIStateMachine = nfcSessionUIStateMachine,
        transferInitiatedUiStateMachine = transferInitiatedUiStateMachine,
        transactionsService = appComponent.transactionsService
      ),
      feeRateEstimator = appComponent.bitcoinFeeRateEstimator,
      inAppBrowserNavigator = inAppBrowserNavigator,
      transactionsService = appComponent.transactionsService
    )

  val homeUiBottomSheetDao = HomeUiBottomSheetDaoImpl(appComponent.bitkeyDatabaseProvider)

  val authKeyRotationAttemptDao = AuthKeyRotationAttemptDaoImpl(
    databaseProvider = appComponent.bitkeyDatabaseProvider
  )

  val appDataDeleter =
    AppDataDeleterImpl(
      appVariant = appComponent.appVariant,
      appPrivateKeyDao = appComponent.appPrivateKeyDao,
      accountService = appComponent.accountService,
      authTokenDao = appComponent.authTokenDao,
      gettingStartedTaskDao = gettingStartedTaskDao,
      keyboxDao = appComponent.keyboxDao,
      notificationTouchpointDao = appComponent.notificationTouchpointDao,
      onboardingKeyboxSealedCsekDao = onboardingKeyboxSealedCsekDao,
      onboardingKeyboxStepStateDao = onboardingKeyboxStepStateDao,
      onboardingKeyboxHardwareKeysDao = onboardingKeyboxHwAuthPublicKeyDao,
      mobilePayService = appComponent.mobilePayService,
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
      relationshipsKeysDao = relationshipsKeysDao,
      relationshipsService = appComponent.relationshipsService,
      socRecStartedChallengeDao = appComponent.socRecStartedChallengeDao,
      csekDao = csekDao,
      authKeyRotationAttemptDao = authKeyRotationAttemptDao,
      recoveryDao = appComponent.recoveryDao,
      authSignatureStatusProvider = appComponent.f8eAuthSignatureStatusProvider,
      hideBalancePreference = hideBalancePreference,
      biometricPreference = appComponent.biometricPreference,
      inheritanceClaimsDao = appComponent.inheritanceClaimsDao
    )

  val exchangeRateChartFetcher = ChartDataFetcherServiceImpl(
    exchangeRateF8eClient = appComponent.exchangeRateF8eClient,
    fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository
  )

  val deviceUpdateCardUiStateMachine =
    DeviceUpdateCardUiStateMachineImpl(
      eventTracker = appComponent.eventTracker,
      firmwareDataService = appComponent.firmwareDataService
    )

  val gettingStartedCardStateMachine =
    GettingStartedCardUiStateMachineImpl(
      appFunctionalityService = appComponent.appFunctionalityService,
      gettingStartedTaskDao = gettingStartedTaskDao,
      eventTracker = appComponent.eventTracker,
      transactionsService = appComponent.transactionsService,
      mobilePayService = appComponent.mobilePayService,
      socRecService = appComponent.socRecService,
      mobilePayRevampFeatureFlag = appComponent.mobilePayRevampFeatureFlag
    )

  val pendingInvitationsCardUiStateMachine =
    RecoveryContactCardsUiStateMachineImpl(
      clock = appComponent.clock,
      socRecService = appComponent.socRecService
    )

  val replaceHardwareCardUiStateMachine =
    SetupHardwareCardUiStateMachineImpl(
      postSocRecTaskRepository = appComponent.postSocRecTaskRepository,
      firmwareDataService = appComponent.firmwareDataService
    )

  val cloudBackupHealthCardUiStateMachine = CloudBackupHealthCardUiStateMachineImpl(
    appFunctionalityService = appComponent.appFunctionalityService,
    cloudBackupHealthRepository = cloudBackupHealthRepository
  )

  val startSweepCardUiStateMachine = StartSweepCardUiStateMachineImpl(
    sweepService = sweepService
  )

  val bitcoinPriceCardUiStateMachine = BitcoinPriceCardUiStateMachineImpl(
    timeZoneProvider = timeZoneProvider,
    bitcoinPriceCardPreference = appComponent.bitcoinPriceCardPreference,
    dateTimeFormatter = dateTimeFormatter,
    moneyDisplayFormatter = moneyDisplayFormatter,
    chartDataFetcherService = exchangeRateChartFetcher,
    fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository,
    currencyConverter = appComponent.currencyConverter
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
      exchangeRateService = appComponent.exchangeRateService,
      currencyConverter = appComponent.currencyConverter,
      bitcoinAddressService = appComponent.bitcoinAddressService
    )

  val getSaleQuoteListF8eClient = GetSaleQuoteListF8eClientImpl(
    appComponent.countryCodeGuesser,
    appComponent.f8eHttpClient
  )

  val partnershipsSellUiStateMachine = PartnershipsSellUiStateMachineImpl(
    partnershipsSellOptionsUiStateMachine = PartnershipsSellOptionsUiStateMachineImpl(
      getSaleQuoteListF8eClient = getSaleQuoteListF8eClient,
      getSellRedirectF8eClient = GetSellRedirectF8eClientImpl(appComponent.f8eHttpClient),
      partnershipsRepository = partnershipTransactionsStatusRepository,
      fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository,
      eventTracker = appComponent.eventTracker
    ),
    partnershipsSellConfirmationUiStateMachine = PartnershipsSellConfirmationUiStateMachineImpl(
      transferConfirmationUiStateMachine = transferConfirmationStateMachine,
      feeSelectionUiStateMachineImpl = feeSelectionStateMachine,
      partnershipsRepository = partnershipTransactionsStatusRepository,
      exchangeRateService = appComponent.exchangeRateService
    ),
    inAppBrowserNavigator = inAppBrowserNavigator
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

  val fullAccountAuthKeyRotationService =
    FullAccountAuthKeyRotationServiceImpl(
      authKeyRotationAttemptDao = authKeyRotationAttemptDao,
      rotateAuthKeysF8eClient = rotateAuthKeysF8eClient,
      keyboxDao = appComponent.keyboxDao,
      accountAuthenticator = appComponent.accountAuthenticator,
      bestEffortFullAccountCloudBackupUploader = bestEffortFullAccountCloudBackupUploader,
      relationshipsService = appComponent.relationshipsService,
      endorseTrustedContactsService = appComponent.endorseTrustedContactsService
    )

  val rotateAuthUIStateMachine = RotateAuthKeyUIStateMachineImpl(
    appKeysGenerator = appComponent.appKeysGenerator,
    proofOfPossessionNfcStateMachine = proofOfPossessionNfcStateMachine,
    fullAccountAuthKeyRotationService = fullAccountAuthKeyRotationService,
    inAppBrowserNavigator = inAppBrowserNavigator
  )

  val inviteTrustedContactFlowUiStateMachine = InviteTrustedContactFlowUiStateMachineImpl(
    addingTrustedContactUiStateMachine = addingTcsUiStateMachine,
    gettingStartedTaskDao = gettingStartedTaskDao,
    relationshipsService = appComponent.relationshipsService
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
      appFunctionalityService = appComponent.appFunctionalityService,
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
      sweepService = sweepService,
      coachmarkService = coachmarkService,
      firmwareDataService = appComponent.firmwareDataService,
      transactionsService = appComponent.transactionsService,
      sellBitcoinFeatureFlag = appComponent.sellBitcoinFeatureFlag,
      mobilePayRevampFeatureFlag = appComponent.mobilePayRevampFeatureFlag,
      exchangeRateService = appComponent.exchangeRateService
    )
  val customAmountEntryUiStateMachine =
    CustomAmountEntryUiStateMachineImpl(
      moneyCalculatorUiStateMachine = moneyCalculatorUiStateMachine,
      moneyDisplayFormatter = moneyDisplayFormatter,
      fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository
    )

  val bitcoinPriceDetailsUiStateMachine = BitcoinPriceChartUiStateMachineImpl(
    clock = appComponent.clock,
    haptics = appComponent.haptics,
    timeZoneProvider = timeZoneProvider,
    currencyConverter = appComponent.currencyConverter,
    dateTimeFormatter = dateTimeFormatter,
    eventTracker = appComponent.eventTracker,
    moneyDisplayFormatter = moneyDisplayFormatter,
    chartDataFetcherService = exchangeRateChartFetcher,
    fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository
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
      recoveryIncompleteRepository = appComponent.postSocRecTaskRepository,
      moneyHomeViewingBalanceUiStateMachine = moneyHomeViewingBalanceUiStateMachine,
      customAmountEntryUiStateMachine = customAmountEntryUiStateMachine,
      repairCloudBackupStateMachine = repairMobileKeyBackupUiStateMachine,
      fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository,
      managingFingerprintsUiStateMachine = managingFingerprintsUiStateMachine,
      sweepUiStateMachine = sweepUiStateMachine,
      bitcoinPriceChartUiStateMachine = bitcoinPriceDetailsUiStateMachine,
      socRecService = appComponent.socRecService,
      transactionsService = appComponent.transactionsService,
      utxoConsolidationUiStateMachine = utxoConsolidationUiStateMachine,
      partnershipsSellUiStateMachine = partnershipsSellUiStateMachine
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
      appComponent.networkingDebugService
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

  private val bitcoinWalletDebugUiStateMachine = BitcoinWalletDebugUiStateMachineImpl(
    transactionsService = appComponent.transactionsService,
    moneyDisplayFormatter = moneyDisplayFormatter,
    bitcoinExplorer = bitcoinExplorer,
    inAppBrowserNavigator = inAppBrowserNavigator
  )

  val debugMenuStateMachine =
    DebugMenuStateMachineImpl(
      analyticsUiStateMachine = analyticsStateMachine,
      bitcoinWalletDebugUiStateMachine = bitcoinWalletDebugUiStateMachine,
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
      currencyConverter = appComponent.currencyConverter,
      mobilePayService = appComponent.mobilePayService,
      authF8eClient = appComponent.authF8eClient,
      transactionsService = appComponent.transactionsService
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
      appFunctionalityService = appComponent.appFunctionalityService,
      managingFingerprintsUiStateMachine = managingFingerprintsUiStateMachine,
      resettingDeviceUiStateMachine = resettingDeviceUiStateMachine,
      coachmarkService = coachmarkService,
      firmwareDataService = appComponent.firmwareDataService,
      clock = appComponent.clock
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
      appFunctionalityService = appComponent.appFunctionalityService,
      cloudBackupHealthRepository = cloudBackupHealthRepository,
      coachmarkService = coachmarkService,
      mobilePayRevampFeatureFlag = appComponent.mobilePayRevampFeatureFlag
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

  private val inviteBeneficiaryUiStateMachine = InviteBeneficiaryUiStateMachineImpl(
    addingTrustedContactUiStateMachine = addingTcsUiStateMachine,
    inheritanceService = appComponent.inheritanceService
  )

  private val startClaimUiStateMachine = StartClaimUiStateMachineImpl(
    inheritanceService = appComponent.inheritanceService,
    notificationsStateMachine = enableNotificationsUiStateMachine,
    permissionChecker = appComponent.permissionChecker,
    dateTimeFormatter = dateTimeFormatter,
    timeZoneProvider = timeZoneProvider
  )

  private val inheritanceManagementUiStateMachine = InheritanceManagementUiStateMachineImpl(
    inviteBeneficiaryUiStateMachine = inviteBeneficiaryUiStateMachine,
    trustedContactEnrollmentUiStateMachine = trustedContactEnrollmentUiStateMachine,
    inheritanceService = appComponent.inheritanceService,
    startClaimUiStateMachine = startClaimUiStateMachine
  )

  private val exportToolsUiStateMachine = ExportToolsUiStateMachineImpl(
    sharingManager = sharingManager,
    exportWatchingDescriptorService = appComponent.exportWatchingDescriptorService,
    exportTransactionsService = appComponent.exportTransactionsService
  )

  val settingsStateMachine =
    SettingsHomeUiStateMachineImpl(
      appVariant = appComponent.appVariant,
      mobilePaySettingsUiStateMachine = mobilePaySettingsUiStateMachine,
      notificationPreferencesUiStateMachine = notificationPreferencesUiStateMachine,
      recoveryChannelSettingsUiStateMachine = recoveryChannelSettingsUiStateMachineImpl,
      appearancePreferenceUiStateMachine = appearancePreferenceUiStateMachine,
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
      firmwareDataService = appComponent.firmwareDataService,
      utxoConsolidationUiStateMachine = utxoConsolidationUiStateMachine,
      utxoConsolidationFeatureFlag = appComponent.utxoConsolidationFeatureFlag,
      inheritanceManagementUiStateMachine = inheritanceManagementUiStateMachine,
      inheritanceFeatureFlag = appComponent.inheritanceFeatureFlag,
      exportToolsUiStateMachine = exportToolsUiStateMachine,
      exportToolsFeatureFlag = appComponent.exportToolsFeatureFlag
    )

  val homeUiBottomSheetStateMachine =
    HomeUiBottomSheetStateMachineImpl(
      homeUiBottomSheetDao = homeUiBottomSheetDao,
      fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository,
      mobilePayService = appComponent.mobilePayService,
      mobilePayRevampFeatureFlag = appComponent.mobilePayRevampFeatureFlag
    )

  val currencyChangeMobilePayBottomSheetUpdater =
    CurrencyChangeMobilePayBottomSheetUpdaterImpl(
      homeUiBottomSheetDao = homeUiBottomSheetDao
    )

  val homeStatusBannerUiStateMachine =
    HomeStatusBannerUiStateMachineImpl(
      appFunctionalityService = appComponent.appFunctionalityService,
      dateTimeFormatter = dateTimeFormatter,
      timeZoneProvider = timeZoneProvider,
      clock = appComponent.clock,
      eventTracker = appComponent.eventTracker
    )

  val appFunctionalityStatusUiStateMachine =
    AppFunctionalityStatusUiStateMachineImpl(
      dateTimeFormatter = dateTimeFormatter,
      timeZoneProvider = timeZoneProvider,
      clock = appComponent.clock,
      mobilePayRevampFeatureFlag = appComponent.mobilePayRevampFeatureFlag
    )

  val expectedTransactionNoticeUiStateMachine = ExpectedTransactionNoticeUiStateMachineImpl(
    dateTimeFormatter = dateTimeFormatter,
    transactionsStatusRepository = partnershipTransactionsStatusRepository,
    delayer = Delayer.Default
  )

  val homeUiStateMachine = HomeUiStateMachineImpl(
    appFunctionalityStatusUiStateMachine = appFunctionalityStatusUiStateMachine,
    currencyChangeMobilePayBottomSheetUpdater = currencyChangeMobilePayBottomSheetUpdater,
    homeStatusBannerUiStateMachine = homeStatusBannerUiStateMachine,
    homeUiBottomSheetStateMachine = homeUiBottomSheetStateMachine,
    moneyHomeUiStateMachine = moneyHomeStateMachine,
    settingsHomeUiStateMachine = settingsStateMachine,
    setSpendingLimitUiStateMachine = setSpendingLimitUiStateMachine,
    trustedContactEnrollmentUiStateMachine = trustedContactEnrollmentUiStateMachine,
    cloudBackupHealthRepository = cloudBackupHealthRepository,
    appFunctionalityService = appComponent.appFunctionalityService,
    expectedTransactionNoticeUiStateMachine = expectedTransactionNoticeUiStateMachine,
    deepLinkHandler = deepLinkHandler,
    inAppBrowserNavigator = inAppBrowserNavigator,
    clock = appComponent.clock,
    timeZoneProvider = timeZoneProvider,
    fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository,
    mobilePayService = appComponent.mobilePayService,
    sellBitcoinFeatureFlag = appComponent.sellBitcoinFeatureFlag
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
    accountService = appComponent.accountService
  )

  val createSoftwareWalletUiStateMachine = CreateSoftwareWalletUiStateMachineImpl(
    createSoftwareWalletService = createSoftwareWalletService,
    notificationPreferencesSetupUiStateMachine = notificationPreferencesSetupUiStateMachine
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

  val initiatingLostAppRecoveryStateMachine =
    InitiatingLostAppRecoveryDataStateMachineImpl(
      appKeysGenerator = appKeysGenerator,
      authF8eClient = appComponent.authF8eClient,
      listKeysetsF8eClient = appComponent.listKeysetsF8eClient,
      lostAppRecoveryInitiator = delayNotifyLostAppRecoveryInitiator,
      lostAppRecoveryAuthenticator = delayNotifyLostAppRecoveryAuthenticator,
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

  val recoveryAuthCompleter = RecoveryAuthCompleterImpl(
    appAuthKeyMessageSigner = appComponent.appAuthKeyMessageSigner,
    completeDelayNotifyF8eClient = completeDelayNotifyF8eClient,
    accountAuthenticator = appComponent.accountAuthenticator,
    recoverySyncer = recoverySyncer,
    authTokenDao = appComponent.authTokenDao,
    delayer = appComponent.delayer,
    relationshipsService = appComponent.relationshipsService
  )

  val f8eSpendingKeyRotator =
    F8eSpendingKeyRotatorImpl(
      createAccountKeysetF8eClient = createAccountKeysetF8eClient,
      setActiveSpendingKeysetF8eClient = setActiveSpendingKeysetF8eClient
    )

  val recoveryInProgressDataStateMachine = RecoveryInProgressDataStateMachineImpl(
    recoveryCanceler = lostAppRecoveryCanceler,
    clock = appComponent.clock,
    csekGenerator = csekGenerator,
    csekDao = csekDao,
    recoveryAuthCompleter = recoveryAuthCompleter,
    f8eSpendingKeyRotator = f8eSpendingKeyRotator,
    uuidGenerator = appComponent.uuidGenerator,
    recoverySyncer = recoverySyncer,
    accountAuthenticator = appComponent.accountAuthenticator,
    recoveryDao = appComponent.recoveryDao,
    delayer = appComponent.delayer,
    deviceTokenManager = appComponent.deviceTokenManager,
    relationshipsService = appComponent.relationshipsService,
    endorseTrustedContactsService = appComponent.endorseTrustedContactsService
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
      socRecChallengeRepository = appComponent.socRecChallengeRepository,
      relationshipsService = appComponent.relationshipsService,
      postSocRecTaskRepository = appComponent.postSocRecTaskRepository,
      socRecStartedChallengeDao = appComponent.socRecStartedChallengeDao,
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

  val cloudBackupRefresher = TrustedContactCloudBackupRefresherImpl(
    socRecService = appComponent.socRecService,
    cloudBackupDao = cloudBackupDao,
    cloudStoreAccountRepository = cloudStoreAccountRepository,
    cloudBackupRepository = cloudBackupRepository,
    fullAccountCloudBackupCreator = fullAccountCloudBackupCreator,
    eventTracker = appComponent.eventTracker,
    clock = appComponent.clock
  )

  val hasActiveFullAccountDataStateMachine = HasActiveFullAccountDataStateMachineImpl(
    lostHardwareRecoveryDataStateMachine = lostHardwareRecoveryDataStateMachine,
    trustedContactCloudBackupRefresher = cloudBackupRefresher,
    fullAccountAuthKeyRotationService = fullAccountAuthKeyRotationService
  )

  val noLongerRecoveringUiStateMachine =
    NoLongerRecoveringUiStateMachineImpl(appComponent.recoveryDao)

  val someoneElseIsRecoveringUiStateMachine =
    SomeoneElseIsRecoveringUiStateMachineImpl(
      proofOfPossessionNfcStateMachine = proofOfPossessionNfcStateMachine,
      recoveryNotificationVerificationUiStateMachine = recoveryNotificationVerificationUiStateMachine
    )

  val someoneElseIsRecoveringDataStateMachine =
    SomeoneElseIsRecoveringDataStateMachineImpl(
      cancelDelayNotifyRecoveryF8eClient = cancelDelayNotifyRecoveryF8eClient,
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
    accountService = appComponent.accountService,
    recoverySyncer = recoverySyncer,
    someoneElseIsRecoveringDataStateMachine = someoneElseIsRecoveringDataStateMachine,
    recoverySyncFrequency = appComponent.recoverySyncFrequency,
    debugOptionsService = appComponent.debugOptionsService
  )

  val liteAccountCreator =
    LiteAccountCreatorImpl(
      accountAuthenticator = appComponent.accountAuthenticator,
      accountService = appComponent.accountService,
      appKeysGenerator = appComponent.appKeysGenerator,
      authTokenDao = appComponent.authTokenDao,
      createLiteAccountF8eClient = createAccountF8eClient
    )

  val liteAccountCloudBackupCreator =
    LiteAccountCloudBackupCreatorImpl(
      relationshipsKeysRepository = relationshipsKeysRepository,
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

  val createLiteAccountUiStateMachine = CreateLiteAccountUiStateMachineImpl(
    liteAccountCreator = liteAccountCreator,
    trustedContactEnrollmentUiStateMachine = trustedContactEnrollmentUiStateMachine,
    liteAccountCloudSignInAndBackupUiStateMachine = liteAccountCloudSignInAndBackupUiStateMachine,
    deviceInfoProvider = appComponent.deviceInfoProvider,
    eventTracker = appComponent.eventTracker,
    debugOptionsService = appComponent.debugOptionsService
  )

  val liteMoneyHomeUiStateMachine =
    LiteMoneyHomeUiStateMachineImpl(
      socRecService = appComponent.socRecService,
      inAppBrowserNavigator = inAppBrowserNavigator,
      viewingProtectedCustomerUiStateMachine = viewingProtectedCustomerUiStateMachine,
      helpingWithRecoveryUiStateMachine = helpingWithRecoveryUiStateMachine
    )
  val liteListingTrustedContactsUiStateMachine =
    LiteListingTrustedContactsUiStateMachineImpl(
      socRecService = appComponent.socRecService,
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
      appearancePreferenceUiStateMachine = appearancePreferenceUiStateMachine,
      helpCenterUiStateMachine = helpCenterUiStateMachine,
      liteTrustedContactManagementUiStateMachine = liteTrustedContactManagementUiStateMachine,
      settingsListUiStateMachine = settingsListUiStateMachine,
      feedbackUiStateMachine = feedbackUiStateMachine,
      debugMenuStateMachine = debugMenuStateMachine
    )
  val liteHomeUiStateMachine = LiteHomeUiStateMachineImpl(
    homeStatusBannerUiStateMachine = homeStatusBannerUiStateMachine,
    liteMoneyHomeUiStateMachine = liteMoneyHomeUiStateMachine,
    liteSettingsHomeUiStateMachine = liteSettingsHomeUiStateMachine,
    liteTrustedContactManagementUiStateMachine = liteTrustedContactManagementUiStateMachine,
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
        symmetricKeyEncryptor = appComponent.symmetricKeyEncryptor,
        appPrivateKeyDao = appComponent.appPrivateKeyDao
      ),
      csekDao = csekDao,
      keyboxDao = appComponent.keyboxDao,
      nfcSessionUIStateMachine = nfcSessionUIStateMachine,
      uuidGenerator = appComponent.uuidGenerator,
      debugOptionsService = appComponent.debugOptionsService
    )

  private val loadAppService = LoadAppServiceImpl(
    featureFlagService = appComponent.featureFlagService,
    accountService = appComponent.accountService,
    fullAccountAuthKeyRotationService = fullAccountAuthKeyRotationService
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
      accountDataStateMachine = accountDataStateMachine,
      loadAppService = loadAppService,
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
