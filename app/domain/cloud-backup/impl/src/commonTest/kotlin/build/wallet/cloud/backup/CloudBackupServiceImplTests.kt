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
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.SharedCloudBackupsFeatureFlag
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.testing.shouldBeErr
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8

class CloudBackupServiceImplTests : FunSpec({
  val accountId = FullAccountIdMock
  val cloudAccount = CloudAccountMock(instanceId = "jack")
  val cloudBackupStore = CloudBackupStoreFake()
  val cloudBackupDao = CloudBackupDaoFake()
  val authTokensService = AuthTokensServiceFake()
  val accountService = AccountServiceFake()
  val clock = ClockFake()
  val deviceInfoProvider = DeviceInfoProviderMock()
  val featureFlagDao = FeatureFlagDaoFake()
  val sharedCloudBackupsFeatureFlag = SharedCloudBackupsFeatureFlag(featureFlagDao)

  val cloudBackupService = CloudBackupServiceImpl(
    cloudBackupStore = cloudBackupStore,
    cloudBackupDao = cloudBackupDao,
    authTokensService = authTokensService,
    accountService = accountService,
    jsonSerializer = JsonSerializer(),
    clock = clock,
    deviceInfoProvider = deviceInfoProvider,
    sharedCloudBackupsFeatureFlag = sharedCloudBackupsFeatureFlag,
    cloudBackupStoreKeys = CloudBackupStoreKeysImpl(sharedCloudBackupsFeatureFlag, clock)
  )

  beforeTest {
    accountService.setActiveAccount(FullAccountMock)
  }

  afterTest {
    cloudBackupDao.reset()
    cloudBackupStore.reset()
    authTokensService.reset()
    featureFlagDao.reset()
  }

  test("clearAll removes only cloud backup keys") {
    sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    val backup = backupTestData(clock).first()

    cloudBackupStore.setString(cloudAccount, key = "cb-${accountId.serverId}", value = backup.json).shouldBeOk()
    cloudBackupStore.setString(
      cloudAccount,
      key = "cb-${accountId.serverId}-${clock.now()}",
      value = backup.json
    ).shouldBeOk()
    cloudBackupStore.setString(cloudAccount, key = "cloud-backup", value = backup.json).shouldBeOk()
    cloudBackupStore.setString(cloudAccount, key = "not-a-backup-key", value = "keep-me").shouldBeOk()

    cloudBackupService.clearAll(cloudAccount, clearRemoteOnly = true).shouldBeOk()

    cloudBackupStore.getString(cloudAccount, key = "cb-${accountId.serverId}").shouldBeOk(null)
    cloudBackupStore.getString(cloudAccount, key = "cb-${accountId.serverId}-${clock.now()}").shouldBeOk(null)
    cloudBackupStore.getString(cloudAccount, key = "cloud-backup").shouldBeOk(null)
    cloudBackupStore.getString(cloudAccount, key = "not-a-backup-key").shouldBeOk("keep-me")
  }

  test("legacy active migration does not overwrite fresher account-specific V3 backup with V2 backup") {
    sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    val accountSpecificBackup = CloudBackupV3WithFullAccountMock.copy(
      accountId = accountId.serverId,
      createdAt = Instant.parse("2024-02-01T00:00:00Z"),
      deviceNickname = "account-specific-v3"
    )
    val legacyBackup = CloudBackupV2WithFullAccountMock.copy(accountId = accountId.serverId)

    cloudBackupStore.setString(
      cloudAccount,
      key = "cb-${accountId.serverId}",
      value = accountSpecificBackup.toBackupJson()
    ).shouldBeOk()
    cloudBackupStore.setString(
      cloudAccount,
      key = "cloud-backup",
      value = legacyBackup.toBackupJson()
    ).shouldBeOk()

    cloudBackupService.migrateBackupToAccountIdKey(cloudAccount).shouldBeOk()

    cloudBackupService.readBackup(accountId, cloudAccount)
      .shouldBeOk(accountSpecificBackup)
    cloudBackupStore.getString(cloudAccount, key = "cloud-backup").shouldBeOk(null)
  }

  test("legacy active migration overwrites older account-specific V3 backup with fresher legacy V3 backup") {
    sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    val accountSpecificBackup = CloudBackupV3WithFullAccountMock.copy(
      accountId = accountId.serverId,
      createdAt = Instant.parse("2024-01-01T00:00:00Z"),
      deviceNickname = "account-specific-old"
    )
    val legacyBackup = CloudBackupV3WithFullAccountMock.copy(
      accountId = accountId.serverId,
      createdAt = Instant.parse("2024-02-01T00:00:00Z"),
      deviceNickname = "legacy-new"
    )

    cloudBackupStore.setString(
      cloudAccount,
      key = "cb-${accountId.serverId}",
      value = accountSpecificBackup.toBackupJson()
    ).shouldBeOk()
    cloudBackupStore.setString(
      cloudAccount,
      key = "cloud-backup",
      value = legacyBackup.toBackupJson()
    ).shouldBeOk()

    cloudBackupService.migrateBackupToAccountIdKey(cloudAccount).shouldBeOk()

    cloudBackupService.readBackup(accountId, cloudAccount)
      .shouldBeOk(legacyBackup)
    cloudBackupStore.getString(cloudAccount, key = "cloud-backup").shouldBeOk(null)
  }

  test("legacy active migration preserves legacy active backup when same-account backups have equal freshness") {
    sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    val createdAt = Instant.parse("2024-02-01T00:00:00Z")
    val accountSpecificBackup = CloudBackupV3WithFullAccountMock.copy(
      accountId = accountId.serverId,
      createdAt = createdAt,
      deviceNickname = "account-specific"
    )
    val legacyBackup = CloudBackupV3WithFullAccountMock.copy(
      accountId = accountId.serverId,
      createdAt = createdAt,
      deviceNickname = "legacy-different"
    )

    cloudBackupStore.setString(
      cloudAccount,
      key = "cb-${accountId.serverId}",
      value = accountSpecificBackup.toBackupJson()
    ).shouldBeOk()
    cloudBackupStore.setString(
      cloudAccount,
      key = "cloud-backup",
      value = legacyBackup.toBackupJson()
    ).shouldBeOk()

    cloudBackupService.migrateBackupToAccountIdKey(cloudAccount).shouldBeOk()

    cloudBackupService.readBackup(accountId, cloudAccount)
      .shouldBeOk(accountSpecificBackup)
    cloudBackupStore.getString(cloudAccount, key = "cloud-backup")
      .shouldBeOk(legacyBackup.toBackupJson())
  }

  test("legacy active migration overwrites different-account account-specific backup") {
    sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    val accountSpecificBackup = CloudBackupV3WithFullAccountMock.copy(
      accountId = "other-account",
      createdAt = Instant.parse("2024-02-01T00:00:00Z"),
      deviceNickname = "wrong-account"
    )
    val legacyBackup = CloudBackupV3WithFullAccountMock.copy(
      accountId = accountId.serverId,
      createdAt = Instant.parse("2024-01-01T00:00:00Z"),
      deviceNickname = "legacy-target-account"
    )

    cloudBackupStore.setString(
      cloudAccount,
      key = "cb-${accountId.serverId}",
      value = accountSpecificBackup.toBackupJson()
    ).shouldBeOk()
    cloudBackupStore.setString(
      cloudAccount,
      key = "cloud-backup",
      value = legacyBackup.toBackupJson()
    ).shouldBeOk()

    cloudBackupService.migrateBackupToAccountIdKey(cloudAccount).shouldBeOk()

    cloudBackupService.readBackup(accountId, cloudAccount)
      .shouldBeOk(legacyBackup)
    cloudBackupStore.getString(cloudAccount, key = "cloud-backup").shouldBeOk(null)
  }

  test("legacy active migration overwrites malformed account-specific backup") {
    sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    val legacyBackup = CloudBackupV3WithFullAccountMock.copy(
      accountId = accountId.serverId,
      createdAt = Instant.parse("2024-02-01T00:00:00Z"),
      deviceNickname = "legacy-valid"
    )

    cloudBackupStore.setString(
      cloudAccount,
      key = "cb-${accountId.serverId}",
      value = "malformed"
    ).shouldBeOk()
    cloudBackupStore.setString(
      cloudAccount,
      key = "cloud-backup",
      value = legacyBackup.toBackupJson()
    ).shouldBeOk()

    cloudBackupService.migrateBackupToAccountIdKey(cloudAccount).shouldBeOk()

    cloudBackupService.readBackup(accountId, cloudAccount)
      .shouldBeOk(legacyBackup)
    cloudBackupStore.getString(cloudAccount, key = "cloud-backup").shouldBeOk(null)
  }

  backupTestData(clock).forEach {
    val backup = it.backup
    val backupJson = it.json

    context(it.testName) {
      test("shared cloud backups is off - write backup to cloud key-value store and dao") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)
        cloudBackupService.writeBackup(accountId, cloudAccount, backup, true).shouldBeOk()

        cloudBackupDao.get(accountId.serverId).shouldBeOk(backup)
        cloudBackupStore.getString(cloudAccount, key = "cloud-backup").shouldBeOk(backupJson)
        cloudBackupStore.getString(cloudAccount, key = "cb-${accountId.serverId}").shouldBeOk(null)
      }

      test("shared cloud backups is off - write backup - dao error") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)
        cloudBackupDao.returnError = true

        cloudBackupService.writeBackup(accountId, cloudAccount, backup, true)
          .shouldBeErr(UnrectifiableCloudBackupError(BackupStorageError()))

        cloudBackupStore.getString(cloudAccount, key = "cloud-backup").shouldBeOk(backupJson)
        cloudBackupDao.get(accountId.serverId).shouldBeErr(BackupStorageError())
      }

      test("shared cloud backups is off - write backup - cloud key-value error") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)
        cloudBackupStore.returnError = true

        cloudBackupService.writeBackup(accountId, cloudAccount, backup, true)
          .shouldBeErr(UnrectifiableCloudBackupError(CloudError()))

        cloudBackupStore.getString(cloudAccount, key = "cb-${accountId.serverId}")
          .shouldBeErr(CloudError())
        // Backup was not written to local storage because we failed to write it to cloud store
        cloudBackupDao.get(accountId.serverId).shouldBeOk(null)
      }

      test("shared cloud backups is off - write backup - error authenticating") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        val error = Error("foo")
        authTokensService.refreshAccessTokenError = error

        cloudBackupService.writeBackup(accountId, cloudAccount, backup, true)
          .shouldBeErr(UnrectifiableCloudBackupError(error))

        // Backup was not written to local storage because we failed to write it to cloud store
        cloudBackupDao.get(accountId.serverId).shouldBeOk(null)
      }

      test("shared cloud backups is off - backup exists in cloud-key value store") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        cloudBackupStore.setString(cloudAccount, key = "cloud-backup", value = backupJson)

        cloudBackupService.readActiveBackup(cloudAccount).shouldBeOk(backup)
      }

      test("shared cloud backups is off - archiveBackup stores backup under timestamped key") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)
        cloudBackupService.writeBackup(accountId, cloudAccount, backup, true).shouldBeOk()

        cloudBackupService.archiveBackup(cloudAccount, backup).shouldBeOk()

        val keys = cloudBackupStore.keys(cloudAccount).shouldBeOk()
        keys.shouldContain("cloud-backup-${clock.now()}")
        val archivedKeys = keys.filter { it.startsWith("cloud-backup-${clock.now()}") }
        archivedKeys.size shouldBe 1
        cloudBackupStore.getString(cloudAccount, archivedKeys.first()).shouldBeOk(backupJson)
      }

      test("shared cloud backups is off - readArchivedBackups() returns only backups that have been archived") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)
        cloudBackupService.writeBackup(accountId, cloudAccount, backup, true).shouldBeOk()
        cloudBackupService.archiveBackup(cloudAccount, backup).shouldBeOk()

        val backups = cloudBackupService.readArchivedBackups(cloudAccount).shouldBeOk()
        backups.size shouldBe 1
        backups.all { it == backup } shouldBe true
      }

      test("shared cloud backups is off - readArchivedBackups() returns empty list when none exist") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        cloudBackupService.readArchivedBackups(cloudAccount).shouldBeOk(emptyList())
      }

      test("shared cloud backups is off - readActiveBackup reads legacy backup when both legacy and account-specific exist") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        // Setup: Put backups in both legacy and account-specific format
        cloudBackupStore.setString(cloudAccount, key = "cloud-backup", value = backupJson)
        cloudBackupStore.setString(cloudAccount, key = "cb-${accountId.serverId}", value = backupJson)

        // Should read the legacy backup
        cloudBackupService.readActiveBackup(cloudAccount).shouldBeOk(backup)
      }

      test("shared cloud backups is off - readActiveBackup works with legacy format only") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        // Setup: Only legacy backup exists
        cloudBackupStore.setString(cloudAccount, key = "cloud-backup", value = backupJson)

        // Should successfully read the legacy backup
        cloudBackupService.readActiveBackup(cloudAccount)
          .shouldBeOk()
          .shouldNotBeNull()
          .accountId
          .shouldBe(backup.accountId)
      }

      test("shared cloud backups is off - readActiveBackup returns null for account-specific format only") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        // Setup: Only account-specific format backup exists
        cloudBackupStore.setString(cloudAccount, key = "cb-${accountId.serverId}", value = backupJson)

        // Should return null as it only reads from legacy key
        cloudBackupService.readActiveBackup(cloudAccount).shouldBeOk(null)
      }

      test("shared cloud backups is off - readActiveBackup returns backup by ignoring the wrong account's backup in shared cloud") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        val otherAccountId = FullAccountId("other-account")
        accountService.setActiveAccount(FullAccountMock.copy(accountId = otherAccountId))

        // Setup: Only other account's backup exists
        cloudBackupStore.setString(cloudAccount, key = "cloud-backup", value = backupJson)

        // Should return backup by ignoring account ID when flag is off
        cloudBackupService.readActiveBackup(cloudAccount).shouldBeOk(backup)
      }

      test("shared cloud backups is off - readAllBackups returns legacy backups in shared cloud account") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        val otherAccountId = FullAccountId("other-account")

        // Setup: Multiple account-specific backups (simulating shared cloud account)
        cloudBackupStore.setString(cloudAccount, key = "cb-${accountId.serverId}", value = backupJson)
        cloudBackupStore.setString(cloudAccount, key = "cb-${otherAccountId.serverId}", value = backupJson)
        cloudBackupStore.setString(cloudAccount, key = "cloud-backup", value = backupJson) // legacy

        // Should return only the legacy backup
        val allBackups = cloudBackupService.readAllBackups(cloudAccount).shouldBeOk()
        allBackups.size shouldBe 1
      }

      test("readBackup prefers account-specific backup over legacy backup") {
        val requestedAccountId = FullAccountId("requested-account")
        val accountSpecificBackup = backup.withAccountId(requestedAccountId.serverId)
        val legacyBackup = backup.withAccountId("legacy-account")
        cloudBackupStore.setString(
          cloudAccount,
          key = "cb-${requestedAccountId.serverId}",
          value = accountSpecificBackup.toBackupJson()
        )
        cloudBackupStore.setString(
          cloudAccount,
          key = "cloud-backup",
          value = legacyBackup.toBackupJson()
        )

        cloudBackupService.readBackup(requestedAccountId, cloudAccount)
          .shouldBeOk(accountSpecificBackup)
      }

      test("readBackup falls back to legacy backup when account-specific backup is missing") {
        val requestedAccountId = FullAccountId("requested-account")
        val legacyBackup = backup.withAccountId("legacy-account")
        cloudBackupStore.setString(
          cloudAccount,
          key = "cloud-backup",
          value = legacyBackup.toBackupJson()
        )

        cloudBackupService.readBackup(requestedAccountId, cloudAccount)
          .shouldBeOk(legacyBackup)
      }

      test("readBackup returns null when account-specific and legacy backups are missing") {
        cloudBackupService.readBackup(FullAccountId("missing-account"), cloudAccount)
          .shouldBeOk(null)
      }

      test("shared cloud backups is off - readArchivedBackups finds both legacy and account-specific format archived backups") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)

        // Manually add a legacy format archived backup
        cloudBackupStore.setString(cloudAccount, key = "cloud-backup-${clock.now()}", value = backupJson)

        // Manually add an account-specific format archived backup
        cloudBackupStore.setString(cloudAccount, key = "cb-${accountId.serverId}-${clock.now()}", value = backupJson)

        val backups = cloudBackupService.readArchivedBackups(cloudAccount).shouldBeOk()
        backups.size shouldBe 2 // Should find both account-specific and legacy archived backups
        backups.all { it == backup } shouldBe true
      }

      test("shared cloud backups is off - migrateBackupToAccountIdKey re-archives legacy archived backup") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        val oldKey = "cloud-backup-${clock.now()}"
        // Setup: Put a legacy backup in cloud storage
        cloudBackupStore.setString(cloudAccount, key = oldKey, value = backupJson)

        // Run migration
        cloudBackupService.migrateBackupToAccountIdKey(cloudAccount).shouldBeOk()

        // The old key should be removed
        cloudBackupStore.getString(cloudAccount, key = oldKey).shouldBeOk(null)

        // A new archived key should be created
        val allKeys = cloudBackupStore.keys(cloudAccount).shouldBeOk()
        val archivedKeys = allKeys.filter { it.startsWith("cloud-backup-") }
        archivedKeys.size shouldBe 1
      }

      test("shared cloud backups is off - migrateBackupToAccountIdKey does nothing when no legacy backup exists") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        // Setup: No legacy backup
        cloudBackupStore.getString(cloudAccount, key = "cloud-backup").shouldBeOk(null)

        // Run migration
        cloudBackupService.migrateBackupToAccountIdKey(cloudAccount).shouldBeOk()

        // Verify nothing changed
        cloudBackupStore.getString(cloudAccount, key = "cb-${accountId.serverId}").shouldBeOk(null)
        val allKeys = cloudBackupStore.keys(cloudAccount).shouldBeOk()
        allKeys.isEmpty() shouldBe true
      }

      test("shared cloud backups is off - migrateBackupToAccountIdKey removes legacy backup") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        // Setup: Both legacy and account-specific backups exist
        cloudBackupStore.setString(cloudAccount, key = "cloud-backup", value = backupJson)
        cloudBackupStore.setString(cloudAccount, key = "cb-${accountId.serverId}", value = backupJson)

        // Run migration
        cloudBackupService.migrateBackupToAccountIdKey(cloudAccount).shouldBeOk()

        // Verify legacy backup is removed and account-specific one remains
        cloudBackupStore.getString(cloudAccount, key = "cloud-backup").shouldBeOk(null)
        cloudBackupStore.getString(cloudAccount, key = "cb-${accountId.serverId}").shouldBeOk().shouldNotBeNull()
      }

      test("shared cloud backups is off - writes only to legacy key") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        // Disable feature flag
        sharedCloudBackupsFeatureFlag.setFlagValue(
          FeatureFlagValue.BooleanFlag(false)
        )

        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)
        cloudBackupService.writeBackup(accountId, cloudAccount, backup, true).shouldBeOk()

        // Should only write to legacy key when feature flag is disabled
        cloudBackupStore.getString(cloudAccount, key = "cloud-backup").shouldBeOk(backupJson)
        cloudBackupStore.getString(cloudAccount, key = "cb-${accountId.serverId}").shouldBeOk(null)
      }

      test("shared cloud backups is off - reads only from legacy key") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
        // Disable feature flag
        sharedCloudBackupsFeatureFlag.setFlagValue(
          FeatureFlagValue.BooleanFlag(false)
        )

        // Put backup in legacy key
        cloudBackupStore.setString(cloudAccount, key = "cloud-backup", value = backupJson)

        // Should read from legacy key
        cloudBackupService.readActiveBackup(cloudAccount).shouldBeOk(backup)
      }

      test("shared cloud backups is off - migration re-archives") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))

        val oldKey = "cloud-backup-${clock.now()}"
        // Setup: Put a legacy backup in cloud storage
        cloudBackupStore.setString(cloudAccount, key = oldKey, value = backupJson)

        // Run migration
        cloudBackupService.migrateBackupToAccountIdKey(cloudAccount).shouldBeOk()

        // Verify legacy is re-archived
        cloudBackupStore.getString(cloudAccount, key = oldKey)
          .shouldBeOk().shouldBeNull()
        val newKeys = cloudBackupStore.keys(cloudAccount).shouldBeOk()
        newKeys.count { it.startsWith("cloud-backup-") } shouldBe 1
      }

      test("shared cloud backups is on - write backup to cloud key-value store and dao") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)
        cloudBackupService.writeBackup(accountId, cloudAccount, backup, true).shouldBeOk()

        cloudBackupDao.get(accountId.serverId).shouldBeOk(backup)

        cloudBackupStore.getString(cloudAccount, key = "cloud-backup").shouldBeOk(null)

        cloudBackupStore.getString(cloudAccount, key = "cb-${accountId.serverId}")
          .shouldBeOk().shouldNotBeNull()
      }

      test("shared cloud backups is on - write backup - dao error") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)
        cloudBackupDao.returnError = true

        cloudBackupService.writeBackup(accountId, cloudAccount, backup, true)
          .shouldBeErr(UnrectifiableCloudBackupError(BackupStorageError()))

        cloudBackupStore.getString(cloudAccount, key = "cloud-backup").shouldBeOk(null)
        cloudBackupStore.getString(cloudAccount, key = "cb-${accountId.serverId}")
          .shouldBeOk().shouldNotBeNull()
        cloudBackupDao.get(accountId.serverId).shouldBeErr(BackupStorageError())
      }

      test("shared cloud backups is on - write backup - cloud key-value error") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)
        cloudBackupStore.returnError = true

        cloudBackupService.writeBackup(accountId, cloudAccount, backup, true)
          .shouldBeErr(UnrectifiableCloudBackupError(CloudError()))

        cloudBackupStore.getString(cloudAccount, key = "cb-${accountId.serverId}")
          .shouldBeErr(CloudError())
        // Backup was not written to local storage because we failed to write it to cloud store
        cloudBackupDao.get(accountId.serverId).shouldBeOk(null)
      }

      test("shared cloud backups is on - write backup - error authenticating") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        val error = Error("foo")
        authTokensService.refreshAccessTokenError = error

        cloudBackupService.writeBackup(accountId, cloudAccount, backup, true)
          .shouldBeErr(UnrectifiableCloudBackupError(error))

        // Backup was not written to local storage because we failed to write it to cloud store
        cloudBackupDao.get(accountId.serverId).shouldBeOk(null)
      }

      test("shared cloud backups is on - backup exists in cloud-key value store") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        deviceInfoProvider.deviceNicknameValue = "Test Device"
        cloudBackupStore.setString(cloudAccount, key = "cloud-backup", value = backupJson)

        cloudBackupService.readActiveBackup(cloudAccount).shouldBeOk()
          .shouldNotBeNull()
          .accountId
          .shouldBe(backup.accountId)
      }

      test("shared cloud backups is on - archiveBackup stores backup under timestamped key") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)
        cloudBackupService.archiveBackup(cloudAccount, backup).shouldBeOk()

        val keys = cloudBackupStore.keys(cloudAccount).shouldBeOk()
        keys.shouldContain("cb-${accountId.serverId}-${clock.now()}")
        keys.size shouldBe 1
        cloudBackupStore.getString(cloudAccount, keys.first()).shouldBeOk().shouldNotBeNull()
      }

      test("shared cloud backups is on - readArchivedBackups() returns only backups that have been archived") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)
        cloudBackupService.writeBackup(accountId, cloudAccount, backup, true).shouldBeOk()
        cloudBackupService.archiveBackup(cloudAccount, backup).shouldBeOk()

        val backups = cloudBackupService.readArchivedBackups(cloudAccount).shouldBeOk()
        backups.size shouldBe 1
        backups.all { it.accountId == backup.accountId } shouldBe true
      }

      test("shared cloud backups is on - readArchivedBackups() returns empty list when none exist") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        cloudBackupService.readArchivedBackups(cloudAccount).shouldBeOk(emptyList())
      }

      test("shared cloud backups is on - readActiveBackup reads account-specific backup when both legacy and account-specific exist") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        deviceInfoProvider.deviceNicknameValue = "Test Device"
        // Setup: Put backups in both legacy and account-specific format
        cloudBackupStore.setString(cloudAccount, key = "cloud-backup", value = backupJson)
        cloudBackupStore.setString(cloudAccount, key = "cb-${accountId.serverId}", value = backupJson)

        // Should read the account-specific backup
        cloudBackupService.readActiveBackup(cloudAccount)
          .shouldBeOk()
          .shouldNotBeNull()
          .accountId
          .shouldBe(backup.accountId)
      }

      test("shared cloud backups is on - readActiveBackup works with legacy format only") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        deviceInfoProvider.deviceNicknameValue = "Test Device"
        // Setup: Only legacy backup exists
        cloudBackupStore.setString(cloudAccount, key = "cloud-backup", value = backupJson)

        // Should successfully read the legacy backup
        cloudBackupService.readActiveBackup(cloudAccount)
          .shouldBeOk()
          .shouldNotBeNull()
          .accountId
          .shouldBe(backup.accountId)
      }

      test("shared cloud backups is on - readActiveBackup works with account-specific format only") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        // Setup: Only account-specific format backup exists
        cloudBackupStore.setString(cloudAccount, key = "cb-${accountId.serverId}", value = backupJson)

        // Should successfully read the account-specific format backup
        cloudBackupService.readActiveBackup(cloudAccount).shouldBeOk(backup)
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
        cloudBackupStore.setString(
          cloudAccount,
          key = "cb-${otherAccountId.serverId}",
          value = backupJson
        )

        // Should return AccountIdMismatched error because the backup's account ID doesn't match the active account.
        cloudBackupService.readActiveBackup(cloudAccount)
          .shouldBeErrOfType<CloudBackupError.AccountIdMismatched>()
      }

      test("shared cloud backups is on - readAllBackups returns all backups in shared cloud account") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        deviceInfoProvider.deviceNicknameValue = "Test Device"
        val otherAccountId = FullAccountId("other-account")
        val otherAccountBackup = backup.withAccountId(otherAccountId.serverId)

        // Setup: Multiple account-specific backups (simulating shared cloud account)
        cloudBackupStore.setString(cloudAccount, key = "cb-${accountId.serverId}", value = backupJson)
        cloudBackupStore.setString(
          cloudAccount,
          key = "cb-${otherAccountId.serverId}",
          value = otherAccountBackup.toBackupJson()
        )
        cloudBackupStore.setString(cloudAccount, key = "cloud-backup", value = backupJson) // legacy

        // Should return all 2 backups
        val allBackups = cloudBackupService.readAllBackups(cloudAccount).shouldBeOk()
        allBackups.size shouldBe 2
      }

      test("shared cloud backups is on - readArchivedBackups finds both legacy and account-specific format archived backups") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)

        // Create and archive backup using account-specific format
        cloudBackupService.writeBackup(accountId, cloudAccount, backup, true).shouldBeOk()
        cloudBackupService.archiveBackup(cloudAccount, backup).shouldBeOk()

        // Manually add a legacy format archived backup
        cloudBackupStore.setString(cloudAccount, key = "cloud-backup-${clock.now()}", value = backupJson)

        val backups = cloudBackupService.readArchivedBackups(cloudAccount).shouldBeOk()
        backups.size shouldBe 2 // Should find both account-specific and legacy, but not the active backup
        backups.all { it.accountId == backup.accountId } shouldBe true
      }

      test("shared cloud backups is on - migrateBackupToAccountIdKey migrates legacy backup to account-specific format") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        val v2Key = "cloud-backup"
        val v3Key = "cb-${accountId.serverId}"
        // Setup: Put a legacy backup in cloud storage
        cloudBackupStore.setString(cloudAccount, key = v2Key, value = backupJson)

        // Verify no account-specific backup exists yet
        cloudBackupStore.getString(cloudAccount, key = v3Key).shouldBeOk(null)

        // Run migration
        cloudBackupService.migrateBackupToAccountIdKey(cloudAccount).shouldBeOk()

        // Verify migration results
        // 1. New account-specific backup should exist (keyed by account ID only)
        cloudBackupStore.getString(cloudAccount, key = v3Key).shouldBeOk().shouldNotBeNull()

        // 2. legacy backup should be removed if migration is successful.
        cloudBackupStore.getString(cloudAccount, key = v2Key).shouldBeOk(null)

        // 3. account-specific backup should be archived (look for archived keys)
        val allKeys = cloudBackupStore.keys(cloudAccount).shouldBeOk()
        val archivedKeys = allKeys.filter { it.startsWith("cb-") }
        archivedKeys.size shouldBe 1
      }

      test("shared cloud backups is on - migrateBackupToAccountIdKey does nothing when no legacy backup exists") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        // Setup: No legacy backup
        cloudBackupStore.getString(cloudAccount, key = "cloud-backup").shouldBeOk(null)

        // Run migration
        cloudBackupService.migrateBackupToAccountIdKey(cloudAccount).shouldBeOk()

        // Verify nothing changed
        cloudBackupStore.getString(cloudAccount, key = "cb-${accountId.serverId}").shouldBeOk(null)
        val allKeys = cloudBackupStore.keys(cloudAccount).shouldBeOk()
        allKeys.isEmpty() shouldBe true
      }

      test("shared cloud backups is on - migrateBackupToAccountIdKey should remove legacy when account-specific backup already exists") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        // Setup: Both legacy and account-specific backups exist
        cloudBackupStore.setString(cloudAccount, key = "cloud-backup", value = backupJson)
        cloudBackupStore.setString(cloudAccount, key = "cb-${accountId.serverId}", value = backupJson)

        // Run migration
        cloudBackupService.migrateBackupToAccountIdKey(cloudAccount).shouldBeOk()

        // Verify both backups still exist (no migration needed)
        cloudBackupStore.getString(cloudAccount, key = "cloud-backup").shouldBeOk()
          .shouldBeNull()
        cloudBackupStore.getString(cloudAccount, key = "cb-${accountId.serverId}").shouldBeOk()
          .shouldNotBeNull()
      }

      test("shared cloud backups is off - writes only to legacy key") {
        // Disable feature flag
        sharedCloudBackupsFeatureFlag.setFlagValue(
          FeatureFlagValue.BooleanFlag(false)
        )

        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)
        cloudBackupService.writeBackup(accountId, cloudAccount, backup, true).shouldBeOk()

        // Should only write to legacy key when feature flag is disabled
        cloudBackupStore.getString(cloudAccount, key = "cloud-backup").shouldBeOk(backupJson)
        cloudBackupStore.getString(cloudAccount, key = "cb-${accountId.serverId}").shouldBeOk(null)
      }

      test("shared cloud backups is on - reads only from legacy key") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        // Disable feature flag
        sharedCloudBackupsFeatureFlag.setFlagValue(
          FeatureFlagValue.BooleanFlag(false)
        )

        // Put backup in legacy key
        cloudBackupStore.setString(cloudAccount, key = "cloud-backup", value = backupJson)

        // Should read from legacy key
        cloudBackupService.readActiveBackup(cloudAccount)
          .shouldBeOk().shouldNotBeNull()
      }

      test("shared cloud backups is on - migration should remove legacy") {
        sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

        // Setup: Put a legacy backup in cloud storage
        cloudBackupStore.setString(cloudAccount, key = "cloud-backup-${clock.now()}", value = backupJson)

        // Run migration
        cloudBackupService.migrateBackupToAccountIdKey(cloudAccount).shouldBeOk()

        // Verify nothing changed - no account-specific backup created
        cloudBackupStore.getString(cloudAccount, key = "cb-${accountId.serverId}-${clock.now()}")
          .shouldBeOk().shouldNotBeNull()
        cloudBackupStore.getString(cloudAccount, key = "cloud-backup-${clock.now()}")
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
      else -> error("Unknown backup version")
    }
    BackupTestData(
      testName = "backup $version",
      backup = updatedBackup as CloudBackup,
      json = json
    )
  }

private fun CloudBackup.withAccountId(accountId: String): CloudBackup =
  when (this) {
    is CloudBackupV2 -> copy(accountId = accountId)
    is CloudBackupV3 -> copy(accountId = accountId)
  }

private fun CloudBackup.toBackupJson(): String =
  when (this) {
    is CloudBackupV2 -> Json.encodeToString(this)
    is CloudBackupV3 -> Json.encodeToString(this)
  }

private suspend fun CloudBackupStoreFake.setString(
  account: CloudStoreAccount,
  key: String,
  value: String,
): Result<Unit, CloudError> = set(account, key, value.encodeUtf8())

private suspend fun CloudBackupStoreFake.getString(
  account: CloudStoreAccount,
  key: String,
): Result<String?, CloudError> = get(account, key).map { it?.utf8() }
