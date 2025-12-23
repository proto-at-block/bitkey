package build.wallet.cloud.backup.health

import build.wallet.availability.AppFunctionalityService
import build.wallet.availability.FunctionalityFeatureStates.FeatureState.Unavailable
import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.backup.*
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupHealthRepository
import build.wallet.cloud.backup.CloudBackupRepository
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.CloudBackupV3
import build.wallet.cloud.backup.JsonSerializer
import build.wallet.cloud.backup.local.CloudBackupDao
import build.wallet.cloud.backup.v2.FullAccountFields
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.emergencyexitkit.EmergencyExitKitRepository
import build.wallet.feature.flags.CloudBackupHealthLoggingFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.logging.logFailure
import build.wallet.logging.logWarn
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.toErrorIfNull
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

@BitkeyInject(AppScope::class)
class CloudBackupHealthRepositoryImpl(
  private val cloudStoreAccountRepository: CloudStoreAccountRepository,
  private val cloudBackupRepository: CloudBackupRepository,
  private val cloudBackupDao: CloudBackupDao,
  private val emergencyExitKitRepository: EmergencyExitKitRepository,
  private val fullAccountCloudBackupRepairer: FullAccountCloudBackupRepairer,
  private val appFunctionalityService: AppFunctionalityService,
  private val jsonSerializer: JsonSerializer,
  private val cloudBackupHealthLoggingFeatureFlag: CloudBackupHealthLoggingFeatureFlag,
  private val fullAccountCloudBackupCreator: FullAccountCloudBackupCreator,
) : CloudBackupHealthRepository {
  companion object {
    // Cloud storage (e.g., iCloud NSUbiquitousKeyValueStore) typically has a 1MB limit
    private const val CLOUD_BACKUP_SIZE_WARNING_BYTES = 900_000
    private const val CLOUD_BACKUP_SIZE_LIMIT_BYTES = 1_000_000
  }

  private val appKeyBackupStatus = MutableStateFlow<AppKeyBackupStatus?>(null)

  override fun appKeyBackupStatus(): StateFlow<AppKeyBackupStatus?> {
    return appKeyBackupStatus
  }

  private val eekBackupStatus = MutableStateFlow<EekBackupStatus?>(null)

  override fun eekBackupStatus(): StateFlow<EekBackupStatus?> {
    return eekBackupStatus
  }

  /**
   * A lock to ensure that only one sync is performed at a time.
   */
  private val syncLock = Mutex()

  /**
   * Cache for backup size calculation to avoid re-serializing on every health check.
   * Maps backup hashCode to calculated size.
   */
  private val backupSizeCache = mutableMapOf<Int, Int>()

  override suspend fun performSync(account: FullAccount): CloudBackupStatus {
    return syncLock.withLock {
      getCurrentCloudAccount()
        .fold(
          success = { cloudAccount ->
            val cloudBackupStatus = syncBackupStatus(cloudAccount, account)

            if (cloudBackupStatus.isHealthy()) {
              cloudBackupStatus
            } else {
              // Attempt to repair the backup silently first
              fullAccountCloudBackupRepairer.attemptRepair(account, cloudAccount, cloudBackupStatus)
              // Re-sync and return whatever the status is after repair attempt.
              syncBackupStatus(cloudAccount, account)
            }
          },
          failure = {
            // If we can't get the cloud account, we can't sync the backup status.
            CloudBackupStatus(
              appKeyBackupStatus = AppKeyBackupStatus.ProblemWithBackup.NoCloudAccess,
              eekBackupStatus = EekBackupStatus.ProblemWithBackup.NoCloudAccess
            )
          }
        )
        .also {
          appKeyBackupStatus.value = it.appKeyBackupStatus
          eekBackupStatus.value = it.eekBackupStatus
        }
    }
  }

  private suspend fun getCurrentCloudAccount(): Result<CloudStoreAccount, Error> {
    return cloudStoreAccountRepository.currentAccount(cloudServiceProvider())
      .toErrorIfNull { CloudStoreAccountMissingError() }
  }

  private class CloudStoreAccountMissingError : Error()

  private suspend fun syncBackupStatus(
    cloudAccount: CloudStoreAccount,
    account: FullAccount,
  ) = CloudBackupStatus(
    appKeyBackupStatus = syncAppKeyBackupStatus(cloudAccount, account),
    eekBackupStatus = syncEekBackupStatus(cloudAccount)
  )

  private suspend fun syncAppKeyBackupStatus(
    cloudAccount: CloudStoreAccount,
    account: FullAccount,
  ): AppKeyBackupStatus {
    if (appFunctionalityService.status.value.featureStates.cloudBackupHealth == Unavailable) {
      return AppKeyBackupStatus.ProblemWithBackup.ConnectivityUnavailable
    }

    val localCloudBackup = cloudBackupDao
      .get(account.accountId.serverId)
      .toErrorIfNull { Error("No local backup found") }
      .logFailure { "Error finding local backup" }
      .get()
      // We are missing a local backup, so we can't validate the integrity of the cloud backup.
      // Mark backup as missing to let the customer
      ?: return AppKeyBackupStatus.ProblemWithBackup.BackupMissing

    val localBackupSize = getBackupSizeBytes(localCloudBackup)

    if (localBackupSize >= CLOUD_BACKUP_SIZE_WARNING_BYTES && cloudBackupHealthLoggingFeatureFlag.isEnabled()) {
      logWarn { "Backup approaching 1MB: ${localBackupSize}b. ${getFieldSizeSummary(localCloudBackup)}" }
      if (localBackupSize >= CLOUD_BACKUP_SIZE_LIMIT_BYTES && localCloudBackup.isFullAccount()) {
        // We've exceeded the limit due to INC-7289; overwrite the local cache with the correct backup
        // and report a mismatch
        val sealedCsek = when (localCloudBackup) {
          is CloudBackupV2 -> localCloudBackup.fullAccountFields!!.sealedHwEncryptionKey
          is CloudBackupV3 -> localCloudBackup.fullAccountFields!!.sealedHwEncryptionKey
        }
        val fixedBackup = fullAccountCloudBackupCreator.create(
          keybox = account.keybox,
          sealedCsek = sealedCsek
        ).logFailure { "Failed to create fixed cloud backup" }
          .get()
        if (fixedBackup != null) {
          cloudBackupDao.set(account.accountId.serverId, fixedBackup)
          return AppKeyBackupStatus.ProblemWithBackup.StaleBackup
        }
      }
    }

    return cloudBackupRepository
      .readActiveBackup(cloudAccount)
      .fold(
        success = { cloudBackup ->
          when (cloudBackup) {
            null -> AppKeyBackupStatus.ProblemWithBackup.BackupMissing
            else -> {
              if (cloudBackup != localCloudBackup) {
                if (cloudBackupHealthLoggingFeatureFlag.isEnabled()) {
                  val cloudBackupSize = getBackupSizeBytes(cloudBackup)
                  logWarn {
                    "Backup mismatch. Local: ${localBackupSize}b (${localCloudBackup.hashCode()}), Cloud: ${cloudBackupSize}b (${cloudBackup.hashCode()}). ${
                      getDiffSummary(
                        localCloudBackup,
                        cloudBackup
                      )
                    }"
                  }
                }
                AppKeyBackupStatus.ProblemWithBackup.InvalidBackup(cloudBackup)
              } else {
                // TODO(BKR-1155): do we need to perform additional integrity checks?
                AppKeyBackupStatus.Healthy(
                  // TODO(BKR-1154): use actual timestamp from backup
                  lastUploaded = Clock.System.now()
                )
              }
            }
          }
        },
        failure = {
          // TODO(BKR-1156): handle unknown loading errors
          logWarn { "Failed to read cloud backup during sync: $it" }
          AppKeyBackupStatus.ProblemWithBackup.NoCloudAccess
        }
      )
  }

  private suspend fun syncEekBackupStatus(cloudAccount: CloudStoreAccount): EekBackupStatus {
    return emergencyExitKitRepository
      .read(cloudAccount)
      .fold(
        success = {
          EekBackupStatus.Healthy(
            // TODO(BKR-1154): use actual timestamp from backup
            lastUploaded = Clock.System.now()
          )
        },
        failure = {
          // TODO(BKR-1153): handle unknown loading errors
          EekBackupStatus.ProblemWithBackup.BackupMissing
        }
      )
  }

  private fun getBackupSizeBytes(backup: CloudBackup): Int {
    val hash = backup.hashCode()
    backupSizeCache[hash]?.let { return it }

    val jsonResult = when (backup) {
      is CloudBackupV3 -> jsonSerializer.encodeToStringResult(backup)
      is CloudBackupV2 -> jsonSerializer.encodeToStringResult(backup)
    }

    return jsonResult
      .map { jsonString ->
        val size = jsonString.encodeToByteArray().size
        backupSizeCache[hash] = size
        size
      }
      .getOrElse {
        logWarn(throwable = it) { "Failed to calculate backup size: $it" }
        -1
      }
  }

  private fun getFieldSizeSummary(backup: CloudBackup): String {
    val fields = when (backup) {
      is CloudBackupV3 -> backup.fullAccountFields
      is CloudBackupV2 -> backup.fullAccountFields
    }
    val dekSize = fields?.socRecSealedDekMap?.let {
      jsonSerializer.encodeToStringResult(it).map { json ->
        json.encodeToByteArray().size
      }.getOrElse { 0 }
    } ?: 0
    val hwSize = fields?.hwFullAccountKeysCiphertext?.let {
      jsonSerializer.encodeToStringResult(it).map { json ->
        json.encodeToByteArray().size
      }.getOrElse { 0 }
    } ?: 0
    val socRecSize = fields?.socRecSealedFullAccountKeys?.let {
      jsonSerializer.encodeToStringResult(it).map { json ->
        json.encodeToByteArray().size
      }.getOrElse { 0 }
    } ?: 0
    return "dek=${dekSize}b, hw=${hwSize}b, socRec=${socRecSize}b"
  }

  @Suppress("CyclomaticComplexMethod")
  private fun getDiffSummary(
    local: CloudBackup,
    cloud: CloudBackup,
  ): String {
    val diffs = mutableListOf<String>()
    if (local::class != cloud::class) return "version"

    when {
      local is CloudBackupV3 && cloud is CloudBackupV3 -> {
        if (local.accountId != cloud.accountId) diffs += "accountId"
        if (local.f8eEnvironment != cloud.f8eEnvironment) diffs += "f8eEnv"
        if (local.delegatedDecryptionKeypair != cloud.delegatedDecryptionKeypair) diffs += "ddkp"
        if (local.appRecoveryAuthKeypair != cloud.appRecoveryAuthKeypair) diffs += "recoveryAuth"
        if (local.deviceNickname != cloud.deviceNickname) diffs += "deviceNickname"
        if (local.createdAt != cloud.createdAt) diffs += "createdAt"
        diffs += getFullAccountFieldsDiff(local.fullAccountFields, cloud.fullAccountFields)
      }
      local is CloudBackupV2 && cloud is CloudBackupV2 -> {
        if (local.accountId != cloud.accountId) diffs += "accountId"
        if (local.f8eEnvironment != cloud.f8eEnvironment) diffs += "f8eEnv"
        if (local.delegatedDecryptionKeypair != cloud.delegatedDecryptionKeypair) diffs += "ddkp"
        if (local.appRecoveryAuthKeypair != cloud.appRecoveryAuthKeypair) diffs += "recoveryAuth"
        diffs += getFullAccountFieldsDiff(local.fullAccountFields, cloud.fullAccountFields)
      }
    }
    return if (diffs.isEmpty()) "unknown" else diffs.joinToString(",")
  }

  private fun getFullAccountFieldsDiff(
    local: FullAccountFields?,
    cloud: FullAccountFields?,
  ): List<String> {
    if (local == null && cloud == null) return emptyList()
    if (local == null || cloud == null) return listOf("fullAcct")
    val diffs = mutableListOf<String>()
    if (local.sealedHwEncryptionKey != cloud.sealedHwEncryptionKey) diffs += "hwEncKey"
    if (local.socRecSealedDekMap != cloud.socRecSealedDekMap) diffs += "dekMap"
    if (local.hwFullAccountKeysCiphertext != cloud.hwFullAccountKeysCiphertext) diffs += "hwKeys"
    if (local.socRecSealedFullAccountKeys != cloud.socRecSealedFullAccountKeys) diffs += "socRecKeys"
    if (local.rotationAppRecoveryAuthKeypair != cloud.rotationAppRecoveryAuthKeypair) diffs += "rotationAuth"
    if (local.appGlobalAuthKeyHwSignature != cloud.appGlobalAuthKeyHwSignature) diffs += "hwSig"
    return diffs
  }
}
