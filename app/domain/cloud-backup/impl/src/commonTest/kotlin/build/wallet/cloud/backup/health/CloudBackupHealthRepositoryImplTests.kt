package build.wallet.cloud.backup.health

import app.cash.turbine.test
import build.wallet.availability.AppFunctionalityServiceFake
import build.wallet.availability.AppFunctionalityStatus.LimitedFunctionality
import build.wallet.availability.F8eUnreachable
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.cloud.backup.*
import build.wallet.cloud.backup.local.CloudBackupDaoFake
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.cloud.store.CloudError
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountRepositoryMock
import build.wallet.coroutines.createBackgroundScope
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.emergencyexitkit.EmergencyExitKitData
import build.wallet.emergencyexitkit.EmergencyExitKitRepositoryFake
import build.wallet.platform.app.AppSessionManagerFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.launch
import okio.ByteString

class CloudBackupHealthRepositoryImplTests : FunSpec({

  val fullAccount = FullAccountMock
  val cloudAccount = CloudAccountMock(instanceId = "test-instance")
  val cloudBackup = CloudBackupV2WithFullAccountMock
  val eekData = EmergencyExitKitData(
    pdfData = ByteString.EMPTY
  )

  val cloudStoreAccountRepository = CloudStoreAccountRepositoryMock()
  val cloudBackupRepository = CloudBackupRepositoryFake()
  val cloudBackupDao = CloudBackupDaoFake()
  val emergencyExitKitRepository = EmergencyExitKitRepositoryFake()
  val fullAccountCloudBackupRepairer = FullAccountCloudBackupRepairerFake()
  val appSessionManager = AppSessionManagerFake()
  val appFunctionalityService = AppFunctionalityServiceFake()

  fun createHealthRepository() =
    CloudBackupHealthRepositoryImpl(
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      cloudBackupRepository = cloudBackupRepository,
      cloudBackupDao = cloudBackupDao,
      emergencyExitKitRepository = emergencyExitKitRepository,
      fullAccountCloudBackupRepairer = fullAccountCloudBackupRepairer,
      appSessionManager = appSessionManager,
      appFunctionalityService = appFunctionalityService
    )

  // Helper function to set cloud backup and ensure it is available from the repository
  suspend fun setCloudBackup(
    account: CloudStoreAccount,
    backup: CloudBackup,
  ) {
    cloudBackupRepository.writeBackup(
      accountId = fullAccount.accountId,
      cloudStoreAccount = account,
      backup = backup,
      requireAuthRefresh = false
    )
    cloudBackupRepository.awaitBackup(account)
  }

  beforeTest {
    cloudStoreAccountRepository.reset()
    cloudBackupRepository.reset()
    cloudBackupDao.reset()
    emergencyExitKitRepository.reset()
    fullAccountCloudBackupRepairer.reset()
    appSessionManager.reset()
    appFunctionalityService.reset()
  }

  context("appKeyBackupStatus") {
    test("returns state flow with initial null value") {
      val healthRepository = createHealthRepository()
      val statusFlow = healthRepository.appKeyBackupStatus()
      statusFlow.value shouldBe null
    }

    test("emits updated values after sync") {
      val healthRepository = createHealthRepository()
      // Setup healthy backup scenario
      cloudStoreAccountRepository.set(cloudAccount)
      cloudBackupDao.set(fullAccount.accountId.serverId, cloudBackup)
      setCloudBackup(cloudAccount, cloudBackup)
      emergencyExitKitRepository.setEekData(cloudAccount, eekData)

      val status = healthRepository.performSync(fullAccount)
      status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.Healthy>()

      healthRepository.appKeyBackupStatus().value.shouldBeInstanceOf<AppKeyBackupStatus.Healthy>()
    }
  }

  context("eekBackupStatus") {
    test("returns state flow with initial null value") {
      val healthRepository = createHealthRepository()
      val statusFlow = healthRepository.eekBackupStatus()
      statusFlow.value shouldBe null
    }

    test("emits updated values after sync") {
      val healthRepository = createHealthRepository()
      // Setup healthy backup scenario
      cloudStoreAccountRepository.set(cloudAccount)
      cloudBackupDao.set(fullAccount.accountId.serverId, cloudBackup)
      setCloudBackup(cloudAccount, cloudBackup)
      emergencyExitKitRepository.setEekData(cloudAccount, eekData)

      val status = healthRepository.performSync(fullAccount)
      status.eekBackupStatus.shouldBeInstanceOf<EekBackupStatus.Healthy>()

      healthRepository.eekBackupStatus().value.shouldBeInstanceOf<EekBackupStatus.Healthy>()
    }
  }

  context("syncLoop") {
    test("performs initial sync on start") {
      val healthRepository = createHealthRepository()
      cloudStoreAccountRepository.set(cloudAccount)
      cloudBackupDao.set(fullAccount.accountId.serverId, cloudBackup)
      setCloudBackup(cloudAccount, cloudBackup)
      emergencyExitKitRepository.setEekData(cloudAccount, eekData)

      val backgroundScope = createBackgroundScope()

      // Start the sync loop first
      backgroundScope.launch {
        healthRepository.syncLoop(fullAccount)
      }

      // Now test that the initial sync is performed correctly
      healthRepository.appKeyBackupStatus().test {
        awaitUntil { it is AppKeyBackupStatus.Healthy }
      }

      healthRepository.eekBackupStatus().test {
        awaitUntil { it is EekBackupStatus.Healthy }
      }
    }

    xtest("TODO(W-11819): syncs when app enters foreground") {
      val healthRepository = createHealthRepository()
      cloudStoreAccountRepository.set(cloudAccount)
      cloudBackupDao.set(fullAccount.accountId.serverId, cloudBackup)
      emergencyExitKitRepository.setEekData(cloudAccount, eekData)

      val backgroundScope = createBackgroundScope()

      // Start the sync loop - this will trigger initial sync since app starts in FOREGROUND
      backgroundScope.launch {
        healthRepository.syncLoop(fullAccount)
      }

      // Verify the new sync detects the healthy backup
      healthRepository.appKeyBackupStatus().test {
        awaitUntil { it is AppKeyBackupStatus.ProblemWithBackup.BackupMissing }

        appSessionManager.appDidEnterBackground()
        setCloudBackup(cloudAccount, cloudBackup)

        // Foreground the app to trigger a sync
        appSessionManager.appDidEnterForeground()
        awaitUntil { it is AppKeyBackupStatus.Healthy }
      }
    }
  }

  test("triggers sync through requestSync") {
    val healthRepository = createHealthRepository()
    cloudStoreAccountRepository.set(cloudAccount)
    cloudBackupDao.set(fullAccount.accountId.serverId, cloudBackup)
    // Start with NO cloud backup - unhealthy state initially
    emergencyExitKitRepository.setEekData(cloudAccount, eekData)

    val backgroundScope = createBackgroundScope()

    // Start the sync loop first
    backgroundScope.launch {
      healthRepository.syncLoop(fullAccount)
    }

    // Wait for initial sync to detect unhealthy state
    healthRepository.appKeyBackupStatus().test {
      awaitUntil { it is AppKeyBackupStatus.ProblemWithBackup.BackupMissing }

      // Now make the backup healthy
      setCloudBackup(cloudAccount, cloudBackup)

      // Request another sync - this should detect the now-healthy backup
      healthRepository.requestSync(fullAccount)

      // Verify that the sync detects the healthy backup
      awaitUntil { it is AppKeyBackupStatus.Healthy }
    }
  }

  test("performSync - returns healthy status when everything is ok") {
    val healthRepository = createHealthRepository()
    cloudStoreAccountRepository.set(cloudAccount)
    cloudBackupDao.set(fullAccount.accountId.serverId, cloudBackup)
    setCloudBackup(cloudAccount, cloudBackup)
    emergencyExitKitRepository.setEekData(cloudAccount, eekData)

    val status = healthRepository.performSync(fullAccount)

    status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.Healthy>()
    status.eekBackupStatus.shouldBeInstanceOf<EekBackupStatus.Healthy>()
  }

  test("performSync - returns no cloud access when cloud account is missing") {
    val healthRepository = createHealthRepository()
    // No cloud account set

    val status = healthRepository.performSync(fullAccount)

    status.appKeyBackupStatus shouldBe AppKeyBackupStatus.ProblemWithBackup.NoCloudAccess
    status.eekBackupStatus shouldBe EekBackupStatus.ProblemWithBackup.NoCloudAccess
  }

  context("performSync - app key backup errors") {
    test("returns connectivity unavailable when cloud backup health feature is unavailable") {
      val healthRepository = createHealthRepository()
      cloudStoreAccountRepository.set(cloudAccount)
      // Set app functionality to have limited functionality (which makes cloudBackupHealth unavailable)
      appFunctionalityService.status.value = LimitedFunctionality(F8eUnreachable(null))

      val status = healthRepository.performSync(fullAccount)

      status.appKeyBackupStatus shouldBe AppKeyBackupStatus.ProblemWithBackup.ConnectivityUnavailable
    }

    test("returns backup missing when local backup is missing") {
      val healthRepository = createHealthRepository()
      cloudStoreAccountRepository.set(cloudAccount)
      // No local backup set

      val status = healthRepository.performSync(fullAccount)

      status.appKeyBackupStatus shouldBe AppKeyBackupStatus.ProblemWithBackup.BackupMissing
    }

    test("returns backup missing when local backup dao returns error") {
      val healthRepository = createHealthRepository()
      cloudStoreAccountRepository.set(cloudAccount)
      cloudBackupDao.returnError = true

      val status = healthRepository.performSync(fullAccount)

      status.appKeyBackupStatus shouldBe AppKeyBackupStatus.ProblemWithBackup.BackupMissing
    }

    test("returns backup missing when cloud backup is null") {
      val healthRepository = createHealthRepository()
      cloudStoreAccountRepository.set(cloudAccount)
      cloudBackupDao.set(fullAccount.accountId.serverId, cloudBackup)
      // No cloud backup set

      val status = healthRepository.performSync(fullAccount)

      status.appKeyBackupStatus shouldBe AppKeyBackupStatus.ProblemWithBackup.BackupMissing
    }

    test("returns no cloud access when cloud backup read fails") {
      val healthRepository = createHealthRepository()
      cloudStoreAccountRepository.set(cloudAccount)
      cloudBackupDao.set(fullAccount.accountId.serverId, cloudBackup)
      cloudBackupRepository.returnReadError =
        CloudBackupError.UnrectifiableCloudBackupError(CloudError())

      val status = healthRepository.performSync(fullAccount)

      status.appKeyBackupStatus shouldBe AppKeyBackupStatus.ProblemWithBackup.NoCloudAccess
    }

    test("returns invalid backup when cloud and local backups don't match") {
      val healthRepository = createHealthRepository()
      val differentCloudBackup = cloudBackup.copy(accountId = "different-id")

      cloudStoreAccountRepository.set(cloudAccount)
      cloudBackupDao.set(fullAccount.accountId.serverId, cloudBackup)
      setCloudBackup(cloudAccount, differentCloudBackup)

      val status = healthRepository.performSync(fullAccount)

      status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.ProblemWithBackup.InvalidBackup>()
      (status.appKeyBackupStatus as AppKeyBackupStatus.ProblemWithBackup.InvalidBackup).cloudBackup shouldBe differentCloudBackup
    }
  }

  test("returns backup missing when eek read fails") {
    val healthRepository = createHealthRepository()
    cloudStoreAccountRepository.set(cloudAccount)
    cloudBackupDao.set(fullAccount.accountId.serverId, cloudBackup)
    setCloudBackup(cloudAccount, cloudBackup)
    emergencyExitKitRepository.readError = Error("EEK read failed")

    val status = healthRepository.performSync(fullAccount)

    status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.Healthy>()
    status.eekBackupStatus shouldBe EekBackupStatus.ProblemWithBackup.BackupMissing
  }

  test("attempts repair when backup is unhealthy") {
    val healthRepository = createHealthRepository()
    cloudStoreAccountRepository.set(cloudAccount)
    cloudBackupDao.set(fullAccount.accountId.serverId, cloudBackup)
    // No cloud backup - unhealthy scenario

    fullAccountCloudBackupRepairer.onRepairAttempt = {
      setCloudBackup(cloudAccount, cloudBackup)
    }

    val status = healthRepository.performSync(fullAccount)
    status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.Healthy>()

    // Verify repair was attempted
    fullAccountCloudBackupRepairer.attemptRepairCalls.size shouldBe 1
    val repairCall = fullAccountCloudBackupRepairer.attemptRepairCalls.first()
    repairCall.account shouldBe fullAccount
    repairCall.cloudStoreAccount shouldBe cloudAccount
    repairCall.cloudBackupStatus.appKeyBackupStatus shouldBe AppKeyBackupStatus.ProblemWithBackup.BackupMissing
  }
})
