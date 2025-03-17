package build.wallet.debug

import bitkey.auth.AuthTokenScope.Global
import bitkey.metrics.MetricTrackerServiceFake
import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus.NoAccount
import build.wallet.auth.AccountAuthTokensMock
import build.wallet.auth.AuthKeyRotationAttemptDaoMock
import build.wallet.auth.AuthTokensServiceFake
import build.wallet.availability.AuthSignatureStatus
import build.wallet.availability.F8eAuthSignatureStatusProviderFake
import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.FASTEST
import build.wallet.bitcoin.transactions.OutgoingTransactionDetailDaoMock
import build.wallet.bitcoin.transactions.TransactionPriorityPreferenceFake
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.auth.AppGlobalAuthKeypairMock
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.spending.AppSpendingKeypair
import build.wallet.bitkey.spending.AppSpendingPublicKeyMock
import build.wallet.cloud.backup.csek.CsekDaoFake
import build.wallet.cloud.backup.local.CloudBackupDaoFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.firmware.FirmwareDeviceInfoDaoMock
import build.wallet.firmware.FirmwareMetadataDaoMock
import build.wallet.fwup.FwupDataDaoMock
import build.wallet.home.GettingStartedTaskDaoMock
import build.wallet.home.HomeUiBottomSheetDaoMock
import build.wallet.inappsecurity.BiometricPreferenceFake
import build.wallet.inappsecurity.HideBalancePreferenceFake
import build.wallet.inheritance.InheritanceClaimsDaoFake
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.keybox.keys.OnboardingAppKeyKeystoreFake
import build.wallet.limit.MobilePayServiceMock
import build.wallet.money.display.BitcoinDisplayPreferenceRepositoryMock
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.notifications.NotificationTouchpointDaoMock
import build.wallet.onboarding.OnboardingKeyboxHardwareKeys
import build.wallet.onboarding.OnboardingKeyboxHardwareKeysDaoFake
import build.wallet.onboarding.OnboardingKeyboxSealedCsekDaoMock
import build.wallet.onboarding.OnboardingKeyboxStepStateDaoMock
import build.wallet.platform.config.AppVariant
import build.wallet.recovery.RecoveryDaoMock
import build.wallet.recovery.socrec.SocRecStartedChallengeDaoFake
import build.wallet.relationships.RelationshipsKeysDaoFake
import build.wallet.relationships.RelationshipsServiceMock
import build.wallet.testing.shouldBeOk
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class AppDataDeleterImplTests : FunSpec({

  val gettingStartedTaskDao = GettingStartedTaskDaoMock(turbines::create)
  val onboardingKeyboxSealedCsekDao = OnboardingKeyboxSealedCsekDaoMock()
  val onboardingKeyboxStepStateDao =
    OnboardingKeyboxStepStateDaoMock(turbines::create)
  val notificationTouchpointDao = NotificationTouchpointDaoMock(turbines::create)

  val accountService = AccountServiceFake()
  val authTokensService = AuthTokensServiceFake()
  val keyboxDao = KeyboxDaoMock(turbines::create)
  val mobilePayService = MobilePayServiceMock(turbines::create)
  val transactionDetailDao = OutgoingTransactionDetailDaoMock(turbines::create)
  val fwupDataDao = FwupDataDaoMock(turbines::create)
  val firmwareDeviceIdentifiersDao =
    FirmwareDeviceInfoDaoMock(turbines::create)
  val firmwareMetadataDao = FirmwareMetadataDaoMock(turbines::create)
  val transactionPriorityPreference = TransactionPriorityPreferenceFake()
  val onboardingAppKeyKeystoreFake = OnboardingAppKeyKeystoreFake()
  val onboardingKeyboxHwAuthPublicKeyDao = OnboardingKeyboxHardwareKeysDaoFake()
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)
  val homeUiBottomSheetDao = HomeUiBottomSheetDaoMock(turbines::create)
  val bitcoinDisplayPreferenceRepository = BitcoinDisplayPreferenceRepositoryMock(turbines::create)
  val appPrivateKeyDao = AppPrivateKeyDaoFake()
  val cloudBackupDao = CloudBackupDaoFake()
  val relationshipsKeysDao = RelationshipsKeysDaoFake()
  val socRecStartedChallengeDao = SocRecStartedChallengeDaoFake()
  val relationshipsService = RelationshipsServiceMock(turbines::create)
  val authKeyRotationAttemptMock = AuthKeyRotationAttemptDaoMock(turbines::create)
  val recoveryDaoMock = RecoveryDaoMock(turbines::create)
  val authSignatureStatusProvider = F8eAuthSignatureStatusProviderFake()

  fun appDataDeleter(appVariant: AppVariant) =
    AppDataDeleterImpl(
      appVariant = appVariant,
      accountService = accountService,
      authTokensService = authTokensService,
      gettingStartedTaskDao = gettingStartedTaskDao,
      keyboxDao = keyboxDao,
      notificationTouchpointDao = notificationTouchpointDao,
      onboardingKeyboxSealedCsekDao = onboardingKeyboxSealedCsekDao,
      onboardingKeyboxStepStateDao = onboardingKeyboxStepStateDao,
      onboardingKeyboxHardwareKeysDao = onboardingKeyboxHwAuthPublicKeyDao,
      mobilePayService = mobilePayService,
      outgoingTransactionDetailDao = transactionDetailDao,
      fwupDataDao = fwupDataDao,
      firmwareDeviceInfoDao = firmwareDeviceIdentifiersDao,
      firmwareMetadataDao = firmwareMetadataDao,
      transactionPriorityPreference = transactionPriorityPreference,
      onboardingAppKeyKeystore = onboardingAppKeyKeystoreFake,
      fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
      homeUiBottomSheetDao = homeUiBottomSheetDao,
      bitcoinDisplayPreferenceRepository = bitcoinDisplayPreferenceRepository,
      appPrivateKeyDao = appPrivateKeyDao,
      cloudBackupDao = cloudBackupDao,
      relationshipsKeysDao = relationshipsKeysDao,
      relationshipsService = relationshipsService,
      socRecStartedChallengeDao = socRecStartedChallengeDao,
      csekDao = CsekDaoFake(),
      authKeyRotationAttemptDao = authKeyRotationAttemptMock,
      recoveryDao = recoveryDaoMock,
      authSignatureStatusProvider = authSignatureStatusProvider,
      hideBalancePreference = HideBalancePreferenceFake(),
      biometricPreference = BiometricPreferenceFake(),
      inheritanceClaimsDao = InheritanceClaimsDaoFake(),
      metricTrackerService = MetricTrackerServiceFake()
    )

  beforeTest {
    accountService.reset()
    authTokensService.reset()
    onboardingKeyboxSealedCsekDao.reset()
    gettingStartedTaskDao.reset()
    transactionPriorityPreference.reset()
    appPrivateKeyDao.reset()
    cloudBackupDao.reset()
  }

  test("not allowed to delete app data in Customer builds") {
    shouldThrow<IllegalStateException> {
      appDataDeleter(AppVariant.Customer).deleteAll()
    }
  }

  listOf(AppVariant.Development, AppVariant.Team).forEach { variant ->
    test("delete app data for $variant") {
      accountService.setActiveAccount(FullAccountMock)
      appPrivateKeyDao.storeAppKeyPair(AppGlobalAuthKeypairMock)
      appPrivateKeyDao.storeAppSpendingKeyPair(AppSpendingKeypair)
      onboardingAppKeyKeystoreFake
        .persistAppKeys(
          AppSpendingPublicKeyMock,
          AppGlobalAuthPublicKeyMock,
          AppRecoveryAuthPublicKeyMock,
          SIGNET
        )
      onboardingKeyboxHwAuthPublicKeyDao.set(
        OnboardingKeyboxHardwareKeys(
          hwAuthPublicKey = HwAuthPublicKey(Secp256k1PublicKey("fake-hw")),
          appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock
        )
      )
      transactionPriorityPreference.set(FASTEST)
      socRecStartedChallengeDao.set("fake")
      authSignatureStatusProvider.updateAuthSignatureStatus(AuthSignatureStatus.Unauthenticated)
      authTokensService.setTokens(FullAccountIdMock, AccountAuthTokensMock, Global)

      appDataDeleter(variant).deleteAll()

      accountService.accountState.value.shouldBeOk(NoAccount)
      appPrivateKeyDao.asymmetricKeys.shouldBeEmpty()
      authTokensService.getTokens(FullAccountIdMock, Global).shouldBeOk(null)
      gettingStartedTaskDao.clearTasksCalls.awaitItem()
      keyboxDao.clearCalls.awaitItem()
      mobilePayService.deleteLocalCalls.awaitItem()
      transactionDetailDao.clearCalls.awaitItem()
      notificationTouchpointDao.clearCalls.awaitItem()
      onboardingKeyboxStepStateDao.clearCalls.awaitItem()
      onboardingKeyboxSealedCsekDao.sealedCsek.shouldBeNull()
      fwupDataDao.clearCalls.awaitItem()
      firmwareDeviceIdentifiersDao.clearCalls.awaitItem()
      firmwareMetadataDao.clearCalls.awaitItem()
      transactionPriorityPreference.preference.shouldBeNull()
      onboardingAppKeyKeystoreFake.appKeys.shouldBeNull()
      fiatCurrencyPreferenceRepository.clearCalls.awaitItem()
      homeUiBottomSheetDao.clearHomeUiBottomSheetCalls.awaitItem()
      bitcoinDisplayPreferenceRepository.clearCalls?.awaitItem()
      authKeyRotationAttemptMock.clearCalls.awaitItem()
      onboardingKeyboxHwAuthPublicKeyDao.keys?.hwAuthPublicKey.shouldBeNull()
      socRecStartedChallengeDao.pendingChallengeId.shouldBeNull()
      recoveryDaoMock.clearCalls.awaitItem()
      authSignatureStatusProvider.authSignatureStatus().value.shouldBe(AuthSignatureStatus.Authenticated)

      cloudBackupDao.shouldBeEmpty()
    }
  }
})
