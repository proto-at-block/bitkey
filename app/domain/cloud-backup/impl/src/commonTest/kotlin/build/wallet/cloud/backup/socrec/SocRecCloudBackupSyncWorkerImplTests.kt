package build.wallet.cloud.backup.socrec

import bitkey.relationships.Relationships
import build.wallet.account.AccountServiceFake
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.events.count.id.InheritanceEventTrackerCounterId
import build.wallet.analytics.events.count.id.SocialRecoveryEventTrackerCounterId
import build.wallet.analytics.v1.Action
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.relationships.EndorsedBeneficiaryFake
import build.wallet.bitkey.relationships.EndorsedTrustedContactFake1
import build.wallet.cloud.backup.*
import build.wallet.cloud.backup.FullAccountCloudBackupCreator.FullAccountCloudBackupCreatorError.FullAccountFieldsCreationError
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
import build.wallet.relationships.RelationshipsServiceMock
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
  val cloudBackupRepository = CloudBackupRepositoryFake()
  val fullAccountCloudBackupCreator = FullAccountCloudBackupCreatorMock(turbines::create)
  val eventTracker = EventTrackerMock(turbines::create)

  val otherBackup = CloudBackupV2WithFullAccountMock.copy(
    fullAccountFields = (CloudBackupV2WithFullAccountMock.fullAccountFields as FullAccountFields).copy(
      socRecSealedDekMap = mapOf()
    )
  )

  val cloudAccount = CloudAccountMock(cloudInstanceId)

  val socRecCloudBackupSyncWorker = SocRecCloudBackupSyncWorkerImpl(
    accountService = accountService,
    recoveryStatusService = recoveryStatusService,
    relationshipsService = relationshipsService,
    cloudBackupDao = cloudBackupDao,
    cloudStoreAccountRepository = cloudStoreAccountRepository,
    cloudBackupRepository = cloudBackupRepository,
    fullAccountCloudBackupCreator = fullAccountCloudBackupCreator,
    eventTracker = eventTracker,
    clock = ClockFake(),
    appSessionManager = AppSessionManagerFake()
  )

  beforeTest {
    cloudBackupDao.clear()
    cloudBackupDao.set(fullAccount.accountId.serverId, otherBackup)
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudAccount)
    fullAccountCloudBackupCreator.backupResult = Ok(CloudBackupV2WithFullAccountMock)
    cloudBackupRepository.reset()
    accountService.reset()
    accountService.setActiveAccount(fullAccount)
    recoveryStatusService.reset()
  }

  afterTest {
    relationshipsService.relationships.emit(RelationshipsFake)
    cloudStoreAccountRepository.reset()
    fullAccountCloudBackupCreator.reset()
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
    cloudBackupRepository.awaitBackup(cloudAccount)
      .shouldBe(CloudBackupV2WithFullAccountMock)
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
    cloudBackupRepository.awaitBackup(cloudAccount).shouldBe(CloudBackupV2WithFullAccountMock)
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
    cloudBackupRepository.awaitBackup(cloudAccount).shouldBe(CloudBackupV2WithFullAccountMock)
    relationshipsService.relationships.emit(RelationshipsFake)
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

  test("success - cloud backup v1 always accepted") {
    // Setting this to empty to match what would be found in an old backup.
    // An upload should be triggered even if no trusted contacts are known.
    relationshipsService.relationships.emit(Relationships.EMPTY)
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
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
    cloudBackupRepository.awaitBackup(cloudAccount)
      .shouldBe(CloudBackupV2WithFullAccountMock)
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

  test("failure - error writing cloud backup") {
    cloudBackupRepository.returnWriteError =
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
    cloudBackupRepository.awaitNoBackups()
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
    cloudBackupRepository.awaitNoBackups()
  }
})
