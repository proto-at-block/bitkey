package build.wallet.cloud.backup.health

import build.wallet.account.AccountServiceFake
import build.wallet.availability.AppFunctionalityServiceFake
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.InactiveApp
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.createBackgroundScope
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.CloudBackupHealthLoggingFeatureFlag
import build.wallet.platform.app.AppSessionManagerFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch

class CloudBackupHealthSyncWorkerImplTests : FunSpec({

  val fullAccount = FullAccountMock
  val accountService = AccountServiceFake()
  val cloudBackupHealthRepository = CloudBackupHealthRepositoryMock(turbines::create)
  val appSessionManager = AppSessionManagerFake()
  val appFunctionalityService = AppFunctionalityServiceFake()
  val featureFlagDao = FeatureFlagDaoFake()
  val cloudBackupHealthLoggingFeatureFlag = CloudBackupHealthLoggingFeatureFlag(featureFlagDao)

  val worker = CloudBackupHealthSyncWorkerImpl(
    accountService = accountService,
    cloudBackupHealthRepository = cloudBackupHealthRepository,
    appSessionManager = appSessionManager,
    appFunctionalityService = appFunctionalityService,
    cloudBackupHealthLoggingFeatureFlag = cloudBackupHealthLoggingFeatureFlag
  )

  beforeTest {
    appFunctionalityService.reset()
    accountService.reset()
    cloudBackupHealthRepository.reset()
    appSessionManager.reset()
    featureFlagDao.reset()
  }

  test("does not perform sync when app is inactive") {
    appFunctionalityService.status.emit(
      AppFunctionalityStatus.LimitedFunctionality(InactiveApp)
    )
    accountService.setActiveAccount(fullAccount)

    createBackgroundScope().launch {
      worker.executeWork()
    }

    // Verify performSync was not called
    cloudBackupHealthRepository.performSyncCalls.expectNoEvents()
  }

  test("performs sync when account becomes active") {
    accountService.setActiveAccount(fullAccount)

    createBackgroundScope().launch {
      worker.executeWork()
    }

    // Verify performSync was called with the correct account
    val account = cloudBackupHealthRepository.performSyncCalls.awaitItem()
    account shouldBe fullAccount
  }

  test("performs new sync when account changes") {
    // Start with first account
    accountService.setActiveAccount(fullAccount)

    createBackgroundScope().launch {
      worker.executeWork()
    }

    // Verify first sync
    cloudBackupHealthRepository.performSyncCalls.awaitItem() shouldBe fullAccount

    // Change to a different account
    val differentAccount = FullAccountMock.copy(
      accountId = FullAccountMock.accountId.copy(serverId = "different-server-id")
    )
    accountService.setActiveAccount(differentAccount)

    // Execute work again (simulating OnEvent account changed)
    createBackgroundScope().launch {
      worker.executeWork()
    }

    // Should trigger new sync for the different account
    cloudBackupHealthRepository.performSyncCalls.awaitItem() shouldBe differentAccount
  }
})
