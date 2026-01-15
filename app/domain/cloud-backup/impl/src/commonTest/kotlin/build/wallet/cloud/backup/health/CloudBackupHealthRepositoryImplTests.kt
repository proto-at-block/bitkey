package build.wallet.cloud.backup.health

import build.wallet.availability.AppFunctionalityServiceFake
import build.wallet.availability.AppFunctionalityStatus.LimitedFunctionality
import build.wallet.availability.F8eUnreachable
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.*
import build.wallet.cloud.backup.CloudBackupOperationLockImpl
import build.wallet.cloud.backup.FullAccountCloudBackupCreator.FullAccountCloudBackupCreatorError.CsekMissing
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.local.CloudBackupDaoFake
import build.wallet.cloud.backup.v2.FullAccountFieldsMock
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.cloud.store.CloudError
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountRepositoryMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.emergencyexitkit.EmergencyExitKitData
import build.wallet.emergencyexitkit.EmergencyExitKitRepositoryFake
import build.wallet.encrypt.XCiphertext
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.CloudBackupForceReuploadTimestampFeatureFlag
import build.wallet.feature.flags.CloudBackupHealthLoggingFeatureFlag
import build.wallet.logging.LogLevel
import build.wallet.logging.LogWriterMock
import build.wallet.logging.Logger
import build.wallet.testing.shouldBeOk
import co.touchlab.kermit.Severity
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import okio.ByteString

class CloudBackupHealthRepositoryImplTests : FunSpec({

  val logWriter = LogWriterMock()
  val fullAccount = FullAccountMock
  val cloudAccount = CloudAccountMock(instanceId = "test-instance")
  val eekData = EmergencyExitKitData(
    pdfData = ByteString.EMPTY
  )

  val cloudStoreAccountRepository = CloudStoreAccountRepositoryMock()
  val cloudBackupRepository = CloudBackupRepositoryFake()
  val cloudBackupDao = CloudBackupDaoFake()
  val emergencyExitKitRepository = EmergencyExitKitRepositoryFake()
  val fullAccountCloudBackupRepairer = FullAccountCloudBackupRepairerFake()
  val appFunctionalityService = AppFunctionalityServiceFake()
  val jsonSerializer = JsonSerializer()
  val featureFlagDao = FeatureFlagDaoFake()
  val cloudBackupHealthLoggingFeatureFlag = CloudBackupHealthLoggingFeatureFlag(featureFlagDao)
  val cloudBackupForceReuploadTimestampFeatureFlag =
    CloudBackupForceReuploadTimestampFeatureFlag(featureFlagDao)
  val fullAccountCloudBackupCreator = FullAccountCloudBackupCreatorMock(turbines::create)

  fun createHealthRepository() =
    CloudBackupHealthRepositoryImpl(
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      cloudBackupRepository = cloudBackupRepository,
      cloudBackupDao = cloudBackupDao,
      emergencyExitKitRepository = emergencyExitKitRepository,
      fullAccountCloudBackupRepairer = fullAccountCloudBackupRepairer,
      appFunctionalityService = appFunctionalityService,
      jsonSerializer = jsonSerializer,
      cloudBackupHealthLoggingFeatureFlag = cloudBackupHealthLoggingFeatureFlag,
      cloudBackupForceReuploadTimestampFeatureFlag = cloudBackupForceReuploadTimestampFeatureFlag,
      fullAccountCloudBackupCreator = fullAccountCloudBackupCreator,
      cloudBackupOperationLock = CloudBackupOperationLockImpl()
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

  // Helper function to set up a backup mismatch scenario
  suspend fun setupBackupMismatch(
    localBackup: CloudBackup,
    cloudBackup: CloudBackup,
  ) {
    cloudStoreAccountRepository.set(cloudAccount)
    cloudBackupDao.set(fullAccount.accountId.serverId, localBackup)
    setCloudBackup(cloudAccount, cloudBackup)
  }

  // Helper function to filter mismatch warning logs
  fun LogWriterMock.mismatchLogs() =
    logs.filter {
      it.severity == Severity.Warn && it.message.contains("Backup mismatch")
    }

  // Helper function to filter size warning logs
  fun LogWriterMock.sizeWarningLogs() =
    logs.filter {
      it.severity == Severity.Warn && it.message.contains("Backup approaching 1MB")
    }

  // Helper function to create a large backup that exceeds the size warning threshold (900KB) but not the
  // size limit (1MB)
  fun createLargeBackup(): CloudBackupV3 {
    val largeDekMap = (1..7400).associate {
      "relationship-id-$it" to XCiphertext("cipher-text-$it-${"x".repeat(80)}.nonce-$it")
    }
    val largeFields = FullAccountFieldsMock.copy(socRecSealedDekMap = largeDekMap)
    return CloudBackupV3WithFullAccountMock.copy(fullAccountFields = largeFields)
  }

  beforeTest {
    cloudStoreAccountRepository.reset()
    cloudBackupRepository.reset()
    cloudBackupDao.reset()
    emergencyExitKitRepository.reset()
    fullAccountCloudBackupRepairer.reset()
    appFunctionalityService.reset()
    featureFlagDao.reset()
    fullAccountCloudBackupCreator.reset()
    logWriter.clear()
    Logger.configure(
      tag = "Test",
      minimumLogLevel = LogLevel.Verbose,
      logWriters = listOf(logWriter)
    )
  }

  context("parameterized tests for all backup versions") {
    AllFullAccountBackupMocks.forEach { cloudBackup ->
      val backupVersion = when (cloudBackup) {
        is CloudBackupV2 -> "v2"
        is CloudBackupV3 -> "v3"
        else -> "unknown"
      }

      context("cloud backup $backupVersion") {
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

        test("performSync - returns invalid backup when cloud and local backups don't match") {
          val healthRepository = createHealthRepository()
          val differentCloudBackup = when (cloudBackup) {
            is CloudBackupV2 -> cloudBackup.copy(accountId = "different-id")
            is CloudBackupV3 -> cloudBackup.copy(accountId = "different-id")
            else -> throw IllegalStateException("Unknown backup version: $cloudBackup")
          }

          cloudStoreAccountRepository.set(cloudAccount)
          cloudBackupDao.set(fullAccount.accountId.serverId, cloudBackup)
          setCloudBackup(cloudAccount, differentCloudBackup)

          val status = healthRepository.performSync(fullAccount)

          status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.ProblemWithBackup.InvalidBackup>()
          (status.appKeyBackupStatus as AppKeyBackupStatus.ProblemWithBackup.InvalidBackup).cloudBackup shouldBe differentCloudBackup
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
      }
    }
  }

  // Version-independent tests
  test("performSync - returns no cloud access when cloud account is missing") {
    val healthRepository = createHealthRepository()
    // No cloud account set

    val status = healthRepository.performSync(fullAccount)

    status.appKeyBackupStatus shouldBe AppKeyBackupStatus.ProblemWithBackup.NoCloudAccess
    status.eekBackupStatus shouldBe EekBackupStatus.ProblemWithBackup.NoCloudAccess
  }

  context("performSync - app key backup errors") {
    val cloudBackup = CloudBackupV2WithFullAccountMock

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
  }

  test("returns backup missing when eek read fails") {
    val healthRepository = createHealthRepository()
    val cloudBackup = CloudBackupV2WithFullAccountMock
    cloudStoreAccountRepository.set(cloudAccount)
    cloudBackupDao.set(fullAccount.accountId.serverId, cloudBackup)
    setCloudBackup(cloudAccount, cloudBackup)
    emergencyExitKitRepository.readError = Error("EEK read failed")

    val status = healthRepository.performSync(fullAccount)

    status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.Healthy>()
    status.eekBackupStatus shouldBe EekBackupStatus.ProblemWithBackup.BackupMissing
  }

  context("backup mismatch logging") {
    val localBackup = CloudBackupV3WithFullAccountMock

    test("logs backup mismatch when feature flag is enabled") {
      cloudBackupHealthLoggingFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
      val healthRepository = createHealthRepository()
      val cloudBackup = localBackup.copy(accountId = "different-account-id")
      setupBackupMismatch(localBackup, cloudBackup)

      val status = healthRepository.performSync(fullAccount)

      status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.ProblemWithBackup.InvalidBackup>()
      logWriter.mismatchLogs().shouldNotBeEmpty()
      logWriter.mismatchLogs().first().message shouldContain "accountId"
    }

    test("does not log backup mismatch when feature flag is disabled") {
      cloudBackupHealthLoggingFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
      val healthRepository = createHealthRepository()
      val cloudBackup = localBackup.copy(accountId = "different-account-id")
      setupBackupMismatch(localBackup, cloudBackup)

      val status = healthRepository.performSync(fullAccount)

      status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.ProblemWithBackup.InvalidBackup>()
      logWriter.mismatchLogs().shouldBeEmpty()
    }

    test("logs diff summary with changed fields") {
      cloudBackupHealthLoggingFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
      val healthRepository = createHealthRepository()
      val cloudBackup = localBackup.copy(
        accountId = "different-account-id",
        deviceNickname = "different-device"
      )
      setupBackupMismatch(localBackup, cloudBackup)

      healthRepository.performSync(fullAccount)

      val mismatchLog = logWriter.mismatchLogs().first()
      mismatchLog.message shouldContain "accountId"
      mismatchLog.message shouldContain "deviceNickname"
    }
  }

  context("backup size warning logging") {
    test("logs size warning when backup exceeds threshold and feature flag is enabled") {
      cloudBackupHealthLoggingFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
      val healthRepository = createHealthRepository()
      val largeBackup = createLargeBackup()

      cloudStoreAccountRepository.set(cloudAccount)
      cloudBackupDao.set(fullAccount.accountId.serverId, largeBackup)
      setCloudBackup(cloudAccount, largeBackup)
      emergencyExitKitRepository.setEekData(cloudAccount, eekData)

      healthRepository.performSync(fullAccount)

      logWriter.sizeWarningLogs().shouldNotBeEmpty()
      logWriter.sizeWarningLogs().first().message shouldContain "dek="
    }

    test("does not log size warning when feature flag is disabled") {
      cloudBackupHealthLoggingFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
      val healthRepository = createHealthRepository()
      val largeBackup = createLargeBackup()

      cloudStoreAccountRepository.set(cloudAccount)
      cloudBackupDao.set(fullAccount.accountId.serverId, largeBackup)
      setCloudBackup(cloudAccount, largeBackup)
      emergencyExitKitRepository.setEekData(cloudAccount, eekData)

      healthRepository.performSync(fullAccount)

      logWriter.sizeWarningLogs().shouldBeEmpty()
    }
  }

  context("backup size limit exceeded for full accounts") {
    // Helper to create an extremely large backup that exceeds 1MB limit
    fun createExtraLargeFullAccountBackup(isV2: Boolean = false): CloudBackup {
      // Create a backup over 1MB by adding a large socRecSealedDekMap
      val extraLargeDekMap = (1..15000).associate {
        "relationship-id-$it" to XCiphertext("cipher-text-$it-${"x".repeat(100)}.nonce-$it")
      }
      val largeFields = FullAccountFieldsMock.copy(socRecSealedDekMap = extraLargeDekMap)
      return if (isV2) {
        CloudBackupV2WithFullAccountMock.copy(fullAccountFields = largeFields)
      } else {
        CloudBackupV3WithFullAccountMock.copy(fullAccountFields = largeFields)
      }
    }

    test("creates fixed backup when backup size exceeds limit for V3 full account") {
      cloudBackupHealthLoggingFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
      val healthRepository = createHealthRepository()
      val oversizedBackup = createExtraLargeFullAccountBackup(isV2 = false) as CloudBackupV3

      // Setup the fixed backup that will be created
      val fixedBackup = CloudBackupV3WithFullAccountMock
      fullAccountCloudBackupCreator.backupResult = Ok(fixedBackup)

      cloudStoreAccountRepository.set(cloudAccount)
      setCloudBackup(cloudAccount, fixedBackup)
      cloudBackupDao.set(fullAccount.accountId.serverId, oversizedBackup)
      emergencyExitKitRepository.setEekData(cloudAccount, eekData)

      val status = healthRepository.performSync(fullAccount)

      // Should return Health status since the fixed backup will be used to silently repair
      status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.Healthy>()

      // Should have called the creator with the correct sealed CSEK and keybox
      val createCall =
        fullAccountCloudBackupCreator.createCalls.awaitItem() as Pair<SealedCsek, Keybox>
      val (actualSealedCsek, actualKeybox) = createCall
      actualSealedCsek shouldBe oversizedBackup.fullAccountFields!!.sealedHwEncryptionKey
      actualKeybox shouldBe fullAccount.keybox

      // Should have updated the DAO with the fixed backup
      val updatedBackup = cloudBackupDao.get(fullAccount.accountId.serverId)
      updatedBackup.shouldBeOk(fixedBackup)

      // Should log the size warning
      logWriter.sizeWarningLogs().shouldNotBeEmpty()
    }

    test("creates fixed backup when backup size exceeds limit for V2 full account") {
      cloudBackupHealthLoggingFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
      val healthRepository = createHealthRepository()
      val oversizedBackup = createExtraLargeFullAccountBackup(isV2 = true) as CloudBackupV2

      // Setup the fixed backup that will be created
      val fixedBackup = CloudBackupV2WithFullAccountMock
      fullAccountCloudBackupCreator.backupResult = Ok(fixedBackup)

      cloudStoreAccountRepository.set(cloudAccount)
      setCloudBackup(cloudAccount, fixedBackup)
      cloudBackupDao.set(fullAccount.accountId.serverId, oversizedBackup)
      emergencyExitKitRepository.setEekData(cloudAccount, eekData)

      val status = healthRepository.performSync(fullAccount)

      // Should return Health status since the fixed backup will be used to silently repair
      status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.Healthy>()

      // Should have called the creator with the correct sealed CSEK and keybox
      val createCall =
        fullAccountCloudBackupCreator.createCalls.awaitItem() as Pair<SealedCsek, Keybox>
      val (actualSealedCsek, actualKeybox) = createCall
      actualSealedCsek shouldBe oversizedBackup.fullAccountFields!!.sealedHwEncryptionKey
      actualKeybox shouldBe fullAccount.keybox

      // Should have updated the DAO with the fixed backup
      val updatedBackup = cloudBackupDao.get(fullAccount.accountId.serverId)
      updatedBackup.shouldBeOk(fixedBackup)

      // Should log the size warning
      logWriter.sizeWarningLogs().shouldNotBeEmpty()
    }

    test("handles fixed backup creation failure gracefully") {
      cloudBackupHealthLoggingFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
      val healthRepository = createHealthRepository()
      val oversizedBackup = createExtraLargeFullAccountBackup() as CloudBackupV3

      // Setup the creator to fail
      fullAccountCloudBackupCreator.backupResult = Err(CsekMissing)

      cloudStoreAccountRepository.set(cloudAccount)
      cloudBackupDao.set(fullAccount.accountId.serverId, oversizedBackup)
      setCloudBackup(cloudAccount, oversizedBackup)
      emergencyExitKitRepository.setEekData(cloudAccount, eekData)

      val status = healthRepository.performSync(fullAccount)

      // Should have called the creator
      fullAccountCloudBackupCreator.createCalls.awaitItem()

      // Should NOT crash, should continue with normal flow
      // Since the fix failed and backups match, should report as healthy
      status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.Healthy>()

      // DAO should still have the original oversized backup (not updated)
      val unchangedBackup = cloudBackupDao.get(fullAccount.accountId.serverId)
      unchangedBackup.shouldBeOk(oversizedBackup)
    }
  }

  context("force reupload timestamp feature flag") {
    beforeTest {
      cloudStoreAccountRepository.reset()
      cloudBackupRepository.reset()
      cloudBackupDao.reset()
      emergencyExitKitRepository.reset()
      fullAccountCloudBackupRepairer.reset()
      appFunctionalityService.reset()
      featureFlagDao.reset()
      fullAccountCloudBackupCreator.reset()
      logWriter.clear()
      Logger.configure(
        tag = "Test",
        minimumLogLevel = LogLevel.Verbose,
        logWriters = listOf(logWriter)
      )
      cloudStoreAccountRepository.set(cloudAccount)
    }

    test("marks backup as stale when timestamp is older than flag threshold") {
      val healthRepository = createHealthRepository()
      
      // Set up matching backups (would normally be healthy)
      val backup = CloudBackupV3WithFullAccountMock
      cloudBackupDao.set(fullAccount.accountId.serverId, backup)
      setCloudBackup(cloudAccount, backup)
      emergencyExitKitRepository.setEekData(cloudAccount, eekData)

      // Set feature flag to a timestamp in the future
      cloudBackupForceReuploadTimestampFeatureFlag.setFlagValue(
        FeatureFlagValue.StringFlag("2099-01-01T00:00:00Z")
      )
      cloudBackupForceReuploadTimestampFeatureFlag.initializeFromDao()

      val status = healthRepository.performSync(fullAccount)

      // Should report as stale backup due to timestamp
      status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.ProblemWithBackup.StaleBackup>()
      
      // Should log the reason
      logWriter.logs.any { 
        it.message.contains("older than force reupload threshold")
      }.shouldBe(true)
    }

    test("reports backup as healthy when timestamp is newer than flag threshold") {
      val healthRepository = createHealthRepository()
      
      // Set up matching backups
      val backup = CloudBackupV3WithFullAccountMock
      cloudBackupDao.set(fullAccount.accountId.serverId, backup)
      setCloudBackup(cloudAccount, backup)
      emergencyExitKitRepository.setEekData(cloudAccount, eekData)

      // Set feature flag to a timestamp in the past
      cloudBackupForceReuploadTimestampFeatureFlag.setFlagValue(
        FeatureFlagValue.StringFlag("2020-01-01T00:00:00Z")
      )
      cloudBackupForceReuploadTimestampFeatureFlag.initializeFromDao()

      val status = healthRepository.performSync(fullAccount)

      // Should report as healthy since backup is newer than threshold
      status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.Healthy>()
    }

    test("ignores feature flag when value is empty") {
      val healthRepository = createHealthRepository()
      
      // Set up matching backups
      val backup = CloudBackupV3WithFullAccountMock
      cloudBackupDao.set(fullAccount.accountId.serverId, backup)
      setCloudBackup(cloudAccount, backup)
      emergencyExitKitRepository.setEekData(cloudAccount, eekData)

      // Feature flag is empty (default)
      cloudBackupForceReuploadTimestampFeatureFlag.setFlagValue(
        FeatureFlagValue.StringFlag("")
      )
      cloudBackupForceReuploadTimestampFeatureFlag.initializeFromDao()

      val status = healthRepository.performSync(fullAccount)

      // Should report as healthy, flag is disabled
      status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.Healthy>()
    }

    test("handles invalid timestamp format gracefully") {
      val healthRepository = createHealthRepository()
      
      // Set up matching backups
      val backup = CloudBackupV3WithFullAccountMock
      cloudBackupDao.set(fullAccount.accountId.serverId, backup)
      setCloudBackup(cloudAccount, backup)
      emergencyExitKitRepository.setEekData(cloudAccount, eekData)

      // Set feature flag to invalid timestamp
      cloudBackupForceReuploadTimestampFeatureFlag.setFlagValue(
        FeatureFlagValue.StringFlag("not-a-valid-timestamp")
      )
      cloudBackupForceReuploadTimestampFeatureFlag.initializeFromDao()

      val status = healthRepository.performSync(fullAccount)

      // Should report as healthy, invalid timestamp is ignored
      status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.Healthy>()
      
      // Should log the error
      logWriter.logs.any {
        it.message.contains("Invalid timestamp format")
      }.shouldBe(true)
    }

    test("force reupload takes precedence over backup match check") {
      val healthRepository = createHealthRepository()
      
      // Set up matching backups that would normally be healthy
      val backup = CloudBackupV3WithFullAccountMock
      cloudBackupDao.set(fullAccount.accountId.serverId, backup)
      setCloudBackup(cloudAccount, backup)
      emergencyExitKitRepository.setEekData(cloudAccount, eekData)

      // Set feature flag to force reupload
      cloudBackupForceReuploadTimestampFeatureFlag.setFlagValue(
        FeatureFlagValue.StringFlag("2099-12-31T23:59:59Z")
      )
      cloudBackupForceReuploadTimestampFeatureFlag.initializeFromDao()

      val status = healthRepository.performSync(fullAccount)

      // Should report as stale even though backups match
      status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.ProblemWithBackup.StaleBackup>()
    }
  }
})
