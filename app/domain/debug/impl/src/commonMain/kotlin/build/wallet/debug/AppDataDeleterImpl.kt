package build.wallet.debug

import bitkey.firmware.HardwareUnlockInfoService
import bitkey.metrics.MetricTrackerService
import bitkey.securitycenter.SecurityRecommendationInteractionDao
import build.wallet.account.AccountService
import build.wallet.auth.AuthKeyRotationAttemptDao
import build.wallet.auth.AuthTokensService
import build.wallet.availability.F8eAuthSignatureStatusProvider
import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitcoin.transactions.OutgoingTransactionDetailDao
import build.wallet.bitcoin.transactions.TransactionPriorityPreference
import build.wallet.cloud.backup.csek.CsekDao
import build.wallet.cloud.backup.local.CloudBackupDao
import build.wallet.coachmark.CoachmarkService
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.firmware.FirmwareMetadataDao
import build.wallet.fwup.FwupDataDaoProvider
import build.wallet.home.GettingStartedTaskDao
import build.wallet.inappsecurity.BiometricPreference
import build.wallet.inappsecurity.HideBalancePreference
import build.wallet.inheritance.InheritanceClaimsDao
import build.wallet.keybox.KeyboxDao
import build.wallet.keybox.keys.OnboardingAppKeyKeystore
import build.wallet.limit.MobilePayService
import build.wallet.logging.logDebug
import build.wallet.money.display.BitcoinDisplayPreferenceRepository
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.notifications.NotificationTouchpointDao
import build.wallet.onboarding.OnboardingKeyboxHardwareKeysDao
import build.wallet.onboarding.OnboardingKeyboxSealedCsekDao
import build.wallet.onboarding.OnboardingKeyboxStepStateDao
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.recovery.RecoveryDao
import build.wallet.recovery.socrec.SocRecStartedChallengeDao
import build.wallet.relationships.RelationshipsKeysDao
import build.wallet.relationships.RelationshipsService
import com.github.michaelbull.result.coroutines.coroutineBinding

@BitkeyInject(AppScope::class)
class AppDataDeleterImpl(
  private val appVariant: AppVariant,
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val accountService: AccountService,
  private val authTokensService: AuthTokensService,
  private val gettingStartedTaskDao: GettingStartedTaskDao,
  private val keyboxDao: KeyboxDao,
  private val notificationTouchpointDao: NotificationTouchpointDao,
  private val onboardingKeyboxSealedCsekDao: OnboardingKeyboxSealedCsekDao,
  private val onboardingKeyboxStepStateDao: OnboardingKeyboxStepStateDao,
  private val onboardingKeyboxHardwareKeysDao: OnboardingKeyboxHardwareKeysDao,
  private val mobilePayService: MobilePayService,
  private val outgoingTransactionDetailDao: OutgoingTransactionDetailDao,
  private val fwupDataDaoProvider: FwupDataDaoProvider,
  private val firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
  private val firmwareMetadataDao: FirmwareMetadataDao,
  private val transactionPriorityPreference: TransactionPriorityPreference,
  private val onboardingAppKeyKeystore: OnboardingAppKeyKeystore,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val bitcoinDisplayPreferenceRepository: BitcoinDisplayPreferenceRepository,
  private val cloudBackupDao: CloudBackupDao,
  private val relationshipsKeysDao: RelationshipsKeysDao,
  private val relationshipsService: RelationshipsService,
  private val socRecStartedChallengeDao: SocRecStartedChallengeDao,
  private val csekDao: CsekDao,
  private val authKeyRotationAttemptDao: AuthKeyRotationAttemptDao,
  private val recoveryDao: RecoveryDao,
  private val authSignatureStatusProvider: F8eAuthSignatureStatusProvider,
  private val hideBalancePreference: HideBalancePreference,
  private val biometricPreference: BiometricPreference,
  private val inheritanceClaimsDao: InheritanceClaimsDao,
  private val metricTrackerService: MetricTrackerService,
  private val hardwareUnlockInfoService: HardwareUnlockInfoService,
  private val securityRecommendationInteractionDao: SecurityRecommendationInteractionDao,
  private val coachmarkService: CoachmarkService,
) : AppDataDeleter {
  override suspend fun deleteAll() =
    coroutineBinding {
      check(appVariant != Customer) {
        "Not allowed to delete app data in Customer builds."
      }
      appPrivateKeyDao.clear()
      gettingStartedTaskDao.clearTasks()
      notificationTouchpointDao.clear()
      onboardingKeyboxSealedCsekDao.clear()
      onboardingKeyboxStepStateDao.clear()
      onboardingKeyboxHardwareKeysDao.clear()
      mobilePayService.deleteLocal()
      outgoingTransactionDetailDao.clear()
      fwupDataDaoProvider.get().clear()
      firmwareDeviceInfoDao.clear()
      firmwareMetadataDao.clear()
      authTokensService.clear()
      transactionPriorityPreference.clear()
      onboardingAppKeyKeystore.clear()
      fiatCurrencyPreferenceRepository.clear()
      bitcoinDisplayPreferenceRepository.clear()
      cloudBackupDao.clear()
      relationshipsKeysDao.clear()
      relationshipsService.clear()
      csekDao.clear()
      socRecStartedChallengeDao.clear()
      authKeyRotationAttemptDao.clear()
      recoveryDao.clear()
      authSignatureStatusProvider.clear()
      biometricPreference.clear()
      hideBalancePreference.clear()
      inheritanceClaimsDao.clear()
      metricTrackerService.clearMetrics()
      hardwareUnlockInfoService.clear()
      securityRecommendationInteractionDao.clear()
      coachmarkService.resetCoachmarks()

      // Make sure we clear Account data last because this will transition the UI
      accountService.clear().bind()
      keyboxDao.clear().bind()

      logDebug { "Successfully unpaired active keybox" }
    }
}
