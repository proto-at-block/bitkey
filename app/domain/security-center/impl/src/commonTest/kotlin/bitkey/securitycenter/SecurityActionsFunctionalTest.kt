package bitkey.securitycenter

import app.cash.turbine.test
import bitkey.metrics.MetricTrackerServiceFake
import bitkey.notifications.NotificationChannel
import bitkey.notifications.NotificationsService
import bitkey.notifications.NotificationsServiceMock
import bitkey.relationships.Relationships
import build.wallet.account.AccountServiceFake
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.relationships.EndorsedTrustedContactFake1
import build.wallet.cloud.backup.health.CloudBackupHealthRepositoryMock
import build.wallet.cloud.backup.health.EekBackupStatus
import build.wallet.cloud.backup.health.MobileKeyBackupStatus
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.coroutines.turbine.turbines
import build.wallet.firmware.FirmwareDeviceInfoDaoMock
import build.wallet.firmware.HardwareUnlockInfoServiceFake
import build.wallet.firmware.UnlockInfo
import build.wallet.firmware.UnlockMethod
import build.wallet.fwup.FirmwareDataPendingUpdateMock
import build.wallet.fwup.FirmwareDataServiceFake
import build.wallet.fwup.FwupDataMock
import build.wallet.home.GettingStartedTaskDaoMock
import build.wallet.inappsecurity.BiometricAuthServiceFake
import build.wallet.recovery.socrec.SocRecServiceFake
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock

class SecurityActionsFunctionalTest :
  FunSpec({
    val accountService = AccountServiceFake()
    val cloudBackupHealthRepository = CloudBackupHealthRepositoryMock(turbines::create).apply {
      eekBackupStatus.value = EekBackupStatus.ProblemWithBackup.BackupMissing
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
    val firmwareDeviceInfoDao = FirmwareDeviceInfoDaoMock(turbines::create)
    val fingerprintsActionFactory = FingerprintsActionFactoryImpl(
      gettingStartedTaskDao,
      hardwareUnlockInfoService,
      firmwareDeviceInfoDao
    )

    val firmwareDataService = FirmwareDataServiceFake()
    firmwareDataService.pendingUpdate = FirmwareDataPendingUpdateMock
    val hardwareDeviceActionFactory = HardwareDeviceActionFactoryImpl(
      firmwareDataService
    )

    val eventTracker = EventTrackerMock(turbines::create)

    val metricTrackerService = MetricTrackerServiceFake()

    val securityRecommendationInteractionDao = SecurityRecommendationInteractionDaoMock()
    val clock = Clock.System

    val securityActionsService = SecurityActionsServiceImpl(
      mobileKeyBackupHealthActionFactory,
      eakBackupHealthActionFactory,
      socialRecoveryActionFactory,
      biometricActionFactory,
      criticalAlertsActionFactory,
      fingerprintsActionFactory,
      hardwareDeviceActionFactory,
      eventTracker,
      metricTrackerService,
      securityRecommendationInteractionDao,
      clock
    )

    beforeTest {
      accountService.setActiveAccount(FullAccountMock)
      hardwareUnlockInfoService.replaceAllUnlockInfo(
        listOf(UnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS, fingerprintIdx = 1))
      )
      firmwareDataService.syncLatestFwupData()
    }

    test("getRecommendations updates when individual action sources change") {
      val recommendations = securityActionsService.getRecommendations()

      val testScenarios = listOf(
        Triple(
          "Biometric setup completed",
          { biometricAuthService.isBiometricAuthRequiredFlow.value = true },
          SecurityActionRecommendation.SETUP_BIOMETRICS
        ),
        Triple(
          "EEK backup completed",
          {
            cloudBackupHealthRepository.eekBackupStatus.value = EekBackupStatus.Healthy(lastUploaded = Clock.System.now())
          },
          SecurityActionRecommendation.BACKUP_EAK
        ),
        Triple(
          "App Key backup completed",
          {
            cloudBackupHealthRepository.mobileKeyBackupStatus.value = MobileKeyBackupStatus.Healthy(lastUploaded = Clock.System.now())
          },
          SecurityActionRecommendation.BACKUP_MOBILE_KEY
        ),
        Triple(
          "Social recovery setup completed",
          {
            val relationships = Relationships(
              invitations = emptyList(),
              endorsedTrustedContacts = listOf(EndorsedTrustedContactFake1),
              unendorsedTrustedContacts = emptyList(),
              protectedCustomers = emptyImmutableList()
            )
            socRecService.socRecRelationships.value = relationships
          },
          SecurityActionRecommendation.ADD_TRUSTED_CONTACTS
        ),
        Triple(
          "Critical alerts enabled",
          {
            notificationService.criticalNotificationsStatus.value = NotificationsService.NotificationStatus.Enabled
          },
          SecurityActionRecommendation.ENABLE_PUSH_NOTIFICATIONS
        ),
        Triple(
          "Fingerprints added",
          {
            runBlocking {
              hardwareUnlockInfoService.replaceAllUnlockInfo(
                listOf(
                  UnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS, fingerprintIdx = 1),
                  UnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS, fingerprintIdx = 2),
                  UnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS, fingerprintIdx = 3)
                )
              )
            }
          },
          SecurityActionRecommendation.ADD_FINGERPRINTS
        ),
        Triple(
          "Device updated",
          {
            runBlocking {
              firmwareDataService.updateFirmwareVersion(FwupDataMock)
            }
          },
          SecurityActionRecommendation.UPDATE_FIRMWARE
        )
      )

      var expectedRecommendations = listOf(
        SecurityActionRecommendation.BACKUP_MOBILE_KEY,
        SecurityActionRecommendation.BACKUP_EAK,
        SecurityActionRecommendation.ADD_FINGERPRINTS,
        SecurityActionRecommendation.ADD_TRUSTED_CONTACTS,
        SecurityActionRecommendation.UPDATE_FIRMWARE,
        SecurityActionRecommendation.ENABLE_PUSH_NOTIFICATIONS,
        SecurityActionRecommendation.SETUP_BIOMETRICS
      )
      recommendations.test {
        awaitItem() shouldBe expectedRecommendations

        testScenarios.forEach { (scenario, action, recommendationToRemove) ->
          action()

          expectedRecommendations =
            expectedRecommendations.filterNot { it == recommendationToRemove }

          runCatching {
            awaitItem() shouldBe expectedRecommendations
          }.onFailure { e ->
            fail("Scenario: $scenario failed with exception: $e")
          }
        }

        cancelAndIgnoreRemainingEvents()
        eventTracker.eventCalls.cancelAndIgnoreRemainingEvents()
      }
    }

    test("getActions returns expected actions") {
      val recoveryActions = securityActionsService.getActions(SecurityActionCategory.RECOVERY)
      recoveryActions.size shouldBe 4

      val accessActions = securityActionsService.getActions(SecurityActionCategory.SECURITY)
      accessActions.size shouldBe 3
    }
  })
