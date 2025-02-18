package build.wallet.di

import build.wallet.account.AccountService
import build.wallet.auth.AuthTokensService
import build.wallet.auth.FullAccountAuthKeyRotationService
import build.wallet.auth.LiteAccountCreator
import build.wallet.availability.F8eNetworkReachabilityService
import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitcoin.blockchain.BitcoinBlockchain
import build.wallet.bitcoin.export.ExportTransactionsService
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimator
import build.wallet.bitcoin.keys.ExtendedKeyGenerator
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.bitcoin.utxo.UtxoConsolidationService
import build.wallet.bitcoin.wallet.SpendingWalletProvider
import build.wallet.cloud.backup.CloudBackupRepository
import build.wallet.cloud.backup.FullAccountCloudBackupCreator
import build.wallet.cloud.backup.LiteAccountCloudBackupCreator
import build.wallet.cloud.backup.csek.CsekGenerator
import build.wallet.cloud.backup.local.CloudBackupDao
import build.wallet.cloud.backup.socrec.SocRecCloudBackupSyncWorker
import build.wallet.cloud.store.CloudFileStore
import build.wallet.cloud.store.CloudKeyValueStore
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.WritableCloudStoreAccountRepository
import build.wallet.debug.AppDataDeleter
import build.wallet.debug.DebugOptionsService
import build.wallet.debug.cloud.CloudBackupDeleter
import build.wallet.encrypt.MessageSigner
import build.wallet.encrypt.Secp256k1KeyGenerator
import build.wallet.f8e.debug.NetworkingDebugService
import build.wallet.f8e.mobilepay.MobilePaySigningF8eClient
import build.wallet.f8e.notifications.NotificationTouchpointF8eClient
import build.wallet.f8e.onboarding.CreateAccountKeysetF8eClient
import build.wallet.f8e.recovery.UpdateDelayNotifyPeriodForTestingApi
import build.wallet.feature.FeatureFlagService
import build.wallet.feature.flags.InheritanceFeatureFlag
import build.wallet.feature.flags.SoftwareWalletIsEnabledFeatureFlag
import build.wallet.home.GettingStartedTaskDao
import build.wallet.keybox.KeyboxDao
import build.wallet.keybox.keys.AppKeysGenerator
import build.wallet.keybox.keys.OnboardingAppKeyKeystore
import build.wallet.keybox.wallet.AppSpendingWalletProvider
import build.wallet.keybox.wallet.KeysetWalletProvider
import build.wallet.limit.MobilePayService
import build.wallet.logging.LoggerInitializer
import build.wallet.nfc.FakeHardwareKeyStore
import build.wallet.nfc.NfcCommandsFake
import build.wallet.nfc.transaction.PairingTransactionProvider
import build.wallet.onboarding.*
import build.wallet.partnerships.PartnershipPurchaseService
import build.wallet.partnerships.PartnershipTransactionsService
import build.wallet.platform.app.AppSessionManager
import build.wallet.platform.data.FileDirectoryProvider
import build.wallet.platform.permissions.PushNotificationPermissionStatusProvider
import build.wallet.platform.sharing.SharingManagerFake
import build.wallet.recovery.RecoveryDao
import build.wallet.recovery.RecoverySyncer
import build.wallet.recovery.socrec.*
import build.wallet.relationships.*
import build.wallet.statemachine.data.keybox.AccountDataStateMachine
import build.wallet.statemachine.data.recovery.sweep.SweepDataStateMachine
import build.wallet.store.EncryptedKeyValueStoreFactory
import build.wallet.worker.AppWorkerExecutor
import kotlinx.coroutines.CoroutineScope

/**
 * These dependencies are exposed to be accessed by JVM test code at runtime, for testing purposes.
 *
 * In order to access a different/new dependency from JVM test code, add it here - assuming the
 * dependency is provided using kotlin-inject annotations (``, etc).
 *
 * The actual component implementation is in [JvmAppComponentImpl].
 */
interface JvmAppComponent {
  val accountDataStateMachine: AccountDataStateMachine
  val accountService: AccountService
  val appCoroutineScope: CoroutineScope
  val appDataDeleter: AppDataDeleter
  val appKeysGenerator: AppKeysGenerator
  val appPrivateKeyDao: AppPrivateKeyDao
  val appSessionManager: AppSessionManager
  val appSpendingWalletProvider: AppSpendingWalletProvider
  val appWorkerExecutor: AppWorkerExecutor
  val authTokensService: AuthTokensService
  val bitcoinBlockchain: BitcoinBlockchain
  val bitcoinWalletService: BitcoinWalletService
  val cloudBackupDao: CloudBackupDao
  val cloudBackupDeleter: CloudBackupDeleter
  val socRecCloudBackupSyncWorker: SocRecCloudBackupSyncWorker
  val cloudBackupRepository: CloudBackupRepository
  val cloudFileStore: CloudFileStore
  val cloudKeyValueStore: CloudKeyValueStore
  val cloudStoreAccountRepository: CloudStoreAccountRepository
  val createAccountKeysetF8eClient: CreateAccountKeysetF8eClient
  val createFullAccountService: CreateFullAccountService
  val createSoftwareWalletService: CreateSoftwareWalletService
  val csekGenerator: CsekGenerator
  val debugOptionsService: DebugOptionsService
  val delegatedDecryptionKeyService: DelegatedDecryptionKeyService
  val exportTransactionsService: ExportTransactionsService
  val extendedKeyGenerator: ExtendedKeyGenerator
  val f8eNetworkReachabilityService: F8eNetworkReachabilityService
  val fakeHardwareKeyStore: FakeHardwareKeyStore
  val fakeNfcCommands: NfcCommandsFake
  val featureFlagService: FeatureFlagService
  val fileDirectoryProvider: FileDirectoryProvider
  val fullAccountAuthKeyRotationService: FullAccountAuthKeyRotationService
  val fullAccountCloudBackupCreator: FullAccountCloudBackupCreator
  val gettingStartedTaskDao: GettingStartedTaskDao
  val inheritanceFeatureFlag: InheritanceFeatureFlag
  val keyboxDao: KeyboxDao
  val keysetWalletProvider: KeysetWalletProvider
  val liteAccountCloudBackupCreator: LiteAccountCloudBackupCreator
  val liteAccountCreator: LiteAccountCreator
  val loggerInitializer: LoggerInitializer
  val messageSigner: MessageSigner
  val mobilePayService: MobilePayService
  val mobilePaySigningF8eClient: MobilePaySigningF8eClient
  val networkingDebugService: NetworkingDebugService
  val notificationTouchpointF8eClient: NotificationTouchpointF8eClient
  val onboardAccountService: OnboardAccountService
  val onboardingAppKeyKeystore: OnboardingAppKeyKeystore
  val onboardingKeyboxHwAuthPublicKeyDao: OnboardingKeyboxHardwareKeysDao
  val pairingTransactionProvider: PairingTransactionProvider
  val partnershipPurchaseService: PartnershipPurchaseService
  val partnershipTransactionsService: PartnershipTransactionsService
  val pushNotificationPermissionStatusProvider: PushNotificationPermissionStatusProvider
  val recoveryDao: RecoveryDao
  val recoverySyncer: RecoverySyncer
  val relationshipsCodeBuilder: RelationshipsCodeBuilder
  val relationshipsCrypto: RelationshipsCrypto
  val relationshipsCryptoFake: RelationshipsCryptoFake
  val relationshipsDao: RelationshipsDao
  val relationshipsEnrollmentAuthenticationDao: RelationshipsEnrollmentAuthenticationDao
  val relationshipsF8eClientProvider: RelationshipsF8eClientProvider
  val relationshipsKeysRepository: RelationshipsKeysRepository
  val relationshipsService: RelationshipsService
  val secp256k1KeyGenerator: Secp256k1KeyGenerator
  val secureStoreFactory: EncryptedKeyValueStoreFactory
  val sharingManager: SharingManagerFake
  val socRecChallengeRepository: SocRecChallengeRepository
  val socRecF8eClientProvider: SocRecF8eClientProvider
  val socRecService: SocRecService
  val socRecStartedChallengeAuthenticationDao: SocRecStartedChallengeAuthenticationDao
  val socRecStartedChallengeDao: SocRecStartedChallengeDao
  val softwareWalletIsEnabledFeatureFlag: SoftwareWalletIsEnabledFeatureFlag
  val softwareWalletSigningService: SoftwareWalletSigningService
  val spendingWalletProvider: SpendingWalletProvider
  val sweepDataStateMachine: SweepDataStateMachine
  val updateDelayNotifyPeriodForTestingApi: UpdateDelayNotifyPeriodForTestingApi
  val utxoConsolidationService: UtxoConsolidationService
  val writableCloudStoreAccountRepository: WritableCloudStoreAccountRepository
  val bitcoinFeeRateEstimator: BitcoinFeeRateEstimator
}
