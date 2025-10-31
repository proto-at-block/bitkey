package bitkey.recovery

import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.sqldelight.inMemorySqlDriver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class DescriptorBackupVerificationDaoImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()

  lateinit var dao: DescriptorBackupVerificationDao

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    dao = DescriptorBackupVerificationDaoImpl(databaseProvider)
  }

  test("getVerifiedBackup returns null when keyset not found") {
    dao.getVerifiedBackup("non-existent-keyset").value.shouldBeNull()
  }

  test("replaceAllVerifiedBackups replaces existing backups") {
    // Initial backups
    val initialBackups = listOf(
      VerifiedBackup(keysetId = "keyset-1"),
      VerifiedBackup(keysetId = "keyset-2")
    )
    dao.replaceAllVerifiedBackups(initialBackups)

    // Replace with new set
    val newBackups = listOf(
      VerifiedBackup(keysetId = "keyset-3"),
      VerifiedBackup(keysetId = "keyset-4")
    )
    dao.replaceAllVerifiedBackups(newBackups)

    // Old backups should be gone
    dao.getVerifiedBackup("keyset-1").value.shouldBeNull()
    dao.getVerifiedBackup("keyset-2").value.shouldBeNull()

    // New backups should exist
    dao.getVerifiedBackup("keyset-3").value.shouldBe(newBackups[0])
    dao.getVerifiedBackup("keyset-4").value.shouldBe(newBackups[1])
  }

  test("clearAllVerifiedBackups removes all backups") {
    val backups = listOf(
      VerifiedBackup(keysetId = "keyset-1"),
      VerifiedBackup(keysetId = "keyset-2")
    )
    dao.replaceAllVerifiedBackups(backups)

    dao.clear()

    dao.getVerifiedBackup("keyset-1").value.shouldBeNull()
    dao.getVerifiedBackup("keyset-2").value.shouldBeNull()
  }

  test("replaceAllVerifiedBackups handles duplicate keyset IDs") {
    val backups = listOf(
      VerifiedBackup(keysetId = "keyset-1"),
      VerifiedBackup(keysetId = "keyset-1")
    )

    dao.replaceAllVerifiedBackups(backups)

    dao.getVerifiedBackup("keyset-1").value.shouldBe(VerifiedBackup(keysetId = "keyset-1"))
  }
})
