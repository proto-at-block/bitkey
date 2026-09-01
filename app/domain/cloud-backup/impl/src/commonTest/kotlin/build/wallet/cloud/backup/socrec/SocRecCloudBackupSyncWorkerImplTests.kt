package build.wallet.cloud.backup.socrec

import bitkey.relationships.Relationships
import build.wallet.account.AccountServiceFake
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.events.count.id.InheritanceEventTrackerCounterId
import build.wallet.analytics.events.count.id.SocialRecoveryEventTrackerCounterId
import build.wallet.analytics.v1.Action
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.relationships.EndorsedBeneficiaryFake
import build.wallet.bitkey.relationships.EndorsedTrustedContactFake1
import build.wallet.bitkey.relationships.EndorsedTrustedContactFake2
import build.wallet.cloud.backup.*
import build.wallet.cloud.backup.CloudBackupOperationLockImpl
import build.wallet.cloud.backup.FullAccountCloudBackupCreator.FullAccountCloudBackupCreatorError.FullAccountFieldsCreationError
import build.wallet.cloud.backup.csek.SealedSsekFake
import build.wallet.cloud.backup.csek.SsekDaoFake
import build.wallet.cloud.backup.csek.SsekFake
import build.wallet.cloud.backup.local.CloudBackupDaoFake
import build.wallet.cloud.backup.v2.FullAccountFields
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.cloud.store.CloudStoreAccountError
import build.wallet.cloud.store.CloudStoreAccountRepositoryMock
import build.wallet.coroutines.createBackgroundScope
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.relationships.RelationshipsFake
import build.wallet.platform.app.AppSessionManagerFake
import build.wallet.recovery.RecoveryStatusServiceMock
import build.wallet.recovery.StillRecoveringInitiatedRecoveryMock
import build.wallet.relationships.RelationshipsServiceMock
import build.wallet.wallet.migration.MigrationError
import build.wallet.wallet.migration.MigrationProgress
import build.wallet.wallet.migration.MigrationServiceFake
import build.wallet.wallet.migration.MigrationType
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch

class SocRecCloudBackupSyncWorkerImplTests : FunSpec({

  val clock = ClockFake()
  val cloudInstanceId = "fake"
  val fullAccount = FullAccountMock
  val accountService = AccountServiceFake()
  val recoveryStatusService = RecoveryStatusServiceMock(turbine = turbines::create)
  val relationshipsService = RelationshipsServiceMock(turbines::create, clock)
  val cloudBackupDao = CloudBackupDaoFake()
  val cloudStoreAccountRepository = CloudStoreAccountRepositoryMock()
  val cloudBackupService = CloudBackupServiceFake()
  val fullAccountCloudBackupCreator = FullAccountCloudBackupCreatorMock(turbines::create)
  val eventTracker = EventTrackerMock(turbines::create)
  val ssekDao = SsekDaoFake()
  val migrationService = MigrationServiceFake()

  val cloudAccount = CloudAccountMock(cloudInstanceId)

  val socRecCloudBackupSyncWorker = SocRecCloudBackupSyncWorkerImpl(
    accountService = accountService,
    recoveryStatusService = recoveryStatusService,
    relationshipsService = relationshipsService,
    cloudBackupDao = cloudBackupDao,
    cloudStoreAccountRepository = cloudStoreAccountRepository,
    cloudBackupService = cloudBackupService,
    fullAccountCloudBackupCreator = fullAccountCloudBackupCreator,
    eventTracker = eventTracker,
    clock = ClockFake(),
    appSessionManager = AppSessionManagerFake(),
    cloudBackupOperationLock = CloudBackupOperationLockImpl(),
    ssekDao = ssekDao,
    migrationService = migrationService
  )

  afterTest {
    relationshipsService.relationships.emit(RelationshipsFake)
    cloudStoreAccountRepository.reset()
    fullAccountCloudBackupCreator.reset()
    ssekDao.reset()
    migrationService.reset()
  }

  context("parameterized tests for all backup versions") {
    AllFullAccountBackupMocks.forEach { cloudBackup ->
      val backupVersion = when (cloudBackup) {
        is CloudBackupV2 -> "v2"
        is CloudBackupV3 -> "v3"
        else -> "unknown"
      }

      // Create otherBackup with empty socRecSealedDekMap for this version
      val otherBackup = when (cloudBackup) {
        is CloudBackupV2 -> cloudBackup.copy(
          fullAccountFields = (cloudBackup.fullAccountFields as? FullAccountFields)?.copy(
            socRecSealedDekMap = mapOf()
          )
        )
        is CloudBackupV3 -> cloudBackup.copy(
          fullAccountFields = (cloudBackup.fullAccountFields as? FullAccountFields)?.copy(
            socRecSealedDekMap = mapOf()
          )
        )
        else -> error("Unknown backup version")
      }

      context("cloud backup $backupVersion") {
        beforeTest {
          cloudBackupDao.clear()
          cloudBackupDao.set(fullAccount.accountId.serverId, otherBackup as CloudBackup)
          cloudStoreAccountRepository.currentAccountResult = Ok(cloudAccount)
          fullAccountCloudBackupCreator.backupResult = Ok(cloudBackup as CloudBackup)
          cloudBackupService.reset()
          accountService.reset()
          accountService.setActiveAccount(fullAccount)
          recoveryStatusService.reset()
        }

        test("success") {
          createBackgroundScope().launch {
            socRecCloudBackupSyncWorker.executeWork()
          }
          eventTracker.eventCalls.awaitItem().shouldBe(
            TrackedAction(
              action = Action.ACTION_APP_COUNT,
              counterId = SocialRecoveryEventTrackerCounterId.SOCREC_COUNT_TOTAL_TCS,
              count = 2
            )
          )
          eventTracker.eventCalls.awaitItem().shouldBe(
            TrackedAction(
              action = Action.ACTION_APP_COUNT,
              counterId = InheritanceEventTrackerCounterId.INHERITANCE_COUNT_TOTAL_BENEFICIARIES,
              count = 1
            )
          )
          fullAccountCloudBackupCreator.createCalls.awaitItem()
          cloudBackupService.awaitBackup(cloudAccount)
            .shouldBe(cloudBackup)
        }

        test("skips cloud backup refresh when lost hardware recovery is in progress") {
          // Set up a hardware recovery in progress
          recoveryStatusService.recoveryStatus.value =
            StillRecoveringInitiatedRecoveryMock.copy(factorToRecover = PhysicalFactor.Hardware)

          createBackgroundScope().launch {
            socRecCloudBackupSyncWorker.executeWork()
          }

          // Verify that no cloud backup was created or uploaded
          fullAccountCloudBackupCreator.createCalls.expectNoEvents()
          cloudBackupService.awaitNoBackups()
        }

        test("success - multiple") {
          createBackgroundScope().launch {
            socRecCloudBackupSyncWorker.executeWork()
          }

          eventTracker.eventCalls.awaitItem().shouldBe(
            TrackedAction(
              action = Action.ACTION_APP_COUNT,
              counterId = SocialRecoveryEventTrackerCounterId.SOCREC_COUNT_TOTAL_TCS,
              count = 2
            )
          )
          eventTracker.eventCalls.awaitItem().shouldBe(
            TrackedAction(
              action = Action.ACTION_APP_COUNT,
              counterId = InheritanceEventTrackerCounterId.INHERITANCE_COUNT_TOTAL_BENEFICIARIES,
              count = 1
            )
          )

          fullAccountCloudBackupCreator.createCalls.awaitItem()
          cloudBackupService.awaitBackup(cloudAccount).shouldBe(cloudBackup)
          relationshipsService.relationships
            .emit(
              RelationshipsFake.copy(
                endorsedTrustedContacts = listOf(
                  EndorsedTrustedContactFake1
                )
              )
            )

          eventTracker.eventCalls.awaitItem().shouldBe(
            TrackedAction(
              action = Action.ACTION_APP_COUNT,
              counterId = SocialRecoveryEventTrackerCounterId.SOCREC_COUNT_TOTAL_TCS,
              count = 1
            )
          )
          eventTracker.eventCalls.awaitItem().shouldBe(
            TrackedAction(
              action = Action.ACTION_APP_COUNT,
              counterId = InheritanceEventTrackerCounterId.INHERITANCE_COUNT_TOTAL_BENEFICIARIES,
              count = 0
            )
          )

          fullAccountCloudBackupCreator.createCalls.awaitItem()
        }

        test("success - beneficiary only") {
          relationshipsService.relationships
            .emit(
              RelationshipsFake.copy(
                endorsedTrustedContacts = listOf(
                  EndorsedBeneficiaryFake
                )
              )
            )

          createBackgroundScope().launch {
            socRecCloudBackupSyncWorker.executeWork()
          }

          eventTracker.eventCalls.awaitItem().shouldBe(
            TrackedAction(
              action = Action.ACTION_APP_COUNT,
              counterId = SocialRecoveryEventTrackerCounterId.SOCREC_COUNT_TOTAL_TCS,
              count = 0
            )
          )
          eventTracker.eventCalls.awaitItem().shouldBe(
            TrackedAction(
              action = Action.ACTION_APP_COUNT,
              counterId = InheritanceEventTrackerCounterId.INHERITANCE_COUNT_TOTAL_BENEFICIARIES,
              count = 1
            )
          )
          fullAccountCloudBackupCreator.createCalls.awaitItem()
        }

        test("success - duplicate ignored") {
          createBackgroundScope().launch {
            socRecCloudBackupSyncWorker.executeWork()
          }

          eventTracker.eventCalls.awaitItem().shouldBe(
            TrackedAction(
              action = Action.ACTION_APP_COUNT,
              counterId = SocialRecoveryEventTrackerCounterId.SOCREC_COUNT_TOTAL_TCS,
              count = 2
            )
          )
          eventTracker.eventCalls.awaitItem().shouldBe(
            TrackedAction(
              action = Action.ACTION_APP_COUNT,
              counterId = InheritanceEventTrackerCounterId.INHERITANCE_COUNT_TOTAL_BENEFICIARIES,
              count = 1
            )
          )

          fullAccountCloudBackupCreator.createCalls.awaitItem()
          cloudBackupService
            .awaitBackup(cloudAccount)
            .shouldBe(cloudBackup)
          relationshipsService.relationships.emit(RelationshipsFake)
        }

        test("success - cloud backup v1 always accepted") {
          // Setting this to empty to match what would be found in an old backup.
          // An upload should be triggered even if no trusted contacts are known.
          relationshipsService.relationships.emit(Relationships.EMPTY)
          cloudBackupDao.set(fullAccount.accountId.serverId, cloudBackup as CloudBackup)
          createBackgroundScope().launch {
            socRecCloudBackupSyncWorker.executeWork()
          }
          eventTracker.eventCalls.awaitItem().shouldBe(
            TrackedAction(
              action = Action.ACTION_APP_COUNT,
              counterId = SocialRecoveryEventTrackerCounterId.SOCREC_COUNT_TOTAL_TCS,
              count = 0
            )
          )
          eventTracker.eventCalls.awaitItem().shouldBe(
            TrackedAction(
              action = Action.ACTION_APP_COUNT,
              counterId = InheritanceEventTrackerCounterId.INHERITANCE_COUNT_TOTAL_BENEFICIARIES,
              count = 0
            )
          )
          fullAccountCloudBackupCreator.createCalls.awaitItem()
          cloudBackupService.awaitBackup(cloudAccount)
            .shouldBe(cloudBackup)
        }

        test("refreshes backup when local SSEK is missing from backup - $backupVersion") {
          // Store a backup whose trusted contacts are up to date so that only the SSEK
          // staleness check can trigger a refresh.
          cloudBackupDao.set(fullAccount.accountId.serverId, cloudBackup as CloudBackup)
          ssekDao.set(SealedSsekFake, SsekFake)
          // Match the trusted contacts to the backup's socRecSealedDekMap so only the SSEK
          // check could trigger a refresh.
          relationshipsService.relationships.emit(
            RelationshipsFake.copy(
              endorsedTrustedContacts = listOf(
                EndorsedTrustedContactFake1,
                EndorsedTrustedContactFake2
              )
            )
          )

          createBackgroundScope().launch {
            socRecCloudBackupSyncWorker.executeWork()
          }

          eventTracker.eventCalls.awaitItem().shouldBe(
            TrackedAction(
              action = Action.ACTION_APP_COUNT,
              counterId = SocialRecoveryEventTrackerCounterId.SOCREC_COUNT_TOTAL_TCS,
              count = 2
            )
          )
          eventTracker.eventCalls.awaitItem().shouldBe(
            TrackedAction(
              action = Action.ACTION_APP_COUNT,
              counterId = InheritanceEventTrackerCounterId.INHERITANCE_COUNT_TOTAL_BENEFICIARIES,
              count = 0
            )
          )
          fullAccountCloudBackupCreator.createCalls.awaitItem()
          cloudBackupService.awaitBackup(cloudAccount)
            .shouldBe(cloudBackup)
        }

        test("does not refresh backup for missing SSEK while migration is in progress - $backupVersion") {
          // Store a backup whose trusted contacts are up to date so that only the SSEK
          // staleness check could trigger a refresh.
          cloudBackupDao.set(fullAccount.accountId.serverId, cloudBackup as CloudBackup)
          ssekDao.set(SealedSsekFake, SsekFake)
          // Match the trusted contacts to the backup's socRecSealedDekMap so only the SSEK
          // check could trigger a refresh.
          relationshipsService.relationships.emit(
            RelationshipsFake.copy(
              endorsedTrustedContacts = listOf(
                EndorsedTrustedContactFake1,
                EndorsedTrustedContactFake2
              )
            )
          )
          // Simulate an in-progress W3 upgrade.
          migrationService.resumeResult = Ok(
            MigrationProgress.AuthKeyRotation(
              type = MigrationType.W3Upgrade,
              currentKeybox = fullAccount.keybox,
              newKeyset = fullAccount.keybox.activeSpendingKeyset
            )
          )

          createBackgroundScope().launch {
            socRecCloudBackupSyncWorker.executeWork()
          }

          fullAccountCloudBackupCreator.createCalls.expectNoEvents()
          cloudBackupService.awaitNoBackups()
        }

        test("does not refresh backup when persisted migration state maps to NotStarted - $backupVersion") {
          // Store a backup whose trusted contacts are up to date so that only the SSEK
          // staleness check could trigger a refresh.
          cloudBackupDao.set(fullAccount.accountId.serverId, cloudBackup as CloudBackup)
          ssekDao.set(SealedSsekFake, SsekFake)
          // Match the trusted contacts to the backup's socRecSealedDekMap so only the SSEK
          // check could trigger a refresh.
          relationshipsService.relationships.emit(
            RelationshipsFake.copy(
              endorsedTrustedContacts = listOf(
                EndorsedTrustedContactFake1,
                EndorsedTrustedContactFake2
              )
            )
          )
          // Simulate an interrupted migration whose persisted checkpoint maps back to
          // NotStarted for flow routing but still has durable state.
          migrationService.resumeResult = Ok(
            MigrationProgress.NotStarted(MigrationType.PrivateWalletMigration)
          )
          migrationService.hasPersistedMigrationStateResult = Ok(true)

          createBackgroundScope().launch {
            socRecCloudBackupSyncWorker.executeWork()
          }

          fullAccountCloudBackupCreator.createCalls.expectNoEvents()
          cloudBackupService.awaitNoBackups()
        }

        test("does not refresh backup when migration state cannot be read - $backupVersion") {
          // Store a backup whose trusted contacts are up to date so that only the SSEK
          // staleness check could trigger a refresh.
          cloudBackupDao.set(fullAccount.accountId.serverId, cloudBackup as CloudBackup)
          ssekDao.set(SealedSsekFake, SsekFake)
          // Match the trusted contacts to the backup's socRecSealedDekMap so only the SSEK
          // check could trigger a refresh.
          relationshipsService.relationships.emit(
            RelationshipsFake.copy(
              endorsedTrustedContacts = listOf(
                EndorsedTrustedContactFake1,
                EndorsedTrustedContactFake2
              )
            )
          )
          // Migration state read failure must fail closed (treated as in progress).
          migrationService.resumeResult = Err(
            MigrationError.StatePersistenceFailed(Error("failed to read migration state"))
          )

          createBackgroundScope().launch {
            socRecCloudBackupSyncWorker.executeWork()
          }

          fullAccountCloudBackupCreator.createCalls.expectNoEvents()
          cloudBackupService.awaitNoBackups()
        }

        test("refreshes backup when SSEK ids cannot be read - $backupVersion") {
          // Store a backup whose trusted contacts are up to date so that only the SSEK
          // staleness check could trigger a refresh.
          cloudBackupDao.set(fullAccount.accountId.serverId, cloudBackup as CloudBackup)
          // Match the trusted contacts to the backup's socRecSealedDekMap so only the SSEK
          // check could trigger a refresh.
          relationshipsService.relationships.emit(
            RelationshipsFake.copy(
              endorsedTrustedContacts = listOf(
                EndorsedTrustedContactFake1,
                EndorsedTrustedContactFake2
              )
            )
          )
          // An SSEK read failure must count as stale so an incomplete backup gets repaired.
          ssekDao.getAllSealedIdsErrResult = Err(Error("failed to enumerate SSEKs"))

          createBackgroundScope().launch {
            socRecCloudBackupSyncWorker.executeWork()
          }

          eventTracker.eventCalls.awaitItem()
          eventTracker.eventCalls.awaitItem()
          fullAccountCloudBackupCreator.createCalls.awaitItem()
        }

        test("does not refresh backup when SSEKs are already backed up - $backupVersion") {
          // Store a backup that is up to date for trusted contacts and already lists the
          // local SSEK.
          val backupWithSsek = when (cloudBackup) {
            is CloudBackupV2 -> cloudBackup.copy(
              fullAccountFields = (cloudBackup.fullAccountFields as FullAccountFields).copy(
                sealedSsekIds = setOf(SealedSsekFake.hex())
              )
            )
            is CloudBackupV3 -> cloudBackup.copy(
              fullAccountFields = (cloudBackup.fullAccountFields as FullAccountFields).copy(
                sealedSsekIds = setOf(SealedSsekFake.hex())
              )
            )
            else -> error("Unknown backup version")
          }
          cloudBackupDao.set(fullAccount.accountId.serverId, backupWithSsek as CloudBackup)
          ssekDao.set(SealedSsekFake, SsekFake)
          // Match the trusted contacts to the backup's socRecSealedDekMap so only the SSEK
          // check could trigger a refresh.
          relationshipsService.relationships.emit(
            RelationshipsFake.copy(
              endorsedTrustedContacts = listOf(
                EndorsedTrustedContactFake1,
                EndorsedTrustedContactFake2
              )
            )
          )

          createBackgroundScope().launch {
            socRecCloudBackupSyncWorker.executeWork()
          }

          fullAccountCloudBackupCreator.createCalls.expectNoEvents()
          cloudBackupService.awaitNoBackups()
        }

        test("failure - error writing cloud backup") {
          cloudBackupService.returnWriteError =
            CloudBackupError.UnrectifiableCloudBackupError(Throwable())
          createBackgroundScope().launch {
            socRecCloudBackupSyncWorker.executeWork()
          }
          eventTracker.eventCalls.awaitItem().shouldBe(
            TrackedAction(
              action = Action.ACTION_APP_COUNT,
              counterId = SocialRecoveryEventTrackerCounterId.SOCREC_COUNT_TOTAL_TCS,
              count = 2
            )
          )
          eventTracker.eventCalls.awaitItem().shouldBe(
            TrackedAction(
              action = Action.ACTION_APP_COUNT,
              counterId = InheritanceEventTrackerCounterId.INHERITANCE_COUNT_TOTAL_BENEFICIARIES,
              count = 1
            )
          )
          fullAccountCloudBackupCreator.createCalls.awaitItem()
          cloudBackupService.awaitNoBackups()
        }

        test("failure - error creating backup") {
          fullAccountCloudBackupCreator.backupResult =
            Err(FullAccountFieldsCreationError())
          createBackgroundScope().launch {
            socRecCloudBackupSyncWorker.executeWork()
          }
          eventTracker.eventCalls.awaitItem().shouldBe(
            TrackedAction(
              action = Action.ACTION_APP_COUNT,
              counterId = SocialRecoveryEventTrackerCounterId.SOCREC_COUNT_TOTAL_TCS,
              count = 2
            )
          )
          eventTracker.eventCalls.awaitItem().shouldBe(
            TrackedAction(
              action = Action.ACTION_APP_COUNT,
              counterId = InheritanceEventTrackerCounterId.INHERITANCE_COUNT_TOTAL_BENEFICIARIES,
              count = 1
            )
          )
          fullAccountCloudBackupCreator.createCalls.awaitItem()
          cloudBackupService.awaitNoBackups()
        }
      }
    }
  }

  // Version-independent tests
  context("lite account tests") {
    val otherBackup = CloudBackupV2WithFullAccountMock.copy(
      fullAccountFields = (CloudBackupV2WithFullAccountMock.fullAccountFields as FullAccountFields).copy(
        socRecSealedDekMap = mapOf()
      )
    )

    beforeTest {
      cloudBackupDao.clear()
      cloudBackupDao.set(fullAccount.accountId.serverId, otherBackup)
      cloudStoreAccountRepository.currentAccountResult = Ok(cloudAccount)
      fullAccountCloudBackupCreator.backupResult = Ok(CloudBackupV2WithFullAccountMock)
      cloudBackupService.reset()
      accountService.reset()
      accountService.setActiveAccount(fullAccount)
      recoveryStatusService.reset()
    }

    test("success - equal trusted contacts sets ignored") {
      cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithLiteAccountMock)

      createBackgroundScope().launch {
        socRecCloudBackupSyncWorker.executeWork()
      }
    }

    test("failure - equal trusted contacts maps ignored") {
      cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithLiteAccountMock)
      createBackgroundScope().launch {
        socRecCloudBackupSyncWorker.executeWork()
      }
    }

    test("failure - null cloud backup ignored") {
      cloudBackupDao.clear()
      createBackgroundScope().launch {
        socRecCloudBackupSyncWorker.executeWork()
      }
    }

    test("failure - no cloud account") {
      cloudStoreAccountRepository.currentAccountResult = Ok(null)
      createBackgroundScope().launch {
        socRecCloudBackupSyncWorker.executeWork()
      }
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(
          action = Action.ACTION_APP_COUNT,
          counterId = SocialRecoveryEventTrackerCounterId.SOCREC_COUNT_TOTAL_TCS,
          count = 2
        )
      )
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(
          action = Action.ACTION_APP_COUNT,
          counterId = InheritanceEventTrackerCounterId.INHERITANCE_COUNT_TOTAL_BENEFICIARIES,
          count = 1
        )
      )
    }

    test("failure - error retrieving cloud account") {
      cloudStoreAccountRepository.currentAccountResult = Err(CloudStoreAccountError())
      createBackgroundScope().launch {
        socRecCloudBackupSyncWorker.executeWork()
      }
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(
          action = Action.ACTION_APP_COUNT,
          counterId = SocialRecoveryEventTrackerCounterId.SOCREC_COUNT_TOTAL_TCS,
          count = 2
        )
      )
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(
          action = Action.ACTION_APP_COUNT,
          counterId = InheritanceEventTrackerCounterId.INHERITANCE_COUNT_TOTAL_BENEFICIARIES,
          count = 1
        )
      )
    }
  }
})
