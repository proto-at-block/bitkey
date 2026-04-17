package build.wallet.debug.cloud

import build.wallet.bitkey.f8e.AccountId
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupStoreFake
import build.wallet.cloud.backup.CloudBackupStoreKeys
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import okio.ByteString.Companion.encodeUtf8

class CloudBackupStoreCleanerImplTests : FunSpec({
  test("deleteBackupsIn removes only cloud backup keys for the account") {
    val cloudBackupStoreKeys =
      object : CloudBackupStoreKeys {
        override fun isValidArchivedKey(key: String): Boolean =
          key == "cloud-backup-2024-01-01T00:00:00Z"

        override fun isValidBackupKey(key: String): Boolean = key == "cloud-backup"

        override fun archiveFormatKey(backup: CloudBackup): String = error("unused in test")

        override fun activeBackupFormatAccountSpecificKey(accountId: AccountId): String =
          error("unused in test")

        override fun activeBackupFormatKey(backup: CloudBackup): String = error("unused in test")

        override fun isLegacyActiveBackupKey(key: String): Boolean = false

        override fun isAccountSpecificActiveBackupKeyForAccount(
          key: String,
          accountId: AccountId,
        ): Boolean = false
      }
    val account = CloudAccountMock(instanceId = "account-1")
    val store = CloudBackupStoreFake()
    store.set(account = account, key = "cloud-backup", value = "backup-a".encodeUtf8()).shouldBeOk()
    store.set(account = account, key = "cloud-backup-2024-01-01T00:00:00Z", value = "backup-b".encodeUtf8()).shouldBeOk()
    store.set(account = account, key = "not-a-backup-key", value = "keep-me".encodeUtf8()).shouldBeOk()
    val deleter =
      CloudBackupStoreCleanerImpl(
        cloudBackupStore = store,
        cloudBackupStoreKeys = cloudBackupStoreKeys
      )
    val type = availableCloudBackupStoreTypes().first()

    deleter.deleteBackupsIn(type = type, cloudStoreAccount = account).shouldBeOk()

    store.get(account = account, key = "cloud-backup").shouldBeOk().shouldBeNull()
    store.get(account = account, key = "cloud-backup-2024-01-01T00:00:00Z").shouldBeOk().shouldBeNull()
    store.get(account = account, key = "not-a-backup-key").shouldBeOk("keep-me".encodeUtf8())
  }
})
