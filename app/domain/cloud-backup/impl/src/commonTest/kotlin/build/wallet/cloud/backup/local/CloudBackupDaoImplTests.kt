package build.wallet.cloud.backup.local

import build.wallet.cloud.backup.CLOUD_BACKUP_V2_WITH_FULL_ACCOUNT_FIELDS_JSON
import build.wallet.cloud.backup.CloudBackupV2WithFullAccountMock
import build.wallet.store.EncryptedKeyValueStoreFactoryFake
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.Ok
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class CloudBackupDaoImplTests : FunSpec({
  val secureStoreFactory = EncryptedKeyValueStoreFactoryFake()

  val accountId = "1"

  context("v2") {
    val backupV2 = CloudBackupV2WithFullAccountMock
    val backupV2Json = CLOUD_BACKUP_V2_WITH_FULL_ACCOUNT_FIELDS_JSON

    val dao =
      CloudBackupDaoImpl(
        encryptedKeyValueStoreFactory = secureStoreFactory
      )

    afterTest {
      secureStoreFactory.store.clear()
    }

    test("set and get a CloudBackupV2") {
      dao.set(accountId = accountId, backup = backupV2)

      secureStoreFactory.store.getStringOrNull(key = accountId)
        .shouldNotBeNull()
        .shouldEqualJson(backupV2Json)
      dao.get(accountId = accountId).shouldBe(Ok(backupV2))
    }

    test("set CloudBackupV2") {
      dao.set(accountId = accountId, backup = backupV2)
      dao.set(accountId = accountId, backup = backupV2)

      secureStoreFactory.store.getStringOrNull(key = accountId)
        .shouldNotBeNull()
        .shouldEqualJson(backupV2Json)
      dao.get(accountId = accountId).shouldBe(Ok(backupV2))
    }

    test("read existing CloudBackupV2") {
      secureStoreFactory.store.putString(key = accountId, value = backupV2Json)

      dao.get(accountId = accountId).shouldBe(Ok(backupV2))
    }

    test("fail to decode CloudBackupV2") {
      val invalidBackupJson = "{}"
      secureStoreFactory.store.putString(key = accountId, value = invalidBackupJson)
      dao.get(accountId = accountId)
        .shouldBeErrOfType<BackupStorageError>()
    }
  }
})
