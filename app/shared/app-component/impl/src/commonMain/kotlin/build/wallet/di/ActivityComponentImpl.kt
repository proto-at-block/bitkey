// We expose all member fields for access in integration tests.
@file:Suppress("MemberVisibilityCanBePrivate")

package build.wallet.di

import build.wallet.amount.AmountCalculatorImpl
import build.wallet.amount.DecimalNumberCalculatorImpl
import build.wallet.amount.DecimalNumberCreatorImpl
import build.wallet.amount.DecimalSeparatorProviderImpl
import build.wallet.amount.DoubleFormatterImpl
import build.wallet.amount.WholeNumberCalculatorImpl
import build.wallet.auth.AuthKeyRotationAttemptDaoImpl
import build.wallet.auth.AuthKeyRotationManager
import build.wallet.auth.AuthKeyRotationManagerImpl
import build.wallet.auth.FullAccountCreatorImpl
import build.wallet.auth.LiteAccountCreatorImpl
import build.wallet.auth.LiteToFullAccountUpgraderImpl
import build.wallet.auth.OnboardingFullAccountDeleterImpl
import build.wallet.availability.AppFunctionalityStatusProviderImpl
import build.wallet.bitcoin.address.BitcoinAddressParserImpl
import build.wallet.bitcoin.blockchain.BitcoinBlockchainImpl
import build.wallet.bitcoin.explorer.BitcoinExplorerImpl
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimatorImpl
import build.wallet.bitcoin.fees.BitcoinTransactionBaseCalculatorImpl
import build.wallet.bitcoin.fees.BitcoinTransactionFeeEstimatorImpl
import build.wallet.bitcoin.fees.MempoolHttpClientImpl
import build.wallet.bitcoin.invoice.BitcoinInvoiceUrlEncoderImpl
import build.wallet.bitcoin.invoice.PaymentDataParserImpl
import build.wallet.bitcoin.lightning.LightningInvoiceParser
import build.wallet.bitcoin.lightning.LightningPreferenceImpl
import build.wallet.bitcoin.transactions.TransactionPriorityPreferenceImpl
import build.wallet.bitcoin.transactions.TransactionRepositoryImpl
import build.wallet.cloud.backup.BestEffortFullAccountCloudBackupUploaderImpl
import build.wallet.cloud.backup.CloudBackupRepositoryImpl
import build.wallet.cloud.backup.FullAccountCloudBackupCreatorImpl
import build.wallet.cloud.backup.FullAccountCloudBackupRestorerImpl
import build.wallet.cloud.backup.LiteAccountCloudBackupCreatorImpl
import build.wallet.cloud.backup.LiteAccountCloudBackupRestorerImpl
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
import build.wallet.configuration.FiatMobilePayConfigurationDaoImpl
import build.wallet.configuration.FiatMobilePayConfigurationRepositoryImpl
import build.wallet.crypto.Spake2
import build.wallet.datadog.DatadogRumMonitor
import build.wallet.email.EmailValidatorImpl
import build.wallet.emergencyaccesskit.EmergencyAccessKitApkParametersProviderImpl
import build.wallet.emergencyaccesskit.EmergencyAccessKitBackupDateProviderImpl
import build.wallet.emergencyaccesskit.EmergencyAccessKitDataProviderImpl
import build.wallet.emergencyaccesskit.EmergencyAccessKitMobileKeyParametersProviderImpl
import build.wallet.emergencyaccesskit.EmergencyAccessKitPayloadDecoderImpl
import build.wallet.emergencyaccesskit.EmergencyAccessKitPdfGeneratorImpl
import build.wallet.emergencyaccesskit.EmergencyAccessKitRepositoryImpl
import build.wallet.emergencyaccesskit.EmergencyAccessKitTemplateProviderImpl
import build.wallet.emergencyaccesskit.EmergencyAccessPayloadCreatorImpl
import build.wallet.emergencyaccesskit.EmergencyAccessPayloadRestorerImpl
import build.wallet.encrypt.CryptoBox
import build.wallet.encrypt.SymmetricKeyEncryptor
import build.wallet.encrypt.SymmetricKeyGenerator
import build.wallet.encrypt.XChaCha20Poly1305
import build.wallet.encrypt.XNonceGenerator
import build.wallet.f8e.ActiveF8eEnvironmentRepositoryImpl
import build.wallet.f8e.configuration.GetBdkConfigurationServiceImpl
import build.wallet.f8e.demo.DemoModeServiceImpl
import build.wallet.f8e.mobilepay.FiatMobilePayConfigurationServiceImpl
import build.wallet.f8e.mobilepay.MobilePayBalanceServiceImpl
import build.wallet.f8e.mobilepay.MobilePaySigningServiceImpl
import build.wallet.f8e.mobilepay.MobilePaySpendingLimitServiceImpl
import build.wallet.f8e.money.FiatCurrencyDefinitionServiceImpl
import build.wallet.f8e.notifications.NotificationTouchpointServiceImpl
import build.wallet.f8e.notifications.RegisterWatchAddressServiceImpl
import build.wallet.f8e.onboarding.CompleteDelayNotifyServiceImpl
import build.wallet.f8e.onboarding.CreateAccountKeysetServiceImpl
import build.wallet.f8e.onboarding.CreateAccountServiceImpl
import build.wallet.f8e.onboarding.DeleteOnboardingFullAccountServiceImpl
import build.wallet.f8e.onboarding.OnboardingServiceImpl
import build.wallet.f8e.onboarding.SetActiveSpendingKeysetServiceImpl
import build.wallet.f8e.onboarding.UpgradeAccountServiceImpl
import build.wallet.f8e.partnerships.GetPurchaseOptionsServiceImpl
import build.wallet.f8e.partnerships.GetPurchaseQuoteListServiceImpl
import build.wallet.f8e.partnerships.GetPurchaseRedirectServiceImpl
import build.wallet.f8e.partnerships.GetTransferPartnerListServiceImpl
import build.wallet.f8e.partnerships.GetTransferRedirectServiceImpl
import build.wallet.f8e.recovery.CancelDelayNotifyRecoveryServiceImpl
import build.wallet.f8e.recovery.GetAccountStatusServiceImpl
import build.wallet.f8e.recovery.GetDelayNotifyRecoveryStatusServiceImpl
import build.wallet.f8e.recovery.InitiateAccountDelayNotifyServiceImpl
import build.wallet.f8e.recovery.InitiateHardwareAuthServiceImpl
import build.wallet.f8e.recovery.ListKeysetsServiceImpl
import build.wallet.f8e.recovery.RecoveryNotificationVerificationServiceImpl
import build.wallet.f8e.recovery.RotateAuthKeysService
import build.wallet.f8e.recovery.RotateAuthKeysServiceImpl
import build.wallet.f8e.socrec.SocialRecoveryServiceFake
import build.wallet.f8e.socrec.SocialRecoveryServiceImpl
import build.wallet.f8e.support.SupportTicketServiceImpl
import build.wallet.home.GettingStartedTaskDaoImpl
import build.wallet.home.HomeUiBottomSheetDaoImpl
import build.wallet.keybox.AppDataDeleterImpl
import build.wallet.keybox.CloudBackupDeleterImpl
import build.wallet.limit.MobilePayDisablerImpl
import build.wallet.limit.MobilePayLimitSetterImpl
import build.wallet.limit.MobilePaySpendingPolicyImpl
import build.wallet.limit.MobilePayStatusProviderImpl
import build.wallet.limit.SpendingLimitDaoImpl
import build.wallet.logging.log
import build.wallet.money.currency.FiatCurrencyRepositoryImpl
import build.wallet.money.exchange.CurrencyConverterImpl
import build.wallet.money.exchange.ExchangeRateDaoImpl
import build.wallet.money.exchange.ExchangeRateSyncerImpl
import build.wallet.money.exchange.F8eExchangeRateServiceImpl
import build.wallet.money.formatter.internal.MoneyDisplayFormatterImpl
import build.wallet.money.formatter.internal.MoneyFormatterDefinitionsImpl
import build.wallet.money.input.MoneyInputFormatterImpl
import build.wallet.nfc.NfcReaderCapabilityProviderImpl
import build.wallet.nfc.NfcTransactorImpl
import build.wallet.nfc.interceptors.collectFirmwareTelemetry
import build.wallet.nfc.interceptors.collectMetrics
import build.wallet.nfc.interceptors.haptics
import build.wallet.nfc.interceptors.iosMessages
import build.wallet.nfc.interceptors.lockDevice
import build.wallet.nfc.interceptors.retryCommands
import build.wallet.nfc.interceptors.sessionLogger
import build.wallet.nfc.interceptors.timeoutSession
import build.wallet.nfc.platform.NfcCommandsProvider
import build.wallet.nfc.platform.NfcSessionProvider
import build.wallet.nfc.transaction.PairingTransactionProviderImpl
import build.wallet.nfc.transaction.StartFingerprintEnrollmentTransactionProviderImpl
import build.wallet.notifications.NotificationTouchpointDaoImpl
import build.wallet.notifications.RegisterWatchAddressQueueImpl
import build.wallet.notifications.RegisterWatchAddressSenderImpl
import build.wallet.onboarding.LiteAccountBackupToFullAccountUpgraderImpl
import build.wallet.onboarding.OnboardingKeyboxHardwareKeysDaoImpl
import build.wallet.onboarding.OnboardingKeyboxSealedCsekDaoImpl
import build.wallet.onboarding.OnboardingKeyboxStepStateDaoImpl
import build.wallet.phonenumber.PhoneNumberFormatterImpl
import build.wallet.phonenumber.PhoneNumberValidatorImpl
import build.wallet.phonenumber.lib.PhoneNumberLibBindings
import build.wallet.platform.clipboard.ClipboardImpl
import build.wallet.platform.links.DeepLinkHandlerImpl
import build.wallet.platform.pdf.PdfAnnotatorFactory
import build.wallet.platform.settings.CountryCodeGuesserImpl
import build.wallet.platform.settings.LocaleIdentifierProviderImpl
import build.wallet.platform.settings.SystemSettingsLauncher
import build.wallet.platform.settings.TelephonyCountryCodeProviderImpl
import build.wallet.platform.sharing.SharingManager
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.queueprocessor.ProcessorImpl
import build.wallet.recovery.LostAppRecoveryAuthenticatorImpl
import build.wallet.recovery.LostAppRecoveryInitiatorImpl
import build.wallet.recovery.LostHardwareRecoveryStarterImpl
import build.wallet.recovery.RecoveryAuthCompleterImpl
import build.wallet.recovery.RecoveryCancelerImpl
import build.wallet.recovery.RecoverySyncerImpl
import build.wallet.recovery.socrec.InviteCodeLoaderImpl
import build.wallet.recovery.socrec.PostSocRecTaskRepositoryImpl
import build.wallet.recovery.socrec.RecoveryIncompleteDaoImpl
import build.wallet.recovery.socrec.SocRecChallengeRepositoryImpl
import build.wallet.recovery.socrec.SocRecCryptoFake
import build.wallet.recovery.socrec.SocRecCryptoImpl
import build.wallet.recovery.socrec.SocRecEnrollmentAuthenticationDaoImpl
import build.wallet.recovery.socrec.SocRecKeysDaoImpl
import build.wallet.recovery.socrec.SocRecKeysRepository
import build.wallet.recovery.socrec.SocRecRelationshipsDaoImpl
import build.wallet.recovery.socrec.SocRecRelationshipsRepositoryImpl
import build.wallet.recovery.socrec.SocRecStartedChallengeAuthenticationDaoImpl
import build.wallet.recovery.socrec.SocRecStartedChallengeDaoImpl
import build.wallet.recovery.socrec.SocialChallengeVerifierImpl
import build.wallet.recovery.socrec.SocialRecoveryCodeBuilderImpl
import build.wallet.recovery.socrec.SocialRecoveryServiceProviderImpl
import build.wallet.recovery.socrec.TrustedContactKeyAuthenticatorImpl
import build.wallet.recovery.sweep.SweepGeneratorImpl
import build.wallet.serialization.Base32Encoding
import build.wallet.statemachine.account.ChooseAccountAccessUiStateMachineImpl
import build.wallet.statemachine.account.create.full.CreateAccountUiStateMachineImpl
import build.wallet.statemachine.account.create.full.OnboardKeyboxUiStateMachineImpl
import build.wallet.statemachine.account.create.full.OverwriteFullAccountCloudBackupUiStateMachineImpl
import build.wallet.statemachine.account.create.full.ReplaceWithLiteAccountRestoreUiStateMachineImpl
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachineImpl
import build.wallet.statemachine.account.create.full.keybox.create.CreateKeyboxUiStateMachineImpl
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupPushItemModelProviderImpl
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachineImpl
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachineV2Impl
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryChannelsSetupPushItemModelProviderImpl
import build.wallet.statemachine.account.create.full.onboard.notifications.UiErrorHintsProviderImpl
import build.wallet.statemachine.account.create.lite.CreateLiteAccountUiStateMachineImpl
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachineImpl
import build.wallet.statemachine.auth.RefreshAuthTokensUiStateMachineImpl
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
import build.wallet.statemachine.data.account.create.OnboardConfigDataStateMachineImpl
import build.wallet.statemachine.data.account.create.OnboardingStepSkipConfigDaoImpl
import build.wallet.statemachine.data.account.create.activate.ActivateFullAccountDataStateMachineImpl
import build.wallet.statemachine.data.account.create.keybox.CreateKeyboxDataStateMachineImpl
import build.wallet.statemachine.data.account.create.onboard.OnboardKeyboxDataStateMachineImpl
import build.wallet.statemachine.data.app.AppDataStateMachineImpl
import build.wallet.statemachine.data.firmware.FirmwareDataStateMachineImpl
import build.wallet.statemachine.data.keybox.AccountDataStateMachineImpl
import build.wallet.statemachine.data.keybox.CloudBackupRefresherImpl
import build.wallet.statemachine.data.keybox.HasActiveFullAccountDataStateMachineImpl
import build.wallet.statemachine.data.keybox.HasActiveLiteAccountDataStateMachineImpl
import build.wallet.statemachine.data.keybox.NoActiveAccountDataStateMachineImpl
import build.wallet.statemachine.data.keybox.address.FullAccountAddressDataStateMachineImpl
import build.wallet.statemachine.data.keybox.config.TemplateFullAccountConfigDataStateMachineImpl
import build.wallet.statemachine.data.keybox.transactions.FullAccountTransactionsDataStateMachineImpl
import build.wallet.statemachine.data.lightning.LightningNodeDataStateMachineImpl
import build.wallet.statemachine.data.mobilepay.MobilePayDataStateMachineImpl
import build.wallet.statemachine.data.money.currency.CurrencyPreferenceDataStateMachineImpl
import build.wallet.statemachine.data.notifications.NotificationTouchpointDataStateMachineImpl
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
import build.wallet.statemachine.data.sync.ElectrumServerDataStateMachineImpl
import build.wallet.statemachine.demo.DemoModeCodeEntryUiStateMachineImpl
import build.wallet.statemachine.demo.DemoModeConfigUiStateMachineImpl
import build.wallet.statemachine.dev.AccountConfigUiStateMachineImpl
import build.wallet.statemachine.dev.AppStateDeleterOptionsUiStateMachineImpl
import build.wallet.statemachine.dev.BitcoinNetworkPickerUiStateMachineImpl
import build.wallet.statemachine.dev.BitkeyDeviceOptionsUiStateMachineImpl
import build.wallet.statemachine.dev.DebugMenuListStateMachineImpl
import build.wallet.statemachine.dev.DebugMenuStateMachineImpl
import build.wallet.statemachine.dev.F8eCustomUrlStateMachineImpl
import build.wallet.statemachine.dev.F8eEnvironmentPickerUiStateMachineImpl
import build.wallet.statemachine.dev.FirmwareMetadataUiStateMachineImpl
import build.wallet.statemachine.dev.InfoOptionsUiStateMachineImpl
import build.wallet.statemachine.dev.OnboardingAppKeyDeletionUiStateMachineImpl
import build.wallet.statemachine.dev.OnboardingConfigStateMachineImpl
import build.wallet.statemachine.dev.analytics.AnalyticsOptionsUiStateMachineImpl
import build.wallet.statemachine.dev.analytics.AnalyticsUiStateMachineImpl
import build.wallet.statemachine.dev.cloud.CloudDevOptionsStateMachine
import build.wallet.statemachine.dev.debug.NetworkingDebugConfigPickerUiStateMachineImpl
import build.wallet.statemachine.dev.featureFlags.BooleanFlagItemUiStateMachineImpl
import build.wallet.statemachine.dev.featureFlags.FeatureFlagsOptionsUiStateMachineImpl
import build.wallet.statemachine.dev.featureFlags.FeatureFlagsStateMachineImpl
import build.wallet.statemachine.dev.lightning.ConnectAndOpenChannelStateMachineImpl
import build.wallet.statemachine.dev.lightning.LightningDebugMenuUiStateMachineImpl
import build.wallet.statemachine.dev.lightning.LightningOptionsUiStateMachineImpl
import build.wallet.statemachine.dev.lightning.LightningSendReceiveUiStateMachineImpl
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
import build.wallet.statemachine.moneyhome.card.fwup.DeviceUpdateCardUiStateMachineImpl
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedCardUiStateMachineImpl
import build.wallet.statemachine.moneyhome.card.replacehardware.ReplaceHardwareCardUiStateMachineImpl
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiStateMachineImpl
import build.wallet.statemachine.moneyhome.full.MoneyHomeViewingBalanceUiStateMachineImpl
import build.wallet.statemachine.moneyhome.lite.LiteMoneyHomeUiStateMachineImpl
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineImpl
import build.wallet.statemachine.notifications.NotificationPreferencesUiStateMachineImpl
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationUiStateMachineImpl
import build.wallet.statemachine.notifications.NotificationsPreferencesCachedProviderImpl
import build.wallet.statemachine.partnerships.AddBitcoinUiStateMachineImpl
import build.wallet.statemachine.partnerships.purchase.CustomAmountEntryUiStateMachineImpl
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseUiStateMachineImpl
import build.wallet.statemachine.partnerships.transfer.PartnershipsTransferUiStateMachineImpl
import build.wallet.statemachine.platform.nfc.EnableNfcNavigatorImpl
import build.wallet.statemachine.platform.permissions.EnableNotificationsUiStateMachineImpl
import build.wallet.statemachine.platform.permissions.NotificationPermissionRequesterImpl
import build.wallet.statemachine.platform.permissions.PermissionUiStateMachineImpl
import build.wallet.statemachine.receive.AddressQrCodeUiStateMachineImpl
import build.wallet.statemachine.recovery.RecoveryInProgressUiStateMachineImpl
import build.wallet.statemachine.recovery.cloud.AccessCloudBackupUiStateMachineImpl
import build.wallet.statemachine.recovery.cloud.CloudSignInUiStateMachine
import build.wallet.statemachine.recovery.cloud.FullAccountCloudBackupRestorationUiStateMachineImpl
import build.wallet.statemachine.recovery.cloud.LiteAccountCloudBackupRestorationUiStateMachineImpl
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIStateMachineImpl
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
import build.wallet.statemachine.send.BitcoinAddressRecipientUiStateMachineImpl
import build.wallet.statemachine.send.BitcoinQrCodeScanUiStateMachineImpl
import build.wallet.statemachine.send.SendUiStateMachineImpl
import build.wallet.statemachine.send.TransactionDetailsCardUiStateMachineImpl
import build.wallet.statemachine.send.TransferAmountEntryUiStateMachineImpl
import build.wallet.statemachine.send.TransferConfirmationUiStateMachineImpl
import build.wallet.statemachine.send.TransferInitiatedUiStateMachineImpl
import build.wallet.statemachine.send.fee.FeeOptionListUiStateMachineImpl
import build.wallet.statemachine.send.fee.FeeOptionUiStateMachineImpl
import build.wallet.statemachine.send.fee.FeeSelectionUiStateMachineImpl
import build.wallet.statemachine.settings.SettingsListUiStateMachineImpl
import build.wallet.statemachine.settings.full.SettingsHomeUiStateMachineImpl
import build.wallet.statemachine.settings.full.device.DeviceSettingsUiStateMachineImpl
import build.wallet.statemachine.settings.full.electrum.CustomElectrumServerSettingUiStateMachineImpl
import build.wallet.statemachine.settings.full.electrum.CustomElectrumServerUiStateMachineImpl
import build.wallet.statemachine.settings.full.electrum.SetElectrumServerUiStateMachineImpl
import build.wallet.statemachine.settings.full.feedback.FeedbackFormUiStateMachineImpl
import build.wallet.statemachine.settings.full.feedback.FeedbackUiStateMachineImpl
import build.wallet.statemachine.settings.full.feedback.old.OldFeedbackUiStateMachineImpl
import build.wallet.statemachine.settings.full.mobilepay.MobilePaySettingsUiStateMachineImpl
import build.wallet.statemachine.settings.full.mobilepay.MobilePayStatusUiStateMachineImpl
import build.wallet.statemachine.settings.full.mobilepay.SpendingLimitCardUiStateMachineImpl
import build.wallet.statemachine.settings.full.notifications.NotificationsSettingsUiStateMachineImpl
import build.wallet.statemachine.settings.full.notifications.RecoveryChannelSettingsUiStateMachineImpl
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiStateMachineImpl
import build.wallet.statemachine.settings.lite.LiteSettingsHomeUiStateMachineImpl
import build.wallet.statemachine.start.GettingStartedRoutingStateMachineImpl
import build.wallet.statemachine.status.AppFunctionalityStatusUiStateMachineImpl
import build.wallet.statemachine.status.HomeStatusBannerUiStateMachineImpl
import build.wallet.statemachine.transactions.TransactionItemUiStateMachineImpl
import build.wallet.statemachine.transactions.TransactionListUiStateMachineImpl
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiStateMachineImpl
import build.wallet.support.SupportTicketFormValidatorImpl
import build.wallet.support.SupportTicketRepositoryImpl
import build.wallet.time.DateTimeFormatterImpl
import build.wallet.time.DurationFormatterImpl
import build.wallet.time.TimeZoneFormatterImpl
import build.wallet.time.TimeZoneProviderImpl
import kotlin.time.Duration.Companion.minutes

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
  phoneNumberLibBindings: PhoneNumberLibBindings,
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

  val lightningPreference = LightningPreferenceImpl(appComponent.bitkeyDatabaseProvider)

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

  val cancelDelayNotifyRecoveryService =
    CancelDelayNotifyRecoveryServiceImpl(appComponent.f8eHttpClient)

  val initiateAccountDelayNotifyService =
    InitiateAccountDelayNotifyServiceImpl(
      appComponent.f8eHttpClient
    )

  val createAccountService =
    CreateAccountServiceImpl(
      f8eHttpClient = appComponent.f8eHttpClient
    )

  val createAccountKeysetService =
    CreateAccountKeysetServiceImpl(
      f8eHttpClient = appComponent.f8eHttpClient
    )

  val setActiveSpendingKeysetService =
    SetActiveSpendingKeysetServiceImpl(appComponent.f8eHttpClient)

  val bitcoinFeeRateEstimator =
    BitcoinFeeRateEstimatorImpl(
      mempoolHttpClient =
        MempoolHttpClientImpl(
          logLevelPolicy = appComponent.ktorLogLevelPolicy,
          networkReachabilityProvider = appComponent.networkReachabilityProvider
        ),
      bdkBlockchainProvider = appComponent.bdkBlockchainProvider
    )

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

  val fullAccountCloudBackupCreator =
    FullAccountCloudBackupCreatorImpl(
      appPrivateKeyDao = appComponent.appPrivateKeyDao,
      fullAccountFieldsCreator = fullAccountFieldsCreator,
      socRecKeysRepository = socRecKeysRepository
    )

  val exchangeRateDao =
    ExchangeRateDaoImpl(
      databaseProvider = appComponent.bitkeyDatabaseProvider
    )

  val activeF8eEnvironmentRepository =
    ActiveF8eEnvironmentRepositoryImpl(
      keyboxDao = appComponent.keyboxDao
    )

  val f8eExchangeRateService =
    F8eExchangeRateServiceImpl(
      f8eHttpClient = appComponent.f8eHttpClient
    )

  val exchangeRateSyncer =
    ExchangeRateSyncerImpl(
      exchangeRateDao = exchangeRateDao,
      f8eExchangeRateService = f8eExchangeRateService,
      activeF8eEnvironmentRepository = activeF8eEnvironmentRepository
    )

  val currencyConverter =
    CurrencyConverterImpl(
      accountRepository = appComponent.accountRepository,
      exchangeRateDao = exchangeRateDao,
      f8eExchangeRateService = f8eExchangeRateService
    )

  val durationFormatter = DurationFormatterImpl()

  val getDelayNotifyRecoveryStatusService =
    GetDelayNotifyRecoveryStatusServiceImpl(
      f8eHttpClient = appComponent.f8eHttpClient
    )

  val recoverySyncer =
    RecoverySyncerImpl(
      recoveryDao = appComponent.recoveryDao,
      getRecoveryStatusService = getDelayNotifyRecoveryStatusService
    )

  val getAccountStatusService =
    GetAccountStatusServiceImpl(appComponent.f8eHttpClient, appComponent.uuid)

  val delayNotifyLostAppRecoveryInitiator =
    LostAppRecoveryInitiatorImpl(
      initiateAccountDelayNotifyService =
        InitiateAccountDelayNotifyServiceImpl(
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

  val nfcTransactor =
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
          collectMetrics(appComponent.datadogRumMonitor, appComponent.datadogTracer),
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

  val initiateHardwareAuthService =
    InitiateHardwareAuthServiceImpl(
      authenticationService = appComponent.authenticationService
    )

  val completeDelayNotifyService = CompleteDelayNotifyServiceImpl(appComponent.f8eHttpClient)

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

  val mobilePaySigningService = MobilePaySigningServiceImpl(appComponent.f8eHttpClient)

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

  val emergencyAccessKitPdfGenerator = EmergencyAccessKitPdfGeneratorImpl(
    apkParametersProvider = EmergencyAccessKitApkParametersProviderImpl(
      emergencyAccessKitDataProvider
    ),
    mobileKeyParametersProvider = EmergencyAccessKitMobileKeyParametersProviderImpl(
      payloadCreator = EmergencyAccessPayloadCreatorImpl(
        csekDao = csekDao,
        symmetricKeyEncryptor = symmetricKeyEncryptor,
        appPrivateKeyDao = appComponent.appPrivateKeyDao
      )
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
      cloudBackupDao = cloudBackupDao
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

  val socRecRelationshipsDao = SocRecRelationshipsDaoImpl(appComponent.bitkeyDatabaseProvider)

  val socRecEnrollmentAuthenticationDao = SocRecEnrollmentAuthenticationDaoImpl(
    appComponent.appPrivateKeyDao,
    appComponent.bitkeyDatabaseProvider
  )

  val base32Encoding = Base32Encoding()

  val pakeCodeBuilder = SocialRecoveryCodeBuilderImpl(
    base32Encoding = base32Encoding
  )

  val socialRecoveryServiceFake =
    SocialRecoveryServiceFake(
      uuid = appComponent.uuid,
      backgroundScope = appComponent.appCoroutineScope
    )

  val socialRecoveryServiceImpl =
    SocialRecoveryServiceImpl(
      f8eHttpClient = appComponent.f8eHttpClient
    )

  val socialRecoveryServiceProvider = SocialRecoveryServiceProviderImpl(
    accountRepository = appComponent.accountRepository,
    socRecFake = socialRecoveryServiceFake,
    socRecService = socialRecoveryServiceImpl,
    templateFullAccountConfigDao = appComponent.templateFullAccountConfigDao
  )

  val socRecStartedChallengeAuthenticationDao = SocRecStartedChallengeAuthenticationDaoImpl(
    appComponent.appPrivateKeyDao,
    appComponent.bitkeyDatabaseProvider
  )

  val socRecRelationshipsRepository =
    SocRecRelationshipsRepositoryImpl(
      socialRecoveryServiceProvider = socialRecoveryServiceProvider,
      socRecRelationshipsDao = socRecRelationshipsDao,
      socRecEnrollmentAuthenticationDao = socRecEnrollmentAuthenticationDao,
      socRecCrypto = socRecCrypto,
      socialRecoveryCodeBuilder = pakeCodeBuilder
    )

  val bestEffortFullAccountCloudBackupUploader =
    BestEffortFullAccountCloudBackupUploaderImpl(
      cloudBackupDao = cloudBackupDao,
      socRecRelationshipsRepository = socRecRelationshipsRepository,
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
    socRecRelationshipsRepository = socRecRelationshipsRepository,
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

  val spendingLimitService =
    MobilePaySpendingLimitServiceImpl(
      f8eHttpClient = appComponent.f8eHttpClient
    )

  val mobilePayDisabler =
    MobilePayDisablerImpl(
      spendingLimitDao = spendingLimitDao,
      spendingLimitService = spendingLimitService
    )

  val mobilePayBalanceService =
    MobilePayBalanceServiceImpl(
      f8eHttpClient = appComponent.f8eHttpClient,
      fiatCurrencyDao = appComponent.fiatCurrencyDao
    )

  val mobilePayStatusProvider =
    MobilePayStatusProviderImpl(
      spendingLimitDao = spendingLimitDao,
      mobilePayBalanceService = mobilePayBalanceService,
      uuid = appComponent.uuid
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

  val moneyCalculatorUiStateMachine =
    MoneyCalculatorUiStateMachineImpl(
      amountCalculator = amountCalculator,
      bitcoinDisplayPreferenceRepository = appComponent.bitcoinDisplayPreferenceRepository,
      currencyConverter = currencyConverter,
      moneyAmountEntryUiStateMachine = moneyAmountEntryUiStateMachine,
      decimalNumberCreator = decimalNumberCreator,
      doubleFormatter = doubleFormatter
    )

  val mobilePaySpendingPolicy = MobilePaySpendingPolicyImpl()

  val transferAmountEntryUiStateMachine =
    TransferAmountEntryUiStateMachineImpl(
      currencyConverter = currencyConverter,
      moneyCalculatorUiStateMachine = moneyCalculatorUiStateMachine,
      mobilePaySpendingPolicy = mobilePaySpendingPolicy,
      moneyDisplayFormatter = moneyDisplayFormatter
    )

  val transactionDetailsCardUiStateMachine =
    TransactionDetailsCardUiStateMachineImpl(
      currencyConverter = currencyConverter,
      moneyDisplayFormatter = moneyDisplayFormatter
    )

  val transferInitiatedUiStateMachine =
    TransferInitiatedUiStateMachineImpl(
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

  val fiatMobilePayConfigurationDao =
    FiatMobilePayConfigurationDaoImpl(appComponent.bitkeyDatabaseProvider)
  val fiatMobilePayConfigurationService =
    FiatMobilePayConfigurationServiceImpl(
      f8eHttpClient = appComponent.f8eHttpClient,
      fiatCurrencyDao = appComponent.fiatCurrencyDao
    )
  val fiatMobilePayConfigurationRepository =
    FiatMobilePayConfigurationRepositoryImpl(
      fiatMobilePayConfigurationDao = fiatMobilePayConfigurationDao,
      fiatMobilePayConfigurationService = fiatMobilePayConfigurationService
    )

  val spendingLimitPickerUiStateMachine =
    SpendingLimitPickerUiStateMachineImpl(
      currencyConverter = currencyConverter,
      fiatMobilePayConfigurationRepository = fiatMobilePayConfigurationRepository,
      moneyDisplayFormatter = moneyDisplayFormatter,
      proofOfPossessionNfcStateMachine = proofOfPossessionNfcStateMachine
    )

  val registerWatchAddressQueue =
    RegisterWatchAddressQueueImpl(
      databaseProvider = appComponent.bitkeyDatabaseProvider
    )

  val registerWatchAddressService =
    RegisterWatchAddressServiceImpl(
      f8eHttpClient = appComponent.f8eHttpClient
    )

  val registerWatchAddressSender =
    RegisterWatchAddressSenderImpl(registerWatchAddressService)

  private val registerWatchAddressProcessor =
    ProcessorImpl(
      queue = registerWatchAddressQueue,
      processor = registerWatchAddressSender,
      retryFrequency = 1.minutes,
      retryBatchSize = 1
    )

  val addressQrCodeUiStateMachine =
    AddressQrCodeUiStateMachineImpl(
      clipboard = clipboard,
      delayer = appComponent.delayer,
      sharingManager = sharingManager,
      bitcoinInvoiceUrlEncoder = bitcoinInvoiceUrlEncoder
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
      uuid = appComponent.uuid
    )

  val cloudBackupRestorer =
    FullAccountCloudBackupRestorerImpl(
      cloudBackupV2Restorer = cloudBackupV2Restorer
    )

  val challengeCodeFormatter = ChallengeCodeFormatterImpl()

  val socRecPendingChallengeDao = SocRecStartedChallengeDaoImpl(appComponent.bitkeyDatabaseProvider)

  val socRecChallengeRepository =
    SocRecChallengeRepositoryImpl(
      socRec = socialRecoveryServiceImpl,
      socRecCodeBuilder = pakeCodeBuilder,
      socRecCrypto = socRecCrypto,
      socRecFake = socialRecoveryServiceFake,
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

  val sweepUiStateMachine =
    SweepUiStateMachineImpl(
      nfcSessionUIStateMachine = nfcSessionUIStateMachine,
      moneyAmountUiStateMachine = moneyAmountUiStateMachine
    )

  val completingRecoveryUiStateMachine =
    CompletingRecoveryUiStateMachineImpl(
      proofOfPossessionNfcStateMachine = proofOfPossessionNfcStateMachine,
      fullAccountCloudSignInAndBackupUiStateMachine = fullAccountCloudSignInAndBackupUiStateMachine,
      sweepUiStateMachine = sweepUiStateMachine,
      nfcSessionUIStateMachine = nfcSessionUIStateMachine
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

  val countryCodeGuesser =
    CountryCodeGuesserImpl(
      localeCountryCodeProvider = appComponent.localeCountryCodeProvider,
      telephonyCountryCodeProvider = telephonyCountryCodeProvider
    )

  val phoneNumberValidator =
    PhoneNumberValidatorImpl(
      countryCodeGuesser = countryCodeGuesser,
      phoneNumberLibBindings = phoneNumberLibBindings
    )

  val notificationTouchpointDao =
    NotificationTouchpointDaoImpl(
      databaseProvider = appComponent.bitkeyDatabaseProvider,
      phoneNumberValidator = phoneNumberValidator
    )

  val notificationTouchpointService =
    NotificationTouchpointServiceImpl(
      f8eHttpClient = appComponent.f8eHttpClient,
      phoneNumberValidator = phoneNumberValidator
    )

  val notificationsPreferencesCachedProvider = NotificationsPreferencesCachedProviderImpl(
    notificationTouchpointService = notificationTouchpointService,
    keyValueStoreFactory = appComponent.keyValueStoreFactory
  )

  val upgradeAccountService = UpgradeAccountServiceImpl(appComponent.f8eHttpClient)

  val fullAccountCreator =
    FullAccountCreatorImpl(
      keyboxDao = appComponent.keyboxDao,
      createFullAccountService = createAccountService,
      accountAuthenticator = appComponent.accountAuthenticator,
      authTokenDao = appComponent.authTokenDao,
      deviceTokenManager = appComponent.deviceTokenManager,
      uuid = appComponent.uuid,
      notificationTouchpointService = notificationTouchpointService,
      notificationTouchpointDao = notificationTouchpointDao
    )

  val onboardingKeyboxStepStateDao =
    OnboardingKeyboxStepStateDaoImpl(
      databaseProvider = appComponent.bitkeyDatabaseProvider
    )

  val phoneNumberFormatter =
    PhoneNumberFormatterImpl(
      phoneNumberLibBindings = phoneNumberLibBindings
    )

  val phoneNumberInputStateMachine =
    PhoneNumberInputUiStateMachineImpl(
      phoneNumberFormatter = phoneNumberFormatter,
      phoneNumberValidator = phoneNumberValidator
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
      notificationTouchpointDao = notificationTouchpointDao,
      notificationTouchpointService = notificationTouchpointService,
      phoneNumberInputUiStateMachine = phoneNumberInputStateMachine,
      proofOfPossessionNfcStateMachine = proofOfPossessionNfcStateMachine,
      uiErrorHintSubmitter = uiErrorHintProvider,
      verificationCodeInputStateMachine = verificationCodeInputStateMachine
    )

  val pushItemModelProvider =
    NotificationPreferencesSetupPushItemModelProviderImpl(
      pushNotificationPermissionStatusProvider = appComponent.pushNotificationPermissionStatusProvider,
      systemSettingsLauncher = systemSettingsLauncher
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
      uiErrorHintsProvider = uiErrorHintProvider
    )

  val notificationPreferencesSetupUiStateMachine =
    NotificationPreferencesSetupUiStateMachineImpl(
      eventTracker = appComponent.eventTracker,
      delayer = appComponent.delayer,
      notificationPermissionRequester = notificationPermissionRequester,
      notificationTouchpointDao = notificationTouchpointDao,
      onboardingKeyboxStepStateDao = onboardingKeyboxStepStateDao,
      notificationTouchpointInputAndVerificationUiStateMachine = notificationTouchpointInputAndVerificationUiStateMachine,
      pushItemModelProvider = pushItemModelProvider
    )

  val notificationPreferencesSetupUiStateMachineV2 =
    NotificationPreferencesSetupUiStateMachineV2Impl(
      eventTracker = appComponent.eventTracker,
      notificationPermissionRequester = notificationPermissionRequester,
      notificationTouchpointInputAndVerificationUiStateMachine = notificationTouchpointInputAndVerificationUiStateMachine,
      notificationTouchpointDao = notificationTouchpointDao,
      notificationPreferencesUiStateMachine = notificationPreferencesUiStateMachine,
      onboardingKeyboxStepStateDao = onboardingKeyboxStepStateDao,
      pushItemModelProvider = pushItemModelProviderV2,
      inAppBrowserNavigator = inAppBrowserNavigator,
      telephonyCountryCodeProvider = telephonyCountryCodeProvider,
      uiErrorHintsProvider = uiErrorHintProvider
    )

  val notificationsSettingsStateMachine =
    NotificationsSettingsUiStateMachineImpl(
      notificationTouchpointInputAndVerificationUiStateMachine = notificationTouchpointInputAndVerificationUiStateMachine
    )

  val onboardingKeyboxSealedCsekDao =
    OnboardingKeyboxSealedCsekDaoImpl(
      encryptedKeyValueStoreFactory = appComponent.secureStoreFactory
    )

  val onboardingKeyboxHwAuthPublicKeyDao =
    OnboardingKeyboxHardwareKeysDaoImpl(
      databaseProvider = appComponent.bitkeyDatabaseProvider
    )

  val onboardingService =
    OnboardingServiceImpl(
      f8eHttpClient = appComponent.f8eHttpClient
    )

  val listKeysetsService =
    ListKeysetsServiceImpl(
      f8eHttpClient = appComponent.f8eHttpClient,
      uuid = appComponent.uuid
    )

  val pairingTransactionProvider =
    PairingTransactionProviderImpl(
      csekGenerator = csekGenerator,
      csekDao = csekDao,
      uuid = appComponent.uuid,
      appInstallationDao = appComponent.appInstallationDao
    )

  val startFingerprintEnrollmentTransactionProvider =
    StartFingerprintEnrollmentTransactionProviderImpl(
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
      startFingerprintEnrollmentTransactionProvider = startFingerprintEnrollmentTransactionProvider,
      helpCenterUiStateMachine = helpCenterUiStateMachine
    )

  private val createKeyboxUiStateMachine =
    CreateKeyboxUiStateMachineImpl(
      pairNewHardwareUiStateMachine = pairNewHardwareUiStateMachine
    )

  val fiatCurrencyDefinitionService = FiatCurrencyDefinitionServiceImpl(appComponent.f8eHttpClient)
  val fiatCurrencyRepository =
    FiatCurrencyRepositoryImpl(
      fiatCurrencyDao = appComponent.fiatCurrencyDao,
      fiatCurrencyDefinitionService = fiatCurrencyDefinitionService
    )

  val currencyPreferenceUiStateMachine =
    CurrencyPreferenceUiStateMachineImpl(
      currencyConverter = currencyConverter,
      fiatCurrencyRepository = fiatCurrencyRepository,
      moneyDisplayFormatter = moneyDisplayFormatter
    )

  val onboardKeyboxUiStateMachine =
    OnboardKeyboxUiStateMachineImpl(
      fullAccountCloudSignInAndBackupUiStateMachine = fullAccountCloudSignInAndBackupUiStateMachine,
      notificationsFlowV2EnabledFeatureFlag = appComponent.notificationsFlowV2EnabledFeatureFlag,
      notificationPreferencesSetupUiStateMachine = notificationPreferencesSetupUiStateMachine,
      notificationPreferencesSetupUiStateMachineV2 = notificationPreferencesSetupUiStateMachineV2
    )

  val deleteOnboardingFullAccountService =
    DeleteOnboardingFullAccountServiceImpl(appComponent.f8eHttpClient)

  val onboardingFullAccountDeleter =
    OnboardingFullAccountDeleterImpl(
      accountRepository = appComponent.accountRepository,
      keyboxDao = appComponent.keyboxDao,
      deleteOnboardingFullAccountService = deleteOnboardingFullAccountService
    )

  val liteToFullAccountUpgrader =
    LiteToFullAccountUpgraderImpl(
      keyboxDao = appComponent.keyboxDao,
      accountAuthenticator = appComponent.accountAuthenticator,
      authTokenDao = appComponent.authTokenDao,
      deviceTokenManager = appComponent.deviceTokenManager,
      upgradeAccountService = upgradeAccountService,
      uuid = appComponent.uuid
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
      uuid = appComponent.uuid,
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
      deviceInfoProvider = appComponent.deviceInfoProvider,
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
      nfcTransactor = nfcTransactor
    )

  val fwupNfcUiStateMachine =
    FwupNfcUiStateMachineImpl(
      deviceInfoProvider = appComponent.deviceInfoProvider,
      fwupNfcSessionUiStateMachine = fwupNfcSessionUiStateMachine,
      deviceOs = appComponent.deviceOs
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

  val lightningOptionsStateMachine =
    LightningOptionsUiStateMachineImpl(
      lightningPreference = lightningPreference,
      lightningIsAvailableFeatureFlag = appComponent.lightningIsAvailableFeatureFlag
    )

  val firmwareMetadataStateMachine =
    FirmwareMetadataUiStateMachineImpl(
      firmwareDeviceInfoDao = appComponent.firmwareDeviceInfoDao,
      firmwareMetadataDao = appComponent.firmwareMetadataDao,
      nfcSessionUIStateMachine = nfcSessionUIStateMachine
    )

  val accountConfigStateMachine =
    AccountConfigUiStateMachineImpl(
      appVariant = appComponent.appVariant
    )

  val f8eEnvironmentPickerStateMachine =
    F8eEnvironmentPickerUiStateMachineImpl(
      appVariant = appComponent.appVariant
    )

  val bitcoinNetworkPickerStateMachine =
    BitcoinNetworkPickerUiStateMachineImpl(
      appVariant = appComponent.appVariant
    )

  val mobilePayLimitSetter =
    MobilePayLimitSetterImpl(
      spendingLimitDao = spendingLimitDao,
      mobilePaySpendingLimitService = spendingLimitService
    )

  val setSpendingLimitUiStateMachine =
    SetSpendingLimitUiStateMachineImpl(
      spendingLimitPickerUiStateMachine = spendingLimitPickerUiStateMachine,
      timeZoneProvider = timeZoneProvider,
      moneyDisplayFormatter = moneyDisplayFormatter
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
      spendingLimitCardUiStateMachine = spendingLimitCardStateMachine
    )

  val analyticOptionsUiStateMachine =
    AnalyticsOptionsUiStateMachineImpl(
      appVariant = appComponent.appVariant
    )

  val analyticsStateMachine =
    AnalyticsUiStateMachineImpl(
      eventStore = appComponent.eventStore
    )

  val logsStateMachine =
    LogsUiStateMachineImpl(
      dateTimeFormatter = dateTimeFormatter,
      logStore = appComponent.logStore,
      timeZoneProvider = timeZoneProvider
    )

  val connectAndOpenChannelStateMachine =
    ConnectAndOpenChannelStateMachineImpl(
      ldkNodeService = appComponent.ldkNodeService,
      clipboard = clipboard
    )

  val sendReceiveStateMachine =
    LightningSendReceiveUiStateMachineImpl(
      ldkNodeService = appComponent.ldkNodeService,
      lightningInvoiceParser = lightningInvoiceParser,
      moneyDisplayFormatter = moneyDisplayFormatter
    )

  val lightningStateMachine =
    LightningDebugMenuUiStateMachineImpl(
      clipboard = clipboard,
      connectAndOpenChannelStateMachine = connectAndOpenChannelStateMachine,
      lightningSendReceiveUiStateMachine = sendReceiveStateMachine,
      ldkNodeService = appComponent.ldkNodeService
    )

  val delayNotifyHardwareRecoveryStarter =
    LostHardwareRecoveryStarterImpl(
      initiateAccountDelayNotifyService = initiateAccountDelayNotifyService,
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

  val feeOptionListUiStateMachine =
    FeeOptionListUiStateMachineImpl(
      feeOptionUiStateMachine = feeOptionUiStateMachine
    )

  val transactionRepository =
    TransactionRepositoryImpl(
      transactionDetailDao = appComponent.transactionDetailDao,
      exchangeRateDao = exchangeRateDao
    )

  val transferConfirmationStateMachine =
    TransferConfirmationUiStateMachineImpl(
      bitcoinBlockchain = bitcoinBlockchain,
      mobilePaySigningService = mobilePaySigningService,
      transactionDetailsCardUiStateMachine = transactionDetailsCardUiStateMachine,
      nfcSessionUIStateMachine = nfcSessionUIStateMachine,
      transactionPriorityPreference = transactionPriorityPreference,
      feeOptionListUiStateMachine = feeOptionListUiStateMachine,
      appSpendingWalletProvider = appComponent.appSpendingWalletProvider,
      transactionRepository = transactionRepository
    )

  val bitcoinTransactionFeeEstimator =
    BitcoinTransactionFeeEstimatorImpl(
      bitcoinFeeRateEstimator = bitcoinFeeRateEstimator,
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
      networkReachabilityProvider = appComponent.networkReachabilityProvider
    )

  val transactionDetailStateMachine =
    build.wallet.statemachine.transactions.TransactionDetailsUiStateMachineImpl(
      bitcoinExplorer = bitcoinExplorer,
      timeZoneProvider = timeZoneProvider,
      dateTimeFormatter = dateTimeFormatter,
      currencyConverter = currencyConverter,
      moneyDisplayFormatter = moneyDisplayFormatter,
      sendUiStateMachine = sendStateMachine,
      bitcoinTransactionFeeEstimator = bitcoinTransactionFeeEstimator,
      clock = appComponent.clock,
      durationFormatter = durationFormatter,
      eventTracker = appComponent.eventTracker
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
      notificationTouchpointDao = notificationTouchpointDao,
      onboardingKeyboxSealedCsekDao = onboardingKeyboxSealedCsekDao,
      onboardingKeyboxStepStateDao = onboardingKeyboxStepStateDao,
      onboardingKeyboxHardwareKeysDao = onboardingKeyboxHwAuthPublicKeyDao,
      spendingLimitDao = spendingLimitDao,
      transactionDetailDao = appComponent.transactionDetailDao,
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
      authSignatureStatusProvider = appComponent.f8eAuthSignatureStatusProvider
    )

  val deviceUpdateCardUiStateMachine =
    DeviceUpdateCardUiStateMachineImpl(
      eventTracker = appComponent.eventTracker
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

  val recoveryIncompleteDao = RecoveryIncompleteDaoImpl(appComponent.bitkeyDatabaseProvider)
  val postSocRecTaskRepository = PostSocRecTaskRepositoryImpl(recoveryIncompleteDao)

  val replaceHardwareCardUiStateMachine =
    ReplaceHardwareCardUiStateMachineImpl(
      postSocRecTaskRepository = postSocRecTaskRepository
    )

  val cloudBackupHealthCardUiStateMachine = CloudBackupHealthCardUiStateMachineImpl(
    cloudBackupHealthFeatureFlag = appComponent.cloudBackupHealthFeatureFlag,
    cloudBackupHealthRepository = cloudBackupHealthRepository
  )

  val cardListStateMachine =
    MoneyHomeCardsUiStateMachineImpl(
      deviceUpdateCardUiStateMachine = deviceUpdateCardUiStateMachine,
      gettingStartedCardUiStateMachine = gettingStartedCardStateMachine,
      hardwareRecoveryStatusCardUiStateMachine = hardwareRecoveryStatusCardUiStateMachine,
      recoveryContactCardsUiStateMachine = pendingInvitationsCardUiStateMachine,
      replaceHardwareCardUiStateMachine = replaceHardwareCardUiStateMachine,
      cloudBackupHealthCardUiStateMachine = cloudBackupHealthCardUiStateMachine
    )

  val deepLinkHandler =
    DeepLinkHandlerImpl(
      platformContext = appComponent.platformContext
    )

  val partnershipsTransferUiStateMachine =
    PartnershipsTransferUiStateMachineImpl(
      getTransferPartnerListService =
        GetTransferPartnerListServiceImpl(
          countryCodeGuesser,
          appComponent.f8eHttpClient
        ),
      getTransferRedirectService = GetTransferRedirectServiceImpl(appComponent.f8eHttpClient)
    )

  val partnershipsPurchaseUiStateMachine =
    PartnershipsPurchaseUiStateMachineImpl(
      moneyDisplayFormatter = moneyDisplayFormatter,
      getPurchaseOptionsService =
        GetPurchaseOptionsServiceImpl(
          countryCodeGuesser,
          appComponent.f8eHttpClient
        ),
      getPurchaseQuoteListService =
        GetPurchaseQuoteListServiceImpl(
          countryCodeGuesser,
          appComponent.f8eHttpClient
        ),
      getPurchaseRedirectService = GetPurchaseRedirectServiceImpl(appComponent.f8eHttpClient)
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

  val rotateAuthKeysService: RotateAuthKeysService = RotateAuthKeysServiceImpl(
    f8eHttpClient = appComponent.f8eHttpClient,
    signer = appComponent.appAuthKeyMessageSigner
  )

  val authKeyRotationManager: AuthKeyRotationManager = AuthKeyRotationManagerImpl(
    authKeyRotationAttemptDao = authKeyRotationAttemptDao,
    rotateAuthKeysService = rotateAuthKeysService,
    keyboxDao = appComponent.keyboxDao,
    accountAuthenticator = appComponent.accountAuthenticator,
    bestEffortFullAccountCloudBackupUploader = bestEffortFullAccountCloudBackupUploader,
    socRecRelationshipsRepository = socRecRelationshipsRepository
  )

  val rotateAuthUIStateMachine = RotateAuthKeyUIStateMachineImpl(
    appKeysGenerator = appComponent.appKeysGenerator,
    proofOfPossessionNfcStateMachine = proofOfPossessionNfcStateMachine,
    authKeyRotationManager = authKeyRotationManager,
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
      eventTracker = appComponent.eventTracker
    )
  val customAmountEntryUiStateMachine =
    CustomAmountEntryUiStateMachineImpl(
      moneyCalculatorUiStateMachine = moneyCalculatorUiStateMachine,
      moneyDisplayFormatter = moneyDisplayFormatter
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
      repairCloudBackupStateMachine = repairMobileKeyBackupUiStateMachine
    )

  val appStateDeleterOptionsUiStateMachine =
    AppStateDeleterOptionsUiStateMachineImpl(appComponent.appVariant)

  val booleanFlagItemStateMachine = BooleanFlagItemUiStateMachineImpl()

  val featureFlagsStateMachine =
    FeatureFlagsStateMachineImpl(
      allBooleanFeatureFlags = appComponent.allFeatureFlags,
      booleanFlagItemUiStateMachine = booleanFlagItemStateMachine
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

  val onboardingConfigStateMachine =
    OnboardingConfigStateMachineImpl(
      appVariant = appComponent.appVariant
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
      lightningOptionsUiStateMachine = lightningOptionsStateMachine,
      cloudSignUiStateMachine = cloudSignInUiStateMachine
    )

  val debugMenuStateMachine =
    DebugMenuStateMachineImpl(
      analyticsUiStateMachine = analyticsStateMachine,
      debugMenuListStateMachine = debugMenuListStateMachine,
      f8eCustomUrlStateMachine = F8eCustomUrlStateMachineImpl(),
      featureFlagsStateMachine = featureFlagsStateMachine,
      firmwareMetadataUiStateMachine = firmwareMetadataStateMachine,
      fwupNfcUiStateMachine = fwupNfcUiStateMachine,
      lightningDebugMenuUiStateMachine = lightningStateMachine,
      logsUiStateMachine = logsStateMachine,
      networkingDebugConfigPickerUiStateMachine = networkingDebugConfigPickerUiStateMachine,
      nfcSessionUIStateMachine = nfcSessionUIStateMachine,
      cloudDevOptionsStateMachine = cloudDevOptionsStateMachine
    )

  val demoModeService = DemoModeServiceImpl(
    appComponent.f8eHttpClient
  )
  val demoModeCodeEntryUiStateMachine = DemoModeCodeEntryUiStateMachineImpl(
    demoModeService = demoModeService
  )
  val demoModeConfigUiStateMachine = DemoModeConfigUiStateMachineImpl(
    demoModeCodeEntryUiStateMachine = demoModeCodeEntryUiStateMachine
  )

  val customElectrumServerUiStateMachine = CustomElectrumServerUiStateMachineImpl()

  val setElectrumServerUiStateMachine =
    SetElectrumServerUiStateMachineImpl(
      delayer = appComponent.delayer,
      electrumServerSettingProvider = appComponent.electrumServerSettingProvider,
      electrumReachability = appComponent.electrumReachability
    )

  val customElectrumServerSettingUiStateMachine =
    CustomElectrumServerSettingUiStateMachineImpl(
      customElectrumServerUIStateMachine = customElectrumServerUiStateMachine,
      setElectrumServerUiStateMachine = setElectrumServerUiStateMachine
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
      appFunctionalityStatusProvider = appFunctionalityStatusProvider
    )

  val oldFeedbackUiStateMachine =
    OldFeedbackUiStateMachineImpl(
      inAppBrowserNavigator = inAppBrowserNavigator
    )

  val customerFeedbackService =
    SupportTicketServiceImpl(
      f8eHttpClient = appComponent.f8eHttpClient
    )

  val supportTicketRepository =
    SupportTicketRepositoryImpl(
      supportTicketService = customerFeedbackService,
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
      inAppBrowserNavigator = inAppBrowserNavigator,
      feedbackFormAddAttachments = appComponent.feedbackFormAddAttachmentsFeatureFlag
    )

  val feedbackUiStateMachine =
    FeedbackUiStateMachineImpl(
      supportTicketRepository = supportTicketRepository,
      feedbackFormNewUiEnabled = appComponent.feedbackFormNewUiEnabledFeatureFlag,
      feedbackFormAddAttachments = appComponent.feedbackFormAddAttachmentsFeatureFlag,
      feedbackFormUiStateMachine = feedbackFormUiStateMachine,
      oldFeedbackUiStateMachine = oldFeedbackUiStateMachine
    )

  val settingsListUiStateMachine =
    SettingsListUiStateMachineImpl(
      appFunctionalityStatusProvider = appFunctionalityStatusProvider,
      cloudBackupHealthFeatureFlag = appComponent.cloudBackupHealthFeatureFlag,
      inactiveDeviceIsEnabledFeatureFlag = appComponent.inactiveDeviceIsEnabledFeatureFlag,
      notificationsFlowV2EnabledFeatureFlag = appComponent.notificationsFlowV2EnabledFeatureFlag
    )
  val cloudBackupHealthDashboardUiStateMachine = CloudBackupHealthDashboardUiStateMachineImpl(
    uuid = appComponent.uuid,
    cloudBackupHealthRepository = cloudBackupHealthRepository,
    dateTimeFormatter = dateTimeFormatter,
    timeZoneProvider = timeZoneProvider,
    repairCloudBackupStateMachine = repairMobileKeyBackupUiStateMachine,
    cloudBackupDao = cloudBackupDao,
    emergencyAccessKitPdfGenerator = emergencyAccessKitPdfGenerator,
    sharingManager = sharingManager
  )

  val settingsStateMachine =
    SettingsHomeUiStateMachineImpl(
      mobilePaySettingsUiStateMachine = mobilePaySettingsUiStateMachine,
      notificationPreferencesUiStateMachine = notificationPreferencesUiStateMachine,
      notificationsSettingsUiStateMachine = notificationsSettingsStateMachine,
      recoveryChannelSettingsUiStateMachine = recoveryChannelSettingsUiStateMachineImpl,
      currencyPreferenceUiStateMachine = currencyPreferenceUiStateMachine,
      customElectrumServerSettingUiStateMachine = customElectrumServerSettingUiStateMachine,
      deviceSettingsUiStateMachine = deviceSettingsUiStateMachine,
      feedbackUiStateMachine = feedbackUiStateMachine,
      helpCenterUiStateMachine = helpCenterUiStateMachine,
      trustedContactManagementUiStateMachine = trustedContactManagementUiStateMachine,
      settingsListUiStateMachine = settingsListUiStateMachine,
      cloudBackupHealthDashboardUiStateMachine = cloudBackupHealthDashboardUiStateMachine,
      rotateAuthKeyUIStateMachine = rotateAuthUIStateMachine
    )

  val homeUiBottomSheetStateMachine =
    HomeUiBottomSheetStateMachineImpl(
      homeUiBottomSheetDao = homeUiBottomSheetDao
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

  val homeUiStateMachine =
    HomeUiStateMachineImpl(
      appFunctionalityStatusUiStateMachine = appFunctionalityStatusUiStateMachine,
      currencyConverter = currencyConverter,
      currencyChangeMobilePayBottomSheetUpdater = currencyChangeMobilePayBottomSheetUpdater,
      homeStatusBannerUiStateMachine = homeStatusBannerUiStateMachine,
      homeUiBottomSheetStateMachine = homeUiBottomSheetStateMachine,
      moneyHomeUiStateMachine = moneyHomeStateMachine,
      settingsHomeUiStateMachine = settingsStateMachine,
      setSpendingLimitUiStateMachine = setSpendingLimitUiStateMachine,
      trustedContactEnrollmentUiStateMachine = trustedContactEnrollmentUiStateMachine,
      socRecRelationshipsRepository = socRecRelationshipsRepository,
      cloudBackupHealthRepository = cloudBackupHealthRepository,
      appFunctionalityStatusProvider = appFunctionalityStatusProvider
    )

  val chooseAccountAccessUiStateMachine =
    ChooseAccountAccessUiStateMachineImpl(
      appVariant = appComponent.appVariant,
      demoModeConfigUiStateMachine = demoModeConfigUiStateMachine,
      debugMenuStateMachine = debugMenuStateMachine,
      deviceInfoProvider = appComponent.deviceInfoProvider
    )

  val mobilePayDataStateMachine =
    MobilePayDataStateMachineImpl(
      mobilePayStatusProvider = mobilePayStatusProvider,
      mobilePayLimitSetter = mobilePayLimitSetter,
      mobilePayDisabler = mobilePayDisabler,
      eventTracker = appComponent.eventTracker,
      currencyConverter = currencyConverter
    )

  val fullAccountAddressDataStateMachine =
    FullAccountAddressDataStateMachineImpl(
      registerWatchAddressProcessor = registerWatchAddressProcessor,
      appSpendingWalletProvider = appComponent.appSpendingWalletProvider
    )

  val fullAccountTransactionsDataStateMachine = FullAccountTransactionsDataStateMachineImpl()

  val onboardConfigDao =
    OnboardingStepSkipConfigDaoImpl(
      databaseProvider = appComponent.bitkeyDatabaseProvider
    )

  val onboardConfigDataStateMachine =
    OnboardConfigDataStateMachineImpl(
      onboardingStepSkipConfigDao = onboardConfigDao
    )

  val notificationTouchpointDataStateMachine =
    NotificationTouchpointDataStateMachineImpl(
      notificationTouchpointDao = notificationTouchpointDao,
      notificationTouchpointService = notificationTouchpointService
    )

  val firmwareDataStateMachine =
    FirmwareDataStateMachineImpl(
      firmwareDeviceNotFoundEnabledFeatureFlag = appComponent.firmwareDeviceNotFoundEnabledFeatureFlag,
      firmwareDeviceInfoDao = appComponent.firmwareDeviceInfoDao,
      fwupDataFetcher = appComponent.fwupDataFetcher,
      fwupDataDao = appComponent.fwupDataDao
    )

  val recoveryNotificationVerificationService =
    RecoveryNotificationVerificationServiceImpl(
      f8eHttpClient = appComponent.f8eHttpClient
    )

  val recoveryNotificationVerificationDataStateMachine =
    RecoveryNotificationVerificationDataStateMachineImpl(
      notificationTouchpointService = notificationTouchpointService,
      recoveryNotificationVerificationService = recoveryNotificationVerificationService
    )

  val initiatingLostAppRecoveryStateMachine =
    InitiatingLostAppRecoveryDataStateMachineImpl(
      appKeysGenerator = appKeysGenerator,
      initiateHardwareAuthService = initiateHardwareAuthService,
      listKeysetsService = listKeysetsService,
      lostAppRecoveryInitiator = delayNotifyLostAppRecoveryInitiator,
      lostAppRecoveryAuthenticator = delayNotifyLostAppRecoveryAuthenticator,
      recoveryNotificationVerificationDataStateMachine = recoveryNotificationVerificationDataStateMachine,
      cancelDelayNotifyRecoveryService = cancelDelayNotifyRecoveryService,
      uuid = appComponent.uuid
    )

  val lostAppRecoveryHaveNotStartedDataStateMachine =
    LostAppRecoveryHaveNotStartedDataStateMachineImpl(
      initiatingLostAppRecoveryDataStateMachine = initiatingLostAppRecoveryStateMachine
    )

  val lostAppRecoveryCanceler =
    RecoveryCancelerImpl(
      cancelDelayNotifyRecoveryService = cancelDelayNotifyRecoveryService,
      recoverySyncer = recoverySyncer
    )

  val recoveryAuthCompleter =
    RecoveryAuthCompleterImpl(
      appAuthKeyMessageSigner = appComponent.appAuthKeyMessageSigner,
      completeDelayNotifyService = completeDelayNotifyService,
      accountAuthenticator = appComponent.accountAuthenticator,
      recoverySyncer = recoverySyncer,
      authTokenDao = appComponent.authTokenDao,
      socRecRelationshipsRepository = socRecRelationshipsRepository
    )

  val sweepGenerator =
    SweepGeneratorImpl(
      listKeysetsService = listKeysetsService,
      bitcoinFeeRateEstimator = bitcoinFeeRateEstimator,
      appPrivateKeyDao = appComponent.appPrivateKeyDao,
      keysetWalletProvider = appComponent.keysetWalletProvider,
      registerWatchAddressProcessor = registerWatchAddressProcessor
    )

  val sweepDataStateMachine =
    SweepDataStateMachineImpl(
      bitcoinBlockchain = bitcoinBlockchain,
      sweepGenerator = sweepGenerator,
      mobilePaySigningService = mobilePaySigningService,
      appSpendingWalletProvider = appComponent.appSpendingWalletProvider,
      exchangeRateSyncer = exchangeRateSyncer,
      transactionRepository = transactionRepository
    )

  val f8eSpendingKeyRotator =
    F8eSpendingKeyRotatorImpl(
      createAccountKeysetService = createAccountKeysetService,
      setActiveSpendingKeysetService = setActiveSpendingKeysetService
    )

  val trustedContactKeyAuthenticator = TrustedContactKeyAuthenticatorImpl(
    socRecRelationshipsRepository = socRecRelationshipsRepository,
    socRecRelationshipsDao = socRecRelationshipsDao,
    socRecEnrollmentAuthenticationDao = socRecEnrollmentAuthenticationDao,
    socRecCrypto = socRecCrypto,
    endorseTrustedContactsServiceProvider = { socialRecoveryServiceProvider.get() }
  )

  val recoveryInProgressDataStateMachine =
    RecoveryInProgressDataStateMachineImpl(
      recoveryCanceler = lostAppRecoveryCanceler,
      clock = appComponent.clock,
      csekGenerator = csekGenerator,
      csekDao = csekDao,
      recoveryAuthCompleter = recoveryAuthCompleter,
      sweepDataStateMachine = sweepDataStateMachine,
      f8eSpendingKeyRotator = f8eSpendingKeyRotator,
      uuid = appComponent.uuid,
      recoverySyncer = recoverySyncer,
      recoveryNotificationVerificationDataStateMachine = recoveryNotificationVerificationDataStateMachine,
      accountAuthenticator = appComponent.accountAuthenticator,
      recoveryDao = appComponent.recoveryDao,
      deviceTokenManager = appComponent.deviceTokenManager,
      deviceInfoProvider = appComponent.deviceInfoProvider,
      socRecRelationshipsRepository = socRecRelationshipsRepository,
      postSocRecTaskRepository = postSocRecTaskRepository,
      trustedContactKeyAuthenticator = trustedContactKeyAuthenticator
    )

  val createKeyboxDataStateMachine =
    CreateKeyboxDataStateMachineImpl(
      fullAccountCreator = fullAccountCreator,
      appKeysGenerator = appKeysGenerator,
      onboardingKeyboxSealedCsekDao = onboardingKeyboxSealedCsekDao,
      onboardingKeyboxHardwareKeysDao = onboardingKeyboxHwAuthPublicKeyDao,
      uuid = appComponent.uuid,
      onboardingAppKeyKeystore = appComponent.onboardingAppKeyKeystore,
      liteToFullAccountUpgrader = liteToFullAccountUpgrader
    )

  val onboardKeyboxDataStateMachine =
    OnboardKeyboxDataStateMachineImpl(
      onboardingKeyboxSealedCsekDao = onboardingKeyboxSealedCsekDao,
      onboardingKeyboxStepStateDao = onboardingKeyboxStepStateDao
    )

  val activateFullAccountDataStateMachine =
    ActivateFullAccountDataStateMachineImpl(
      eventTracker = appComponent.eventTracker,
      gettingStartedTaskDao = gettingStartedTaskDao,
      keyboxDao = appComponent.keyboxDao,
      onboardingKeyboxStepStateDao = onboardingKeyboxStepStateDao,
      onboardingService = onboardingService,
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
      lostHardwareRecoveryStarter = delayNotifyHardwareRecoveryStarter,
      recoveryNotificationVerificationDataStateMachine = recoveryNotificationVerificationDataStateMachine,
      cancelDelayNotifyRecoveryService = cancelDelayNotifyRecoveryService
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
      uuid = appComponent.uuid,
      cloudBackupDao = cloudBackupDao,
      recoveryChallengeStateMachine = recoveryChallengeUiStateMachine,
      socRecChallengeRepository = socRecChallengeRepository,
      socialRelationshipsRepository = socRecRelationshipsRepository,
      postSocRecTaskRepository = postSocRecTaskRepository,
      socRecStartedChallengeDao = socRecPendingChallengeDao,
      authKeyRotationManager = authKeyRotationManager,
      inactiveDeviceIsEnabledFeatureFlag = appComponent.inactiveDeviceIsEnabledFeatureFlag
    )

  val lostAppRecoveryHaveNotStartedUiStateMachine =
    LostAppRecoveryHaveNotStartedUiStateMachineImpl(
      initiatingLostAppRecoveryUiStateMachine = initiatingLostAppRecoveryUiStateMachineImpl,
      accessCloudBackupUiStateMachine = accessCloudBackupUiStateMachine,
      fullAccountCloudBackupRestorationUiStateMachine = fullAccountCloudBackupRestorationUiStateMachine
    )

  val recoveringKeyboxUiStateMachine =
    LostAppRecoveryUiStateMachineImpl(
      lostAppRecoveryHaveNotStartedDataStateMachine = lostAppRecoveryHaveNotStartedUiStateMachine,
      recoveryInProgressUiStateMachine = recoveryInProgressUiStateMachine
    )

  val cloudBackupRefresher =
    CloudBackupRefresherImpl(
      socRecRelationshipsRepository = socRecRelationshipsRepository,
      cloudBackupDao = cloudBackupDao,
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      cloudBackupRepository = cloudBackupRepository,
      fullAccountCloudBackupCreator = fullAccountCloudBackupCreator,
      eventTracker = appComponent.eventTracker,
      clock = appComponent.clock
    )

  val hasActiveFullAccountDataStateMachine =
    HasActiveFullAccountDataStateMachineImpl(
      mobilePayDataStateMachine = mobilePayDataStateMachine,
      fullAccountAddressDataStateMachine = fullAccountAddressDataStateMachine,
      fullAccountTransactionsDataStateMachine = fullAccountTransactionsDataStateMachine,
      notificationTouchpointDataStateMachine = notificationTouchpointDataStateMachine,
      lostHardwareRecoveryDataStateMachine = lostHardwareRecoveryDataStateMachine,
      appSpendingWalletProvider = appComponent.appSpendingWalletProvider,
      exchangeRateSyncer = exchangeRateSyncer,
      cloudBackupRefresher = cloudBackupRefresher,
      postSocRecTaskRepository = postSocRecTaskRepository,
      authKeyRotationManager = authKeyRotationManager,
      inactiveDeviceIsEnabledFeatureFlag = appComponent.inactiveDeviceIsEnabledFeatureFlag,
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
      cancelDelayNotifyRecoveryService = cancelDelayNotifyRecoveryService,
      recoveryNotificationVerificationDataStateMachine = recoveryNotificationVerificationDataStateMachine,
      recoverySyncer = recoverySyncer
    )

  val hasActiveLiteAccountDataStateMachine =
    HasActiveLiteAccountDataStateMachineImpl(
      createFullAccountDataStateMachine = createFullAccountDataStateMachine,
      keyboxDao = appComponent.keyboxDao
    )

  val accountDataStateMachine =
    AccountDataStateMachineImpl(
      hasActiveFullAccountDataStateMachine = hasActiveFullAccountDataStateMachine,
      hasActiveLiteAccountDataStateMachine = hasActiveLiteAccountDataStateMachine,
      noActiveAccountDataStateMachine = noActiveAccountDataStateMachine,
      accountRepository = appComponent.accountRepository,
      recoverySyncer = recoverySyncer,
      noLongerRecoveringDataStateMachine = noLongerRecoveringDataStateMachine,
      someoneElseIsRecoveringDataStateMachine = someoneElseIsRecoveringDataStateMachine,
      recoverySyncFrequency = appComponent.recoverySyncFrequency,
      onboardConfigDataStateMachine = onboardConfigDataStateMachine
    )

  val lightningNodeDataStateMachine =
    LightningNodeDataStateMachineImpl(
      lightningIsAvailableFeatureFlag = appComponent.lightningIsAvailableFeatureFlag,
      lightningPreference = lightningPreference,
      ldkNodeService = appComponent.ldkNodeService
    )

  val templateFullAccountConfigDataStateMachine =
    TemplateFullAccountConfigDataStateMachineImpl(
      templateFullAccountConfigDao = appComponent.templateFullAccountConfigDao
    )

  val electrumServerDataStateMachine =
    ElectrumServerDataStateMachineImpl(
      electrumServerRepository = appComponent.electrumServerDao,
      getBdkConfigurationService =
        GetBdkConfigurationServiceImpl(
          appComponent.f8eHttpClient,
          appComponent.deviceInfoProvider
        )
    )

  val currencyPreferenceDataStateMachine =
    CurrencyPreferenceDataStateMachineImpl(
      bitcoinDisplayPreferenceRepository = appComponent.bitcoinDisplayPreferenceRepository,
      eventTracker = appComponent.eventTracker,
      fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository
    )

  val appDataStateMachine =
    AppDataStateMachineImpl(
      eventTracker = appComponent.eventTracker,
      appInstallationDao = appComponent.appInstallationDao,
      featureFlagInitializer = appComponent.featureFlagInitializer,
      accountDataStateMachine = accountDataStateMachine,
      periodicEventProcessor = appComponent.periodicEventProcessor,
      periodicFirmwareTelemetryProcessor = appComponent.periodicFirmwareTelemetryEventProcessor,
      periodicFirmwareCoredumpProcessor = appComponent.periodicFirmwareCoredumpProcessor,
      periodicRegisterWatchAddressProcessor = registerWatchAddressProcessor,
      lightningNodeDataStateMachine = lightningNodeDataStateMachine,
      templateFullAccountConfigDataStateMachine = templateFullAccountConfigDataStateMachine,
      electrumServerDataStateMachine = electrumServerDataStateMachine,
      firmwareDataStateMachine = firmwareDataStateMachine,
      currencyPreferenceDataStateMachine = currencyPreferenceDataStateMachine,
      networkingDebugConfigRepository = appComponent.networkingDebugConfigRepository,
      bitcoinDisplayPreferenceRepository = appComponent.bitcoinDisplayPreferenceRepository,
      fiatCurrencyRepository = fiatCurrencyRepository,
      fiatCurrencyPreferenceRepository = appComponent.fiatCurrencyPreferenceRepository,
      fiatMobilePayConfigurationRepository = fiatMobilePayConfigurationRepository,
      emergencyAccessKitDataProvider = emergencyAccessKitDataProvider
    )

  val liteAccountCreator =
    LiteAccountCreatorImpl(
      accountAuthenticator = appComponent.accountAuthenticator,
      accountRepository = appComponent.accountRepository,
      appKeysGenerator = appComponent.appKeysGenerator,
      authTokenDao = appComponent.authTokenDao,
      createLiteAccountService = createAccountService
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
      eventTracker = appComponent.eventTracker
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
      currencyPreferenceUiStateMachine = currencyPreferenceUiStateMachine,
      helpCenterUiStateMachine = helpCenterUiStateMachine,
      liteTrustedContactManagementUiStateMachine = liteTrustedContactManagementUiStateMachine,
      settingsListUiStateMachine = settingsListUiStateMachine,
      feedbackUiStateMachine = feedbackUiStateMachine
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
      uuid = appComponent.uuid
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
      authKeyRotationUiStateMachine = rotateAuthUIStateMachine
    )
}
