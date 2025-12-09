package build.wallet.cloud.backup.local

import build.wallet.cloud.backup.*
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

  val dao =
    CloudBackupDaoImpl(
      encryptedKeyValueStoreFactory = secureStoreFactory
    )

  afterTest {
    secureStoreFactory.store.clear()
  }

  context("parameterized tests for all backup versions") {
    AllFullAccountBackupMocks.forEach { backup ->
      val backupVersion = when (backup) {
        is CloudBackupV2 -> "v2"
        is CloudBackupV3 -> "v3"
        else -> "unknown"
      }

      context("cloud backup $backupVersion") {
        test("set and get a CloudBackup") {
          dao.set(accountId = accountId, backup = backup as CloudBackup)
          dao.get(accountId = accountId).shouldBe(Ok(backup))
        }

        test("set CloudBackup twice") {
          dao.set(accountId = accountId, backup = backup as CloudBackup)
          dao.set(accountId = accountId, backup = backup as CloudBackup)
          dao.get(accountId = accountId).shouldBe(Ok(backup))
        }
      }
    }
  }

  test("fail to decode invalid JSON") {
    val invalidBackupJson = "{}"
    secureStoreFactory.store.putString(key = accountId, value = invalidBackupJson)
    dao.get(accountId = accountId)
      .shouldBeErrOfType<BackupStorageError>()
  }

  context("v2 specific tests") {
    val backupV2 = CloudBackupV2WithFullAccountMock
    val backupV2Json = CLOUD_BACKUP_V2_WITH_FULL_ACCOUNT_FIELDS_JSON

    test("read existing CloudBackupV2 from JSON") {
      secureStoreFactory.store.putString(key = accountId, value = backupV2Json)
      dao.get(accountId = accountId).shouldBe(Ok(backupV2))
    }

    test("verify JSON serialization format") {
      dao.set(accountId = accountId, backup = backupV2)
      secureStoreFactory.store.getStringOrNull(key = accountId)
        .shouldNotBeNull()
        .shouldEqualJson(backupV2Json)
    }
  }

  context("v3 specific tests") {
    val backupV3 = CloudBackupV3WithFullAccountMock
    val backupV3Json = CLOUD_BACKUP_V3_WITH_FULL_ACCOUNT_FIELDS_JSON

    test("read existing CloudBackupV3 from JSON") {
      secureStoreFactory.store.putString(key = accountId, value = backupV3Json)
      dao.get(accountId = accountId).shouldBe(Ok(backupV3))
    }

    test("verify JSON serialization format") {
      dao.set(accountId = accountId, backup = backupV3)
      secureStoreFactory.store.getStringOrNull(key = accountId)
        .shouldNotBeNull()
        .shouldEqualJson(backupV3Json)
    }
  }
})
