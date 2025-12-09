package build.wallet.cloud.backup.csek

import build.wallet.cloud.backup.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CloudBackupTests : FunSpec({

  context("parameterized tests for all backup versions") {
    AllFullAccountBackupMocks.forEach { backup ->
      val backupVersion = when (backup) {
        is CloudBackupV2 -> "2"
        is CloudBackupV3 -> "3"
        else -> "unknown"
      }

      context("backup v$backupVersion") {
        test("redact backup - toString()") {
          backup.toString().shouldBe("CloudBackupV$backupVersion(██)")
        }

        test("redact backup - interpolation") {
          "$backup".shouldBe("CloudBackupV$backupVersion(██)")
        }
      }
    }
  }
})
