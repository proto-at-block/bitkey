package build.wallet.cloud.backup

import bitkey.auth.AuthTokenScope.Recovery
import build.wallet.account.AccountServiceFake
import build.wallet.auth.AccountAuthTokensMock
import build.wallet.auth.AuthTokensServiceFake
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.cloud.backup.CloudBackupError.UnrectifiableCloudBackupError
import build.wallet.cloud.backup.local.BackupStorageError
import build.wallet.cloud.backup.local.CloudBackupDaoFake
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.cloud.store.CloudError
import build.wallet.cloud.store.CloudKeyValueStoreFake
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.SharedCloudBackupsFeatureFlag
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.testing.shouldBeErr
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import build.wallet.time.ClockFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CloudBackupRepositoryImplTests : FunSpec({
  val accountId = FullAccountIdMock
  val cloudAccount = CloudAccountMock(instanceId = "jack")
  val cloudKeyValueStore = CloudKeyValueStoreFake()
  val cloudBackupDao = CloudBackupDaoFake()
  val authTokensService = AuthTokensServiceFake()
  val accountService = AccountServiceFake()
  val clock = ClockFake()
  val deviceInfoProvider = DeviceInfoProviderMock()
  val featureFlagDao = FeatureFlagDaoFake()
  val sharedCloudBackupsFeatureFlag = SharedCloudBackupsFeatureFlag(featureFlagDao)

  val cloudBackupRepository = CloudBackupRepositoryImpl(
    cloudKeyValueStore = cloudKeyValueStore,
    cloudBackupDao = cloudBackupDao,
    authTokensService = authTokensService,
    accountService = accountService,
    jsonSerializer = JsonSerializer(),
    clock = clock,
    deviceInfoProvider = deviceInfoProvider,
    sharedCloudBackupsFeatureFlag = sharedCloudBackupsFeatureFlag,
    cloudBackupRepositoryKeys = CloudBackupRepositoryKeysImpl(sharedCloudBackupsFeatureFlag, clock)
  )

  beforeTest {
    accountService.setActiveAccount(FullAccountMock)
  }

  afterTest {
    cloudBackupDao.reset()
    cloudKeyValueStore.reset()
    authTokensService.reset()
    featureFlagDao.reset()
  }

  backupTestData(clock).forEach {
    val backup = it.backup
    val backupJson = it.json

    context(it.testName) {
      test("shared cloud backups is off - write backup to cloud key-value store and dao") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)
        cloudBackupRepository.writeBackup(accountId, cloudAccount, backup, true).shouldBeOk()

        cloudBackupDao.get(accountId.serverId).shouldBeOk(backup)
        cloudKeyValueStore.getString(cloudAccount, key = "cloud-backup").shouldBeOk(backupJson)
        cloudKeyValueStore.getString(cloudAccount, key = "cb-${accountId.serverId}").shouldBeOk(null)
      }

      test("shared cloud backups is off - write backup - dao error") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)
        cloudBackupDao.returnError = true

        cloudBackupRepository.writeBackup(accountId, cloudAccount, backup, true)
          .shouldBeErr(UnrectifiableCloudBackupError(BackupStorageError()))

        cloudKeyValueStore.getString(cloudAccount, key = "cloud-backup").shouldBeOk(backupJson)
        cloudBackupDao.get(accountId.serverId).shouldBeErr(BackupStorageError())
      }

      test("shared cloud backups is off - write backup - cloud key-value error") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)
        cloudKeyValueStore.returnError = true

        cloudBackupRepository.writeBackup(accountId, cloudAccount, backup, true)
          .shouldBeErr(UnrectifiableCloudBackupError(CloudError()))

        cloudKeyValueStore.getString(cloudAccount, key = "cb-${accountId.serverId}")
          .shouldBeErr(CloudError())
        // Backup was not written to local storage because we failed to write it to cloud store
        cloudBackupDao.get(accountId.serverId).shouldBeOk(null)
      }

      test("shared cloud backups is off - write backup - error authenticating") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        val error = Error("foo")
        authTokensService.refreshAccessTokenError = error

        cloudBackupRepository.writeBackup(accountId, cloudAccount, backup, true)
          .shouldBeErr(UnrectifiableCloudBackupError(error))

        // Backup was not written to local storage because we failed to write it to cloud store
        cloudBackupDao.get(accountId.serverId).shouldBeOk(null)
      }

      test("shared cloud backups is off - backup exists in cloud-key value store") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        cloudKeyValueStore.setString(cloudAccount, key = "cloud-backup", value = backupJson)

        cloudBackupRepository.readActiveBackup(cloudAccount).shouldBeOk(backup)
      }

      test("shared cloud backups is off - archiveBackup stores backup under timestamped key") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)
        cloudBackupRepository.writeBackup(accountId, cloudAccount, backup, true).shouldBeOk()

        cloudBackupRepository.archiveBackup(cloudAccount, backup).shouldBeOk()

        val keys = cloudKeyValueStore.keys(cloudAccount).shouldBeOk()
        keys.shouldContain("cloud-backup-${clock.now()}")
        val archivedKeys = keys.filter { it.startsWith("cloud-backup-${clock.now()}") }
        archivedKeys.size shouldBe 1
        cloudKeyValueStore.getString(cloudAccount, archivedKeys.first()).shouldBeOk(backupJson)
      }

      test("shared cloud backups is off - readArchivedBackups() returns only backups that have been archived") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)
        cloudBackupRepository.writeBackup(accountId, cloudAccount, backup, true).shouldBeOk()
        cloudBackupRepository.archiveBackup(cloudAccount, backup).shouldBeOk()

        val backups = cloudBackupRepository.readArchivedBackups(cloudAccount).shouldBeOk()
        backups.size shouldBe 1
        backups.all { it == backup } shouldBe true
      }

      test("shared cloud backups is off - readArchivedBackups() returns empty list when none exist") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        cloudBackupRepository.readArchivedBackups(cloudAccount).shouldBeOk(emptyList())
      }

      test("shared cloud backups is off - readActiveBackup reads legacy backup when both legacy and account-specific exist") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        // Setup: Put backups in both legacy and account-specific format
        cloudKeyValueStore.setString(cloudAccount, key = "cloud-backup", value = backupJson)
        cloudKeyValueStore.setString(cloudAccount, key = "cb-${accountId.serverId}", value = backupJson)

        // Should read the legacy backup
        cloudBackupRepository.readActiveBackup(cloudAccount).shouldBeOk(backup)
      }

      test("shared cloud backups is off - readActiveBackup works with legacy format only") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        // Setup: Only legacy backup exists
        cloudKeyValueStore.setString(cloudAccount, key = "cloud-backup", value = backupJson)

        // Should successfully read the legacy backup
        cloudBackupRepository.readActiveBackup(cloudAccount)
          .shouldBeOk()
          .shouldNotBeNull()
          .accountId
          .shouldBe(backup.accountId)
      }

      test("shared cloud backups is off - readActiveBackup returns null for account-specific format only") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        // Setup: Only account-specific format backup exists
        cloudKeyValueStore.setString(cloudAccount, key = "cb-${accountId.serverId}", value = backupJson)

        // Should return null as it only reads from legacy key
        cloudBackupRepository.readActiveBackup(cloudAccount).shouldBeOk(null)
      }

      test("shared cloud backups is off - readActiveBackup returns backup by ignoring the wrong account's backup in shared cloud") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        val otherAccountId = FullAccountId("other-account")
        accountService.setActiveAccount(FullAccountMock.copy(accountId = otherAccountId))

        // Setup: Only other account's backup exists
        cloudKeyValueStore.setString(cloudAccount, key = "cloud-backup", value = backupJson)

        // Should return backup by ignoring account ID when flag is off
        cloudBackupRepository.readActiveBackup(cloudAccount).shouldBeOk(backup)
      }

      test("shared cloud backups is off - readAllBackups returns legacy backups in shared cloud account") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        val otherAccountId = FullAccountId("other-account")

        // Setup: Multiple account-specific backups (simulating shared cloud account)
        cloudKeyValueStore.setString(cloudAccount, key = "cb-${accountId.serverId}", value = backupJson)
        cloudKeyValueStore.setString(cloudAccount, key = "cb-${otherAccountId.serverId}", value = backupJson)
        cloudKeyValueStore.setString(cloudAccount, key = "cloud-backup", value = backupJson) // legacy

        // Should return all 3 backups
        val allBackups = cloudBackupRepository.readAllBackups(cloudAccount).shouldBeOk()
        allBackups.size shouldBe 1
      }

      test("shared cloud backups is off - readArchivedBackups finds both legacy and account-specific format archived backups") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)

        // Manually add a legacy format archived backup
        cloudKeyValueStore.setString(cloudAccount, key = "cloud-backup-${clock.now()}", value = backupJson)

        // Manually add an account-specific format archived backup
        cloudKeyValueStore.setString(cloudAccount, key = "cb-${accountId.serverId}-${clock.now()}", value = backupJson)

        val backups = cloudBackupRepository.readArchivedBackups(cloudAccount).shouldBeOk()
        backups.size shouldBe 2 // Should find both account-specific and legacy archived backups
        backups.all { it == backup } shouldBe true
      }

      test("shared cloud backups is off - migrateBackupToAccountIdKey re-archives legacy archived backup") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        val oldKey = "cloud-backup-${clock.now()}"
        // Setup: Put a legacy backup in cloud storage
        cloudKeyValueStore.setString(cloudAccount, key = oldKey, value = backupJson)

        // Run migration
        cloudBackupRepository.migrateBackupToAccountIdKey(cloudAccount).shouldBeOk()

        // The old key should be removed
        cloudKeyValueStore.getString(cloudAccount, key = oldKey).shouldBeOk(null)

        // A new archived key should be created
        val allKeys = cloudKeyValueStore.keys(cloudAccount).shouldBeOk()
        val archivedKeys = allKeys.filter { it.startsWith("cloud-backup-") }
        archivedKeys.size shouldBe 1
      }

      test("shared cloud backups is off - migrateBackupToAccountIdKey does nothing when no legacy backup exists") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        // Setup: No legacy backup
        cloudKeyValueStore.getString(cloudAccount, key = "cloud-backup").shouldBeOk(null)

        // Run migration
        cloudBackupRepository.migrateBackupToAccountIdKey(cloudAccount).shouldBeOk()

        // Verify nothing changed
        cloudKeyValueStore.getString(cloudAccount, key = "cb-${accountId.serverId}").shouldBeOk(null)
        val allKeys = cloudKeyValueStore.keys(cloudAccount).shouldBeOk()
        allKeys.isEmpty() shouldBe true
      }

      test("shared cloud backups is off - migrateBackupToAccountIdKey removes legacy backup") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        // Setup: Both legacy and account-specific backups exist
        cloudKeyValueStore.setString(cloudAccount, key = "cloud-backup", value = backupJson)
        cloudKeyValueStore.setString(cloudAccount, key = "cb-${accountId.serverId}", value = backupJson)

        // Run migration
        cloudBackupRepository.migrateBackupToAccountIdKey(cloudAccount).shouldBeOk()

        // Verify legacy backup is removed and account-specific one remains
        cloudKeyValueStore.getString(cloudAccount, key = "cloud-backup").shouldBeOk(null)
        cloudKeyValueStore.getString(cloudAccount, key = "cb-${accountId.serverId}").shouldBeOk().shouldNotBeNull()
      }

      test("shared cloud backups is off - writes only to legacy key") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        // Disable feature flag
        sharedCloudBackupsFeatureFlag.setFlagValue(
          FeatureFlagValue.BooleanFlag(false)
        )

        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)
        cloudBackupRepository.writeBackup(accountId, cloudAccount, backup, true).shouldBeOk()

        // Should only write to legacy key when feature flag is disabled
        cloudKeyValueStore.getString(cloudAccount, key = "cloud-backup").shouldBeOk(backupJson)
        cloudKeyValueStore.getString(cloudAccount, key = "cb-${accountId.serverId}").shouldBeOk(null)
      }

      test("shared cloud backups is off - reads only from legacy key") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        // Disable feature flag
        sharedCloudBackupsFeatureFlag.setFlagValue(
          FeatureFlagValue.BooleanFlag(false)
        )

        // Put backup in legacy key
        cloudKeyValueStore.setString(cloudAccount, key = "cloud-backup", value = backupJson)

        // Should read from legacy key
        cloudBackupRepository.readActiveBackup(cloudAccount).shouldBeOk(backup)
      }

      test("shared cloud backups is off - migration re-archives") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))

        val oldKey = "cloud-backup-${clock.now()}"
        // Setup: Put a legacy backup in cloud storage
        cloudKeyValueStore.setString(cloudAccount, key = oldKey, value = backupJson)

        // Run migration
        cloudBackupRepository.migrateBackupToAccountIdKey(cloudAccount).shouldBeOk()

        // Verify legacy is re-archived
        cloudKeyValueStore.getString(cloudAccount, key = oldKey)
          .shouldBeOk().shouldBeNull()
        val newKeys = cloudKeyValueStore.keys(cloudAccount).shouldBeOk()
        newKeys.filter { it.startsWith("cloud-backup-") }.size shouldBe 1
      }

      test("shared cloud backups is on - write backup to cloud key-value store and dao") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)
        cloudBackupRepository.writeBackup(accountId, cloudAccount, backup, true).shouldBeOk()

        cloudBackupDao.get(accountId.serverId).shouldBeOk(backup)

        cloudKeyValueStore.getString(cloudAccount, key = "cloud-backup").shouldBeOk(null)

        cloudKeyValueStore.getString(cloudAccount, key = "cb-${accountId.serverId}")
          .shouldBeOk().shouldNotBeNull()
      }

      test("shared cloud backups is on - write backup - dao error") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)
        cloudBackupDao.returnError = true

        cloudBackupRepository.writeBackup(accountId, cloudAccount, backup, true)
          .shouldBeErr(UnrectifiableCloudBackupError(BackupStorageError()))

        cloudKeyValueStore.getString(cloudAccount, key = "cloud-backup").shouldBeOk(null)
        cloudKeyValueStore.getString(cloudAccount, key = "cb-${accountId.serverId}")
          .shouldBeOk().shouldNotBeNull()
        cloudBackupDao.get(accountId.serverId).shouldBeErr(BackupStorageError())
      }

      test("shared cloud backups is on - write backup - cloud key-value error") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)
        cloudKeyValueStore.returnError = true

        cloudBackupRepository.writeBackup(accountId, cloudAccount, backup, true)
          .shouldBeErr(UnrectifiableCloudBackupError(CloudError()))

        cloudKeyValueStore.getString(cloudAccount, key = "cb-${accountId.serverId}")
          .shouldBeErr(CloudError())
        // Backup was not written to local storage because we failed to write it to cloud store
        cloudBackupDao.get(accountId.serverId).shouldBeOk(null)
      }

      test("shared cloud backups is on - write backup - error authenticating") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        val error = Error("foo")
        authTokensService.refreshAccessTokenError = error

        cloudBackupRepository.writeBackup(accountId, cloudAccount, backup, true)
          .shouldBeErr(UnrectifiableCloudBackupError(error))

        // Backup was not written to local storage because we failed to write it to cloud store
        cloudBackupDao.get(accountId.serverId).shouldBeOk(null)
      }

      test("shared cloud backups is on - backup exists in cloud-key value store") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        deviceInfoProvider.deviceNicknameValue = "Test Device"
        cloudKeyValueStore.setString(cloudAccount, key = "cloud-backup", value = backupJson)

        cloudBackupRepository.readActiveBackup(cloudAccount).shouldBeOk()
          .shouldNotBeNull()
          .accountId
          .shouldBe(backup.accountId)
      }

      test("shared cloud backups is on - archiveBackup stores backup under timestamped key") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)
        cloudBackupRepository.archiveBackup(cloudAccount, backup).shouldBeOk()

        val keys = cloudKeyValueStore.keys(cloudAccount).shouldBeOk()
        keys.shouldContain("cb-${accountId.serverId}-${clock.now()}")
        keys.size shouldBe 1
        cloudKeyValueStore.getString(cloudAccount, keys.first()).shouldBeOk().shouldNotBeNull()
      }

      test("shared cloud backups is on - readArchivedBackups() returns only backups that have been archived") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)
        cloudBackupRepository.writeBackup(accountId, cloudAccount, backup, true).shouldBeOk()
        cloudBackupRepository.archiveBackup(cloudAccount, backup).shouldBeOk()

        val backups = cloudBackupRepository.readArchivedBackups(cloudAccount).shouldBeOk()
        backups.size shouldBe 1
        backups.all { it.accountId == backup.accountId } shouldBe true
      }

      test("shared cloud backups is on - readArchivedBackups() returns empty list when none exist") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        cloudBackupRepository.readArchivedBackups(cloudAccount).shouldBeOk(emptyList())
      }

      test("shared cloud backups is on - readActiveBackup reads account-specific backup when both legacy and account-specific exist") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        deviceInfoProvider.deviceNicknameValue = "Test Device"
        // Setup: Put backups in both legacy and account-specific format
        cloudKeyValueStore.setString(cloudAccount, key = "cloud-backup", value = backupJson)
        cloudKeyValueStore.setString(cloudAccount, key = "cb-${accountId.serverId}", value = backupJson)

        // Should read the account-specific backup
        cloudBackupRepository.readActiveBackup(cloudAccount)
          .shouldBeOk()
          .shouldNotBeNull()
          .accountId
          .shouldBe(backup.accountId)
      }

      test("shared cloud backups is on - readActiveBackup works with legacy format only") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        deviceInfoProvider.deviceNicknameValue = "Test Device"
        // Setup: Only legacy backup exists
        cloudKeyValueStore.setString(cloudAccount, key = "cloud-backup", value = backupJson)

        // Should successfully read the legacy backup
        cloudBackupRepository.readActiveBackup(cloudAccount)
          .shouldBeOk()
          .shouldNotBeNull()
          .accountId
          .shouldBe(backup.accountId)
      }

      test("shared cloud backups is on - readActiveBackup works with account-specific format only") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        // Setup: Only account-specific format backup exists
        cloudKeyValueStore.setString(cloudAccount, key = "cb-${accountId.serverId}", value = backupJson)

        // Should successfully read the account-specific format backup
        cloudBackupRepository.readActiveBackup(cloudAccount).shouldBeOk(backup)
      }

      test(
        "shared cloud backups is on - readActiveBackup returns AccountIdMismatched error when reading wrong account's backup"
      ) {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        val otherAccountId = FullAccountId("other-account")
        accountService.setActiveAccount(FullAccountMock.copy(accountId = otherAccountId))

        // Setup: A backup for a different account ID exists at the key for the *active* account.
        // This simulates a weird state, but it's what the code is guarding against.
        // The key is for `otherAccountId`, but the content is for `accountId` from the test data.
        cloudKeyValueStore.setString(
          cloudAccount,
          key = "cb-${otherAccountId.serverId}",
          value = backupJson
        )

        // Should return AccountIdMismatched error because the backup's account ID doesn't match the active account.
        cloudBackupRepository.readActiveBackup(cloudAccount)
          .shouldBeErrOfType<CloudBackupError.AccountIdMismatched>()
      }

      test("shared cloud backups is on - readAllBackups returns all backups in shared cloud account") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        deviceInfoProvider.deviceNicknameValue = "Test Device"
        val otherAccountId = FullAccountId("other-account")

        // Setup: Multiple account-specific backups (simulating shared cloud account)
        cloudKeyValueStore.setString(cloudAccount, key = "cb-${accountId.serverId}", value = backupJson)
        cloudKeyValueStore.setString(cloudAccount, key = "cb-${otherAccountId.serverId}", value = backupJson)
        cloudKeyValueStore.setString(cloudAccount, key = "cloud-backup", value = backupJson) // legacy

        // Should return all 2 backups
        val allBackups = cloudBackupRepository.readAllBackups(cloudAccount).shouldBeOk()
        allBackups.size shouldBe 2
      }

      test("shared cloud backups is on - readArchivedBackups finds both legacy and account-specific format archived backups") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)

        // Create and archive backup using account-specific format
        cloudBackupRepository.writeBackup(accountId, cloudAccount, backup, true).shouldBeOk()
        cloudBackupRepository.archiveBackup(cloudAccount, backup).shouldBeOk()

        // Manually add a legacy format archived backup
        cloudKeyValueStore.setString(cloudAccount, key = "cloud-backup-${clock.now()}", value = backupJson)

        val backups = cloudBackupRepository.readArchivedBackups(cloudAccount).shouldBeOk()
        backups.size shouldBe 2 // Should find both account-specific and legacy, but not the active backup
        backups.all { it.accountId == backup.accountId } shouldBe true
      }

      test("shared cloud backups is on - migrateBackupToAccountIdKey migrates legacy backup to account-specific format") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        val v2Key = "cloud-backup"
        val v3Key = "cb-${accountId.serverId}"
        // Setup: Put a legacy backup in cloud storage
        cloudKeyValueStore.setString(cloudAccount, key = v2Key, value = backupJson)

        // Verify no account-specific backup exists yet
        cloudKeyValueStore.getString(cloudAccount, key = v3Key).shouldBeOk(null)

        // Run migration
        cloudBackupRepository.migrateBackupToAccountIdKey(cloudAccount).shouldBeOk()

        // Verify migration results
        // 1. New account-specific backup should exist (keyed by account ID only)
        cloudKeyValueStore.getString(cloudAccount, key = v3Key).shouldBeOk().shouldNotBeNull()

        // 2. legacy backup should be removed if migration is successful.
        cloudKeyValueStore.getString(cloudAccount, key = v2Key).shouldBeOk(null)

        // 3. account-specific backup should be archived (look for archived keys)
        val allKeys = cloudKeyValueStore.keys(cloudAccount).shouldBeOk()
        val archivedKeys = allKeys.filter { it.startsWith("cb-") }
        archivedKeys.size shouldBe 1
      }

      test("shared cloud backups is on - migrateBackupToAccountIdKey does nothing when no legacy backup exists") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        // Setup: No legacy backup
        cloudKeyValueStore.getString(cloudAccount, key = "cloud-backup").shouldBeOk(null)

        // Run migration
        cloudBackupRepository.migrateBackupToAccountIdKey(cloudAccount).shouldBeOk()

        // Verify nothing changed
        cloudKeyValueStore.getString(cloudAccount, key = "cb-${accountId.serverId}").shouldBeOk(null)
        val allKeys = cloudKeyValueStore.keys(cloudAccount).shouldBeOk()
        allKeys.isEmpty() shouldBe true
      }

      test("shared cloud backups is on - migrateBackupToAccountIdKey should remove legacy when account-specific backup already exists") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        // Setup: Both legacy and account-specific backups exist
        cloudKeyValueStore.setString(cloudAccount, key = "cloud-backup", value = backupJson)
        cloudKeyValueStore.setString(cloudAccount, key = "cb-${accountId.serverId}", value = backupJson)

        // Run migration
        cloudBackupRepository.migrateBackupToAccountIdKey(cloudAccount).shouldBeOk()

        // Verify both backups still exist (no migration needed)
        cloudKeyValueStore.getString(cloudAccount, key = "cloud-backup").shouldBeOk()
          .shouldBeNull()
        cloudKeyValueStore.getString(cloudAccount, key = "cb-${accountId.serverId}").shouldBeOk()
          .shouldNotBeNull()
      }

      test("shared cloud backups is off - writes only to legacy key") {
        // Disable feature flag
        sharedCloudBackupsFeatureFlag.setFlagValue(
          FeatureFlagValue.BooleanFlag(false)
        )

        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)
        cloudBackupRepository.writeBackup(accountId, cloudAccount, backup, true).shouldBeOk()

        // Should only write to legacy key when feature flag is disabled
        cloudKeyValueStore.getString(cloudAccount, key = "cloud-backup").shouldBeOk(backupJson)
        cloudKeyValueStore.getString(cloudAccount, key = "cb-${accountId.serverId}").shouldBeOk(null)
      }

      test("shared cloud backups is on - reads only from legacy key") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        // Disable feature flag
        sharedCloudBackupsFeatureFlag.setFlagValue(
          FeatureFlagValue.BooleanFlag(false)
        )

        // Put backup in legacy key
        cloudKeyValueStore.setString(cloudAccount, key = "cloud-backup", value = backupJson)

        // Should read from legacy key
        cloudBackupRepository.readActiveBackup(cloudAccount)
          .shouldBeOk().shouldNotBeNull()
      }

      test("shared cloud backups is on - migration should remove legacy") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

        // Setup: Put a legacy backup in cloud storage
        cloudKeyValueStore.setString(cloudAccount, key = "cloud-backup-${clock.now()}", value = backupJson)

        // Run migration
        cloudBackupRepository.migrateBackupToAccountIdKey(cloudAccount).shouldBeOk()

        // Verify nothing changed - no account-specific backup created
        cloudKeyValueStore.keys(cloudAccount).value.map {
          println(it)
        }
        cloudKeyValueStore.getString(cloudAccount, key = "cb-${accountId.serverId}-${clock.now()}")
          .shouldBeOk().shouldNotBeNull()
        cloudKeyValueStore.getString(cloudAccount, key = "cloud-backup-${clock.now()}")
          .shouldBeOk().shouldBeNull()
      }
    }
  }
})

private data class BackupTestData(
  val testName: String,
  val backup: CloudBackup,
  /** JSON representation of [backup] instance. */
  val json: String,
)

private fun backupTestData(clock: Clock) =
  AllFullAccountBackupMocks.map { backup ->
    val version = when (backup) {
      is CloudBackupV2 -> "v2"
      is CloudBackupV3 -> "v3"
      else -> "unknown"
    }
    var updatedBackup = backup
    if (backup is CloudBackupV3) {
      updatedBackup = backup.copy(createdAt = clock.now())
    }
    val json = when (updatedBackup) {
      is CloudBackupV2 -> Json.encodeToString(updatedBackup)
      is CloudBackupV3 -> Json.encodeToString(updatedBackup)
      else -> throw IllegalStateException("Unknown backup version")
    }
    BackupTestData(
      testName = "backup $version",
      backup = updatedBackup as CloudBackup,
      json = json
    )
  }
