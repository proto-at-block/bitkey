package build.wallet.statemachine.data.keybox

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.events.count.id.SocialRecoveryEventTrackerCounterId
import build.wallet.analytics.v1.Action
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.relationships.EndorsedTrustedContactFake1
import build.wallet.cloud.backup.*
import build.wallet.cloud.backup.FullAccountCloudBackupCreator.FullAccountCloudBackupCreatorError.FullAccountFieldsCreationError
import build.wallet.cloud.backup.local.CloudBackupDaoFake
import build.wallet.cloud.backup.v2.FullAccountFields
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.cloud.store.CloudStoreAccountError
import build.wallet.cloud.store.CloudStoreAccountRepositoryMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.relationships.Relationships
import build.wallet.f8e.relationships.RelationshipsFake
import build.wallet.recovery.socrec.SocRecServiceFake
import build.wallet.testing.shouldBeOk
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class CloudBackupRefresherImplTests : FunSpec({

  val cloudInstanceId = "fake"
  val fullAccount = FullAccountMock
  val socRecService = SocRecServiceFake()
  val cloudBackupDao = CloudBackupDaoFake()
  val cloudStoreAccountRepository = CloudStoreAccountRepositoryMock()
  val cloudBackupRepository = CloudBackupRepositoryFake()
  val fullAccountCloudBackupCreator = FullAccountCloudBackupCreatorMock(turbines::create)
  val eventTracker = EventTrackerMock(turbines::create)

  val otherBackup =
    CloudBackupV2WithFullAccountMock.copy(
      fullAccountFields =
        (CloudBackupV2WithFullAccountMock.fullAccountFields as FullAccountFields).copy(
          socRecSealedDekMap = mapOf()
        )
    )

  val cloudAccount = CloudAccountMock(cloudInstanceId)

  val trustedContactCloudBackupRefresher =
    TrustedContactCloudBackupRefresherImpl(
      socRecService = socRecService,
      cloudBackupDao = cloudBackupDao,
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      cloudBackupRepository = cloudBackupRepository,
      fullAccountCloudBackupCreator = fullAccountCloudBackupCreator,
      eventTracker = eventTracker,
      clock = ClockFake()
    )

  beforeTest {
    cloudBackupDao.clear()
    cloudBackupDao.set(fullAccount.accountId.serverId, otherBackup)
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudAccount)
    fullAccountCloudBackupCreator.backupResult = Ok(CloudBackupV2WithFullAccountMock)
    cloudBackupRepository.reset()
  }

  afterTest {
    socRecService.socRecRelationships.emit(RelationshipsFake)
    cloudStoreAccountRepository.reset()
    fullAccountCloudBackupCreator.reset()
  }

  test("success") {
    runTest {
      backgroundScope.launch {
        trustedContactCloudBackupRefresher.refreshCloudBackupsWhenNecessary(backgroundScope, fullAccount)
      }
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(
          action = Action.ACTION_APP_COUNT,
          counterId = SocialRecoveryEventTrackerCounterId.SOCREC_COUNT_TOTAL_TCS,
          count = 2
        )
      )
      fullAccountCloudBackupCreator.createCalls.awaitItem()
      cloudBackupRepository.readBackup(
        cloudAccount
      ).shouldBeOk(CloudBackupV2WithFullAccountMock)
    }
  }

  test("success - multiple") {
    runTest {
      backgroundScope.launch {
        trustedContactCloudBackupRefresher.refreshCloudBackupsWhenNecessary(backgroundScope, fullAccount)
      }

      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(
          action = Action.ACTION_APP_COUNT,
          counterId = SocialRecoveryEventTrackerCounterId.SOCREC_COUNT_TOTAL_TCS,
          count = 2
        )
      )

      fullAccountCloudBackupCreator.createCalls.awaitItem()
      cloudBackupRepository.readBackup(
        cloudAccount
      ).shouldBeOk(CloudBackupV2WithFullAccountMock)
      socRecService.socRecRelationships
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

      fullAccountCloudBackupCreator.createCalls.awaitItem()
    }
  }

  test("success - duplicate ignored") {
    runTest {
      backgroundScope.launch {
        trustedContactCloudBackupRefresher.refreshCloudBackupsWhenNecessary(backgroundScope, fullAccount)
      }

      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(
          action = Action.ACTION_APP_COUNT,
          counterId = SocialRecoveryEventTrackerCounterId.SOCREC_COUNT_TOTAL_TCS,
          count = 2
        )
      )

      fullAccountCloudBackupCreator.createCalls.awaitItem()
      cloudBackupRepository.readBackup(
        cloudAccount
      ).shouldBeOk(CloudBackupV2WithFullAccountMock)
      socRecService.socRecRelationships.emit(RelationshipsFake)
      runCurrent()
    }
  }

  test("success - equal trusted contacts sets ignored") {
    runTest {
      cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithLiteAccountMock)

      backgroundScope.launch {
        trustedContactCloudBackupRefresher.refreshCloudBackupsWhenNecessary(backgroundScope, fullAccount)
      }
      runCurrent()
    }
  }

  test("failure - equal trusted contacts maps ignored") {
    runTest {
      cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithLiteAccountMock)
      backgroundScope.launch {
        trustedContactCloudBackupRefresher.refreshCloudBackupsWhenNecessary(backgroundScope, fullAccount)
      }
      runCurrent()
    }
  }

  test("success - cloud backup v1 always accepted") {
    runTest {
      // Setting this to empty to match what would be found in an old backup.
      // An upload should be triggered even if no trusted contacts are known.
      socRecService.socRecRelationships.emit(Relationships.EMPTY)
      cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
      backgroundScope.launch {
        trustedContactCloudBackupRefresher.refreshCloudBackupsWhenNecessary(backgroundScope, fullAccount)
      }
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(
          action = Action.ACTION_APP_COUNT,
          counterId = SocialRecoveryEventTrackerCounterId.SOCREC_COUNT_TOTAL_TCS,
          count = 0
        )
      )
      fullAccountCloudBackupCreator.createCalls.awaitItem()
      cloudBackupRepository.readBackup(
        cloudAccount
      ).shouldBeOk(CloudBackupV2WithFullAccountMock)
    }
  }

  test("failure - null cloud backup ignored") {
    runTest {
      cloudBackupDao.clear()
      backgroundScope.launch {
        trustedContactCloudBackupRefresher.refreshCloudBackupsWhenNecessary(backgroundScope, fullAccount)
      }

      runCurrent()
    }
  }

  test("failure - no cloud account") {
    runTest {
      cloudStoreAccountRepository.currentAccountResult = Ok(null)
      backgroundScope.launch {
        trustedContactCloudBackupRefresher.refreshCloudBackupsWhenNecessary(backgroundScope, fullAccount)
      }
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(
          action = Action.ACTION_APP_COUNT,
          counterId = SocialRecoveryEventTrackerCounterId.SOCREC_COUNT_TOTAL_TCS,
          count = 2
        )
      )
      runCurrent()
    }
  }

  test("failure - error retrieving cloud account") {
    runTest {
      cloudStoreAccountRepository.currentAccountResult = Err(CloudStoreAccountError())
      backgroundScope.launch {
        trustedContactCloudBackupRefresher.refreshCloudBackupsWhenNecessary(backgroundScope, fullAccount)
      }
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(
          action = Action.ACTION_APP_COUNT,
          counterId = SocialRecoveryEventTrackerCounterId.SOCREC_COUNT_TOTAL_TCS,
          count = 2
        )
      )
      runCurrent()
    }
  }

  test("failure - error writing cloud backup") {
    runTest {
      cloudBackupRepository.returnWriteError =
        CloudBackupError.UnrectifiableCloudBackupError(Throwable())
      backgroundScope.launch {
        trustedContactCloudBackupRefresher.refreshCloudBackupsWhenNecessary(backgroundScope, fullAccount)
      }
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(
          action = Action.ACTION_APP_COUNT,
          counterId = SocialRecoveryEventTrackerCounterId.SOCREC_COUNT_TOTAL_TCS,
          count = 2
        )
      )
      fullAccountCloudBackupCreator.createCalls.awaitItem()
      cloudBackupRepository.shouldBeEmpty()
    }
  }

  test("failure - error creating backup") {
    runTest {
      fullAccountCloudBackupCreator.backupResult =
        Err(FullAccountFieldsCreationError())
      backgroundScope.launch {
        trustedContactCloudBackupRefresher.refreshCloudBackupsWhenNecessary(backgroundScope, fullAccount)
      }
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(
          action = Action.ACTION_APP_COUNT,
          counterId = SocialRecoveryEventTrackerCounterId.SOCREC_COUNT_TOTAL_TCS,
          count = 2
        )
      )
      fullAccountCloudBackupCreator.createCalls.awaitItem()
      runCurrent()
      cloudBackupRepository.shouldBeEmpty()
    }
  }
})
