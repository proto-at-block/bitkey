package build.wallet.cloud.backup.health

import bitkey.account.HardwareType
import build.wallet.availability.AppFunctionalityServiceFake
import build.wallet.availability.AppFunctionalityStatus.LimitedFunctionality
import build.wallet.availability.F8eUnreachable
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.*
import build.wallet.cloud.backup.FullAccountCloudBackupCreator.FullAccountCloudBackupCreatorError.CsekMissing
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.CloudBackupOperationLockImpl
import build.wallet.cloud.backup.local.CloudBackupDaoFake
import build.wallet.cloud.backup.v2.FullAccountFieldsMock
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.cloud.store.CloudError
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountRepositoryMock
import build.wallet.emergencyexitkit.EmergencyExitKitData
import build.wallet.emergencyexitkit.EmergencyExitKitRepositoryFake
import build.wallet.encrypt.XCiphertext
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.CloudBackupForceReuploadTimestampFeatureFlag
import build.wallet.testing.shouldBeOk
import build.wallet.logging.LogLevel
import build.wallet.logging.LogWriterMock
import build.wallet.logging.Logger
import build.wallet.coroutines.turbine.turbines
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
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
  val cloudBackupService = CloudBackupServiceFake()
  val cloudBackupDao = CloudBackupDaoFake()
  val emergencyExitKitRepository = EmergencyExitKitRepositoryFake()
  val fullAccountCloudBackupRepairer = FullAccountCloudBackupRepairerFake()
  val appFunctionalityService = AppFunctionalityServiceFake()
  val jsonSerializer = JsonSerializer()
  val featureFlagDao = FeatureFlagDaoFake()
  val cloudBackupForceReuploadTimestampFeatureFlag =
    CloudBackupForceReuploadTimestampFeatureFlag(featureFlagDao)
  val fullAccountCloudBackupCreator = FullAccountCloudBackupCreatorMock(turbines::create)

  fun createHealthRepository() =
    CloudBackupHealthRepositoryImpl(
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      cloudBackupService = cloudBackupService,
      cloudBackupDao = cloudBackupDao,
      emergencyExitKitRepository = emergencyExitKitRepository,
      fullAccountCloudBackupRepairer = fullAccountCloudBackupRepairer,
      appFunctionalityService = appFunctionalityService,
      jsonSerializer = jsonSerializer,
      cloudBackupForceReuploadTimestampFeatureFlag = cloudBackupForceReuploadTimestampFeatureFlag,
      fullAccountCloudBackupCreator = fullAccountCloudBackupCreator,
      cloudBackupOperationLock = CloudBackupOperationLockImpl()
    )

  // Helper function to set cloud backup and ensure it is available from the repository
  suspend fun setCloudBackup(
    account: CloudStoreAccount,
    backup: CloudBackup,
  ) {
    cloudBackupService.writeBackup(
      accountId = fullAccount.accountId,
      cloudStoreAccount = account,
      backup = backup,
      requireAuthRefresh = false
    )
    cloudBackupService.awaitBackup(account)
  }

  beforeTest {
    cloudStoreAccountRepository.reset()
    cloudBackupService.reset()
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

            val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)
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

            val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)
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

          val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)

          status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.Healthy>()
          status.eekBackupStatus.shouldBeInstanceOf<EekBackupStatus.Healthy>()
        }

        test("performSync - returns invalid backup when cloud and local backups don't match") {
          val healthRepository = createHealthRepository()
          val differentCloudBackup = when (cloudBackup) {
            is CloudBackupV2 -> cloudBackup.copy(accountId = "different-id")
            is CloudBackupV3 -> cloudBackup.copy(accountId = "different-id")
            else -> error("Unknown backup version: $cloudBackup")
          }

          cloudStoreAccountRepository.set(cloudAccount)
          cloudBackupDao.set(fullAccount.accountId.serverId, cloudBackup)
          setCloudBackup(cloudAccount, differentCloudBackup)

          val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)

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

          val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)
          status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.Healthy>()

          // Verify repair was attempted
          fullAccountCloudBackupRepairer.attemptRepairCalls.size shouldBe 1
          val repairCall = fullAccountCloudBackupRepairer.attemptRepairCalls.first()
          repairCall.accountId shouldBe fullAccount.accountId
          repairCall.keybox shouldBe fullAccount.keybox
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

    val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)

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

      val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)

      status.appKeyBackupStatus shouldBe AppKeyBackupStatus.ProblemWithBackup.ConnectivityUnavailable
    }

    test("returns backup missing when local backup is missing") {
      val healthRepository = createHealthRepository()
      cloudStoreAccountRepository.set(cloudAccount)
      // No local backup set

      val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)

      status.appKeyBackupStatus shouldBe AppKeyBackupStatus.ProblemWithBackup.BackupMissing
    }

    test("returns backup missing when local backup dao returns error") {
      val healthRepository = createHealthRepository()
      cloudStoreAccountRepository.set(cloudAccount)
      cloudBackupDao.returnError = true

      val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)

      status.appKeyBackupStatus shouldBe AppKeyBackupStatus.ProblemWithBackup.BackupMissing
    }

    test("returns backup missing when cloud backup is null") {
      val healthRepository = createHealthRepository()
      cloudStoreAccountRepository.set(cloudAccount)
      cloudBackupDao.set(fullAccount.accountId.serverId, cloudBackup)
      // No cloud backup set

      val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)

      status.appKeyBackupStatus shouldBe AppKeyBackupStatus.ProblemWithBackup.BackupMissing
    }

    test("returns no cloud access when cloud backup read fails") {
      val healthRepository = createHealthRepository()
      cloudStoreAccountRepository.set(cloudAccount)
      cloudBackupDao.set(fullAccount.accountId.serverId, cloudBackup)
      cloudBackupService.returnReadError =
        CloudBackupError.UnrectifiableCloudBackupError(CloudError())

      val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)

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

    val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)

    status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.Healthy>()
    status.eekBackupStatus shouldBe EekBackupStatus.ProblemWithBackup.BackupMissing
  }

  context("backup size limit exceeded for full accounts") {
    fun createOversizedBackup(isV2: Boolean): CloudBackup {
      val oversizedDekMap = (1..15000).associate {
        "relationship-id-$it" to XCiphertext("cipher-text-$it-${"x".repeat(100)}.nonce-$it")
      }
      val fields = FullAccountFieldsMock.copy(socRecSealedDekMap = oversizedDekMap)
      return if (isV2) {
        CloudBackupV2WithFullAccountMock.copy(fullAccountFields = fields)
      } else {
        CloudBackupV3WithFullAccountMock.copy(fullAccountFields = fields)
      }
    }

    listOf(false, true).forEach { isV2 ->
      test("repairs oversized ${if (isV2) "V2" else "V3"} full account backup") {
        val healthRepository = createHealthRepository()
        val oversizedBackup = createOversizedBackup(isV2)
        val fixedBackup = if (isV2) CloudBackupV2WithFullAccountMock else CloudBackupV3WithFullAccountMock
        fullAccountCloudBackupCreator.backupResult = Ok(fixedBackup)
        cloudStoreAccountRepository.set(cloudAccount)
        setCloudBackup(cloudAccount, fixedBackup)
        cloudBackupDao.set(fullAccount.accountId.serverId, oversizedBackup)
        emergencyExitKitRepository.setEekData(cloudAccount, eekData)

        val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)

        status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.Healthy>()
        val (sealedCsek, keybox) =
          fullAccountCloudBackupCreator.createCalls.awaitItem() as Pair<SealedCsek, Keybox>
        sealedCsek shouldBe when (oversizedBackup) {
          is CloudBackupV2 -> oversizedBackup.fullAccountFields!!.sealedHwEncryptionKey
          is CloudBackupV3 -> oversizedBackup.fullAccountFields!!.sealedHwEncryptionKey
        }
        keybox shouldBe fullAccount.keybox
        cloudBackupDao.get(fullAccount.accountId.serverId).shouldBeOk(fixedBackup)
      }
    }

    test("continues when oversized backup repair fails") {
      val healthRepository = createHealthRepository()
      val oversizedBackup = createOversizedBackup(isV2 = false)
      fullAccountCloudBackupCreator.backupResult = Err(CsekMissing)
      cloudStoreAccountRepository.set(cloudAccount)
      cloudBackupDao.set(fullAccount.accountId.serverId, oversizedBackup)
      setCloudBackup(cloudAccount, oversizedBackup)
      emergencyExitKitRepository.setEekData(cloudAccount, eekData)

      val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)

      fullAccountCloudBackupCreator.createCalls.awaitItem()
      status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.Healthy>()
      cloudBackupDao.get(fullAccount.accountId.serverId).shouldBeOk(oversizedBackup)
    }
  }

  context("app global auth key hw signature check") {
    // Backup written before the real hw signature was captured (keybox now has the real one).
    fun withPlaceholderSignature(backup: CloudBackup): CloudBackup {
      val placeholderFields = FullAccountFieldsMock.copy(
        appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature(
          AppGlobalAuthKeyHwSignature.W3_ONBOARDING_PLACEHOLDER
        )
      )
      return when (backup) {
        is CloudBackupV2 -> backup.copy(fullAccountFields = placeholderFields)
        is CloudBackupV3 -> backup.copy(fullAccountFields = placeholderFields)
      }
    }

    AllFullAccountBackupMocks.forEach { cloudBackup ->
      val backupVersion = when (cloudBackup) {
        is CloudBackupV2 -> "v2"
        is CloudBackupV3 -> "v3"
        else -> "unknown"
      }

      test("$backupVersion - regenerates backup and reports stale when backup has a placeholder signature") {
        val healthRepository = createHealthRepository()
        val staleBackup = withPlaceholderSignature(cloudBackup)

        fullAccountCloudBackupCreator.backupResult = Ok(cloudBackup)

        cloudStoreAccountRepository.set(cloudAccount)
        cloudBackupDao.set(fullAccount.accountId.serverId, staleBackup)
        setCloudBackup(cloudAccount, staleBackup)
        emergencyExitKitRepository.setEekData(cloudAccount, eekData)

        // Make the post-repair re-sync healthy by uploading the fixed backup during repair.
        fullAccountCloudBackupRepairer.onRepairAttempt = {
          setCloudBackup(cloudAccount, cloudBackup)
        }

        val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)

        status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.Healthy>()

        val createCall =
          fullAccountCloudBackupCreator.createCalls.awaitItem() as Pair<SealedCsek, Keybox>
        val (actualSealedCsek, actualKeybox) = createCall
        actualSealedCsek shouldBe FullAccountFieldsMock.sealedHwEncryptionKey
        actualKeybox shouldBe fullAccount.keybox

        cloudBackupDao.get(fullAccount.accountId.serverId).shouldBeOk(cloudBackup)

        fullAccountCloudBackupRepairer.attemptRepairCalls.size shouldBe 1
        fullAccountCloudBackupRepairer.attemptRepairCalls.first()
          .cloudBackupStatus.appKeyBackupStatus shouldBe AppKeyBackupStatus.ProblemWithBackup.StaleBackup
      }

      test("$backupVersion - reports no cloud access when backup ownership cannot be confirmed") {
        val healthRepository = createHealthRepository()
        val staleBackup = withPlaceholderSignature(cloudBackup)

        cloudStoreAccountRepository.set(cloudAccount)
        cloudBackupDao.set(fullAccount.accountId.serverId, staleBackup)
        setCloudBackup(cloudAccount, staleBackup)
        emergencyExitKitRepository.setEekData(cloudAccount, eekData)
        cloudBackupService.returnReadError =
          CloudBackupError.UnrectifiableCloudBackupError(CloudError())

        val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)

        status.appKeyBackupStatus shouldBe AppKeyBackupStatus.ProblemWithBackup.NoCloudAccess
        fullAccountCloudBackupCreator.createCalls.expectNoEvents()
        cloudBackupDao.get(fullAccount.accountId.serverId).shouldBeOk(staleBackup)
      }

      test("$backupVersion - does not report stale when cloud backup belongs to another account") {
        val healthRepository = createHealthRepository()
        val staleBackup = withPlaceholderSignature(cloudBackup)
        val otherAccountBackup = when (cloudBackup) {
          is CloudBackupV2 -> cloudBackup.copy(accountId = "other-account-id")
          is CloudBackupV3 -> cloudBackup.copy(accountId = "other-account-id")
          else -> error("Unknown backup version: $cloudBackup")
        }

        fullAccountCloudBackupCreator.backupResult = Ok(cloudBackup)

        cloudStoreAccountRepository.set(cloudAccount)
        cloudBackupDao.set(fullAccount.accountId.serverId, staleBackup)
        // Shared cloud account: the active cloud backup belongs to a different account.
        setCloudBackup(cloudAccount, otherAccountBackup)
        emergencyExitKitRepository.setEekData(cloudAccount, eekData)

        val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)

        // Not reported stale: the repairer's StaleBackup path uploads unconditionally and
        // would overwrite the other account's backup. The mismatch surfaces as InvalidBackup,
        // whose repair branch refuses to overwrite a different account's backup.
        fullAccountCloudBackupCreator.createCalls.expectNoEvents()
        status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.ProblemWithBackup.InvalidBackup>()
        cloudBackupDao.get(fullAccount.accountId.serverId).shouldBeOk(staleBackup)
      }

      test("$backupVersion - does not report stale when backup regeneration fails") {
        val healthRepository = createHealthRepository()
        val staleBackup = withPlaceholderSignature(cloudBackup)

        fullAccountCloudBackupCreator.backupResult = Err(CsekMissing)

        cloudStoreAccountRepository.set(cloudAccount)
        cloudBackupDao.set(fullAccount.accountId.serverId, staleBackup)
        setCloudBackup(cloudAccount, staleBackup)
        emergencyExitKitRepository.setEekData(cloudAccount, eekData)

        val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)

        // The failed repair remains visible after performSync's post-repair health check.
        fullAccountCloudBackupCreator.createCalls.awaitItem()
        fullAccountCloudBackupCreator.createCalls.awaitItem()

        status.appKeyBackupStatus shouldBe
          AppKeyBackupStatus.ProblemWithBackup.PlaceholderSignatureRepairFailed
        fullAccountCloudBackupRepairer.attemptRepairCalls.size shouldBe 1
        cloudBackupDao.get(fullAccount.accountId.serverId).shouldBeOk(staleBackup)
      }

      test("$backupVersion - does not report stale when persisting the regenerated backup fails") {
        val healthRepository = createHealthRepository()
        val staleBackup = withPlaceholderSignature(cloudBackup)

        fullAccountCloudBackupCreator.backupResult = Ok(cloudBackup)

        cloudStoreAccountRepository.set(cloudAccount)
        cloudBackupDao.set(fullAccount.accountId.serverId, staleBackup)
        setCloudBackup(cloudAccount, staleBackup)
        emergencyExitKitRepository.setEekData(cloudAccount, eekData)

        cloudBackupDao.returnErrorOnSet = true

        val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)

        // The failed repair remains visible after performSync's post-repair health check.
        fullAccountCloudBackupCreator.createCalls.awaitItem()
        fullAccountCloudBackupCreator.createCalls.awaitItem()

        status.appKeyBackupStatus shouldBe
          AppKeyBackupStatus.ProblemWithBackup.PlaceholderSignatureRepairFailed
        fullAccountCloudBackupRepairer.attemptRepairCalls.size shouldBe 1
        cloudBackupDao.get(fullAccount.accountId.serverId).shouldBeOk(staleBackup)
      }

      test("$backupVersion - does not regenerate when backup has a real but different signature") {
        val healthRepository = createHealthRepository()
        // Real but different signature, e.g. mid auth key rotation.
        val rotatedFields = FullAccountFieldsMock.copy(
          appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature("pre-rotation-signature")
        )
        val rotatedBackup = when (cloudBackup) {
          is CloudBackupV2 -> cloudBackup.copy(fullAccountFields = rotatedFields)
          is CloudBackupV3 -> cloudBackup.copy(fullAccountFields = rotatedFields)
          else -> error("Unknown backup version: $cloudBackup")
        }

        cloudStoreAccountRepository.set(cloudAccount)
        cloudBackupDao.set(fullAccount.accountId.serverId, rotatedBackup)
        setCloudBackup(cloudAccount, rotatedBackup)
        emergencyExitKitRepository.setEekData(cloudAccount, eekData)

        val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)

        status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.Healthy>()
        fullAccountCloudBackupCreator.createCalls.expectNoEvents()
        cloudBackupDao.get(fullAccount.accountId.serverId).shouldBeOk(rotatedBackup)
      }

      test("$backupVersion - skips signature check when backup hardware type does not match keybox") {
        val healthRepository = createHealthRepository()
        // Backup belongs to different hardware (mid-upgrade); its CSEK can't be reused.
        val otherHardwareFields = FullAccountFieldsMock.copy(
          hardwareType = HardwareType.W3,
          appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature("other-hardware-signature")
        )
        val otherHardwareBackup = when (cloudBackup) {
          is CloudBackupV2 -> cloudBackup.copy(fullAccountFields = otherHardwareFields)
          is CloudBackupV3 -> cloudBackup.copy(fullAccountFields = otherHardwareFields)
          else -> error("Unknown backup version: $cloudBackup")
        }

        cloudStoreAccountRepository.set(cloudAccount)
        cloudBackupDao.set(fullAccount.accountId.serverId, otherHardwareBackup)
        setCloudBackup(cloudAccount, otherHardwareBackup)
        emergencyExitKitRepository.setEekData(cloudAccount, eekData)

        val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)

        status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.Healthy>()
        fullAccountCloudBackupCreator.createCalls.expectNoEvents()
        cloudBackupDao.get(fullAccount.accountId.serverId).shouldBeOk(otherHardwareBackup)
      }

      test("$backupVersion - skips signature check when keybox has orphaned key recovery sentinel") {
        val healthRepository = createHealthRepository()
        val sentinelKeybox = fullAccount.keybox.copy(
          appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature(
            AppGlobalAuthKeyHwSignature.ORPHANED_KEY_RECOVERY_SENTINEL
          )
        )

        cloudStoreAccountRepository.set(cloudAccount)
        cloudBackupDao.set(fullAccount.accountId.serverId, cloudBackup)
        setCloudBackup(cloudAccount, cloudBackup)
        emergencyExitKitRepository.setEekData(cloudAccount, eekData)

        val status = healthRepository.performSync(fullAccount.accountId, sentinelKeybox)

        status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.Healthy>()
        fullAccountCloudBackupCreator.createCalls.expectNoEvents()
        cloudBackupDao.get(fullAccount.accountId.serverId).shouldBeOk(cloudBackup)
      }

      test("$backupVersion - skips signature check when keybox has W3 onboarding placeholder") {
        val healthRepository = createHealthRepository()
        val placeholderKeybox = fullAccount.keybox.copy(
          appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature(
            AppGlobalAuthKeyHwSignature.W3_ONBOARDING_PLACEHOLDER
          )
        )

        cloudStoreAccountRepository.set(cloudAccount)
        cloudBackupDao.set(fullAccount.accountId.serverId, cloudBackup)
        setCloudBackup(cloudAccount, cloudBackup)
        emergencyExitKitRepository.setEekData(cloudAccount, eekData)

        val status = healthRepository.performSync(fullAccount.accountId, placeholderKeybox)

        status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.Healthy>()
        fullAccountCloudBackupCreator.createCalls.expectNoEvents()
        cloudBackupDao.get(fullAccount.accountId.serverId).shouldBeOk(cloudBackup)
      }

      test("$backupVersion - does not flag backup when signature matches keybox") {
        val healthRepository = createHealthRepository()
        cloudStoreAccountRepository.set(cloudAccount)
        cloudBackupDao.set(fullAccount.accountId.serverId, cloudBackup)
        setCloudBackup(cloudAccount, cloudBackup)
        emergencyExitKitRepository.setEekData(cloudAccount, eekData)

        val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)

        status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.Healthy>()
        fullAccountCloudBackupCreator.createCalls.expectNoEvents()
      }
    }

    test("skips signature check for lite account backups") {
      val healthRepository = createHealthRepository()
      val liteBackup = CloudBackupV3WithLiteAccountMock

      cloudStoreAccountRepository.set(cloudAccount)
      cloudBackupDao.set(fullAccount.accountId.serverId, liteBackup)
      setCloudBackup(cloudAccount, liteBackup)
      emergencyExitKitRepository.setEekData(cloudAccount, eekData)

      val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)

      status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.Healthy>()
      fullAccountCloudBackupCreator.createCalls.expectNoEvents()
    }
  }

  context("force reupload timestamp feature flag") {
    beforeTest {
      cloudStoreAccountRepository.reset()
      cloudBackupService.reset()
      cloudBackupDao.reset()
      emergencyExitKitRepository.reset()
      fullAccountCloudBackupRepairer.reset()
      appFunctionalityService.reset()
      featureFlagDao.reset()
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

      val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)

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

      val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)

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

      val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)

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

      val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)

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

      val status = healthRepository.performSync(fullAccount.accountId, fullAccount.keybox)

      // Should report as stale even though backups match
      status.appKeyBackupStatus.shouldBeInstanceOf<AppKeyBackupStatus.ProblemWithBackup.StaleBackup>()
    }
  }
})
