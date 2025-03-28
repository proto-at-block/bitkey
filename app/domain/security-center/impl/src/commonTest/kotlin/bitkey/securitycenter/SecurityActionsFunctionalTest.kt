package bitkey.securitycenter

import bitkey.notifications.NotificationChannel
import bitkey.notifications.NotificationsService
import bitkey.notifications.NotificationsServiceMock
import bitkey.relationships.Relationships
import build.wallet.account.AccountServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.cloud.backup.health.CloudBackupHealthRepositoryMock
import build.wallet.cloud.backup.health.EakBackupStatus
import build.wallet.cloud.backup.health.MobileKeyBackupStatus
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.coroutines.turbine.turbines
import build.wallet.firmware.HardwareUnlockInfoServiceFake
import build.wallet.firmware.UnlockInfo
import build.wallet.firmware.UnlockMethod
import build.wallet.home.GettingStartedTaskDaoMock
import build.wallet.inappsecurity.BiometricAuthServiceFake
import build.wallet.inheritance.InheritanceServiceMock
import build.wallet.recovery.socrec.SocRecServiceFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SecurityActionsFunctionalTest :
  FunSpec({
    val accountService = AccountServiceFake()
    val cloudBackupHealthRepository = CloudBackupHealthRepositoryMock(turbines::create).apply {
      eakBackupStatus.value = EakBackupStatus.ProblemWithBackup.BackupMissing
      mobileKeyBackupStatus.value = MobileKeyBackupStatus.ProblemWithBackup.NoCloudAccess
    }
    val mobileKeyBackupHealthActionFactory = MobileKeyBackupHealthActionFactoryImpl(
      cloudBackupHealthRepository,
      accountService
    )
    val eakBackupHealthActionFactory = EakBackupHealthActionFactoryImpl(
      cloudBackupHealthRepository,
      accountService
    )

    val socRecService = SocRecServiceFake()
    val emptyRelationships = Relationships(
      invitations = emptyList(),
      endorsedTrustedContacts = emptyList(),
      unendorsedTrustedContacts = emptyList(),
      protectedCustomers = emptyImmutableList()
    )
    socRecService.socRecRelationships.value = emptyRelationships
    val socialRecoveryActionFactory = SocialRecoveryActionFactoryImpl(socRecService)

    val inheritanceService = InheritanceServiceMock(
      syncCalls = turbines.create("Sync Calls"),
      cancelClaimCalls = turbines.create("Cancel Claim Calls")
    )
    inheritanceService.relationships.value = emptyRelationships
    val inheritanceActionFactory = InheritanceActionFactoryImpl(inheritanceService)

    val biometricAuthService = BiometricAuthServiceFake()
    biometricAuthService.isBiometricAuthRequiredFlow.value = false
    val biometricActionFactory = BiometricActionFactoryImpl(biometricAuthService)

    val notificationService = NotificationsServiceMock()
    notificationService.criticalNotificationsStatus.value = NotificationsService.NotificationStatus.Missing(
      setOf(NotificationChannel.Push)
    )
    val criticalAlertsActionFactory = CriticalAlertsActionFactoryImpl(
      accountService,
      notificationService
    )

    val gettingStartedTaskDao = GettingStartedTaskDaoMock(turbines::create)
    val hardwareUnlockInfoService = HardwareUnlockInfoServiceFake()
    val fingerprintsActionFactory = FingerprintsActionFactoryImpl(
      gettingStartedTaskDao,
      hardwareUnlockInfoService
    )

    val securityActionsService = SecurityActionsServiceImpl(
      mobileKeyBackupHealthActionFactory,
      eakBackupHealthActionFactory,
      socialRecoveryActionFactory,
      inheritanceActionFactory,
      biometricActionFactory,
      criticalAlertsActionFactory,
      fingerprintsActionFactory
    )

    beforeTest {
      accountService.setActiveAccount(FullAccountMock)
      hardwareUnlockInfoService.replaceAllUnlockInfo(
        listOf(UnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS, fingerprintIdx = 1))
      )
    }

    test("getRecommendations returns expected recommendations") {
      val recommendations = securityActionsService.getRecommendations()

      cloudBackupHealthRepository.performSyncCalls.awaitItem()
      cloudBackupHealthRepository.performSyncCalls.awaitItem()

      recommendations shouldBe listOf(
        SecurityActionRecommendation.BACKUP_MOBILE_KEY,
        SecurityActionRecommendation.BACKUP_EAK,
        SecurityActionRecommendation.ADD_FINGERPRINTS,
        SecurityActionRecommendation.ADD_TRUSTED_CONTACTS,
        SecurityActionRecommendation.ENABLE_CRITICAL_ALERTS,
        SecurityActionRecommendation.ADD_BENEFICIARY,
        SecurityActionRecommendation.SETUP_BIOMETRICS
      )
    }

    test("getActions returns expected actions") {
      val recoveryActions = securityActionsService.getActions(SecurityActionCategory.RECOVERY)
      recoveryActions.size shouldBe 5

      val accessActions = securityActionsService.getActions(SecurityActionCategory.SECURITY)
      accessActions.size shouldBe 2
    }
  })
