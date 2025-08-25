package bitkey.securitycenter

import app.cash.turbine.test
import bitkey.metrics.MetricTrackerServiceFake
import bitkey.notifications.NotificationChannel
import bitkey.notifications.NotificationsService
import bitkey.notifications.NotificationsServiceMock
import bitkey.privilegedactions.FingerprintResetF8eClientFake
import bitkey.privilegedactions.FingerprintResetServiceFake
import bitkey.relationships.Relationships
import build.wallet.account.AccountServiceFake
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.availability.AppFunctionalityServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.relationships.EndorsedTrustedContactFake1
import build.wallet.cloud.backup.health.AppKeyBackupStatus
import build.wallet.cloud.backup.health.CloudBackupHealthRepositoryMock
import build.wallet.cloud.backup.health.EekBackupStatus
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
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.datetime.Clock

class SecurityActionsFunctionalTest : FunSpec({
  val accountService = AccountServiceFake()
  val appFunctionalityService = AppFunctionalityServiceFake()
  val cloudBackupHealthRepository = CloudBackupHealthRepositoryMock(turbines::create).apply {
    eekBackupStatus.value = EekBackupStatus.ProblemWithBackup.BackupMissing
    appKeyBackupStatus.value = AppKeyBackupStatus.ProblemWithBackup.NoCloudAccess
  }
  val appKeyBackupHealthActionFactory = AppKeyBackupHealthActionFactoryImpl(
    cloudBackupHealthRepository,
    accountService,
    appFunctionalityService
  )
  val eekBackupHealthActionFactory = EekBackupHealthActionFactoryImpl(
    cloudBackupHealthRepository,
    accountService,
    appFunctionalityService
  )

  val socRecService = SocRecServiceFake()
  val emptyRelationships = Relationships(
    invitations = emptyList(),
    endorsedTrustedContacts = emptyList(),
    unendorsedTrustedContacts = emptyList(),
    protectedCustomers = emptyImmutableList()
  )
  socRecService.socRecRelationships.value = emptyRelationships
  val socialRecoveryActionFactory = SocialRecoveryActionFactoryImpl(socRecService, appFunctionalityService)

  val biometricAuthService = BiometricAuthServiceFake()
  biometricAuthService.isBiometricAuthRequiredFlow.value = false
  val biometricActionFactory = BiometricActionFactoryImpl(biometricAuthService)

  val notificationService = NotificationsServiceMock()
  notificationService.criticalNotificationsStatus.value =
    NotificationsService.NotificationStatus.Missing(
      setOf(NotificationChannel.Push)
    )
  val criticalAlertsActionFactory = CriticalAlertsActionFactoryImpl(
    accountService,
    notificationService,
    appFunctionalityService
  )

  val clock = Clock.System
  val gettingStartedTaskDao = GettingStartedTaskDaoMock(turbines::create)
  val hardwareUnlockInfoService = HardwareUnlockInfoServiceFake()
  val firmwareDeviceInfoDao = FirmwareDeviceInfoDaoMock(turbines::create)
  val fingerprintResetF8eClient = FingerprintResetF8eClientFake(clock)
  val fingerprintResetService = FingerprintResetServiceFake(
    fingerprintResetF8eClient,
    accountService,
    clock
  )
  val fingerprintsActionFactory = FingerprintsActionFactoryImpl(
    gettingStartedTaskDao,
    hardwareUnlockInfoService,
    firmwareDeviceInfoDao,
    fingerprintResetService,
    clock
  )

  val firmwareDataService = FirmwareDataServiceFake()
  firmwareDataService.pendingUpdate = FirmwareDataPendingUpdateMock
  val hardwareDeviceActionFactory = HardwareDeviceActionFactoryImpl(
    firmwareDataService
  )
  val txVerificationActionFactory = TxVerificationActionFactoryFake()

  val eventTracker = EventTrackerMock(turbines::create)

  val metricTrackerService = MetricTrackerServiceFake()

  val securityRecommendationInteractionDao = SecurityRecommendationInteractionDaoMock()

  val securityActionsService = SecurityActionsServiceImpl(
    appKeyBackupHealthActionFactory,
    eekBackupHealthActionFactory,
    socialRecoveryActionFactory,
    biometricActionFactory,
    criticalAlertsActionFactory,
    fingerprintsActionFactory,
    hardwareDeviceActionFactory,
    txVerificationActionFactory,
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
    fingerprintResetService.setupReadyToCompleteFingerprintReset()
  }

  test("getRecommendations updates when individual action sources change") {
    val atRiskTestScenarios = listOf(
      Triple(
        "App Key backup completed",
        {
          cloudBackupHealthRepository.appKeyBackupStatus.value =
            AppKeyBackupStatus.Healthy(lastUploaded = Clock.System.now())
        },
        SecurityActionRecommendation.BACKUP_MOBILE_KEY
      )
    )

    val testScenarios = listOf(
      Triple(
        "Critical alerts enabled",
        {
          notificationService.criticalNotificationsStatus.value =
            NotificationsService.NotificationStatus.Enabled
        },
        SecurityActionRecommendation.ENABLE_PUSH_NOTIFICATIONS
      ),
      Triple(
        "Biometric setup completed",
        { biometricAuthService.isBiometricAuthRequiredFlow.value = true },
        SecurityActionRecommendation.SETUP_BIOMETRICS
      ),
      Triple(
        "EEK backup completed",
        {
          cloudBackupHealthRepository.eekBackupStatus.value =
            EekBackupStatus.Healthy(lastUploaded = Clock.System.now())
        },
        SecurityActionRecommendation.BACKUP_EAK
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
        "Complete fingerprint reset",
        {
          runBlocking {
            fingerprintResetService.completeFingerprintResetAndGetGrant(
              "test-action-id",
              "test-completion-token"
            )
          }
        },
        SecurityActionRecommendation.COMPLETE_FINGERPRINT_RESET
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

    var expectedAtRiskRecommendations = listOf(
      SecurityActionRecommendation.BACKUP_MOBILE_KEY
    )

    var expectedRecommendations = listOf(
      SecurityActionRecommendation.COMPLETE_FINGERPRINT_RESET,
      SecurityActionRecommendation.BACKUP_EAK,
      SecurityActionRecommendation.ADD_FINGERPRINTS,
      SecurityActionRecommendation.ADD_TRUSTED_CONTACTS,
      SecurityActionRecommendation.UPDATE_FIRMWARE,
      SecurityActionRecommendation.ENABLE_PUSH_NOTIFICATIONS,
      SecurityActionRecommendation.SETUP_BIOMETRICS,
      SecurityActionRecommendation.ENABLE_TRANSACTION_VERIFICATION
    )

    val testScope = TestScope()

    testScope.launch {
      securityActionsService.executeWork()
    }

    testScope.advanceUntilIdle()

    securityActionsService.securityActionsWithRecommendations.test {
      awaitItem().apply {
        atRiskRecommendations shouldBe expectedAtRiskRecommendations
        recommendations.shouldBeEmpty()
      }

      atRiskTestScenarios.forEach { (scenario, action, recommendationToRemove) ->
        action()

        expectedAtRiskRecommendations =
          expectedAtRiskRecommendations.filterNot { it == recommendationToRemove }

        testScope.advanceUntilIdle()

        runCatching {
          awaitItem().apply {
            atRiskRecommendations.shouldBeEmpty()
            recommendations shouldBe expectedRecommendations
          }
        }.onFailure { e ->
          fail("Scenario: $scenario failed with exception: $e")
        }
      }

      testScenarios.forEach { (scenario, action, recommendationToRemove) ->
        action()

        // Special case: Complete fingerprint reset creates a grant, so recommendation persists
        expectedRecommendations = if (recommendationToRemove == SecurityActionRecommendation.COMPLETE_FINGERPRINT_RESET) {
          // Don't remove the recommendation, it should persist due to grant being present in the db
          expectedRecommendations
        } else {
          expectedRecommendations.filterNot { it == recommendationToRemove }
        }

        testScope.advanceUntilIdle()

        runCatching {
          awaitItem().apply {
            atRiskRecommendations.shouldBeEmpty()
            recommendations shouldBe expectedRecommendations
          }
        }.onFailure { e ->
          fail("Scenario: $scenario failed with exception: $e")
        }
      }

      cancelAndIgnoreRemainingEvents()
      eventTracker.eventCalls.cancelAndIgnoreRemainingEvents()
    }
  }

  test("getActions returns expected actions") {
    val recoveryActions = securityActionsService.securityActionsWithRecommendations.value.recoveryActions
    recoveryActions.size shouldBe 4

    val accessActions = securityActionsService.securityActionsWithRecommendations.value.securityActions
    accessActions.size shouldBe 4
  }
})
