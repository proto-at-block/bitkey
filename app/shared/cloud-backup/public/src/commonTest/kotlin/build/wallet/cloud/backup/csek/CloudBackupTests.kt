package build.wallet.cloud.backup.csek

import build.wallet.cloud.backup.CloudBackupV2WithFullAccountMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CloudBackupTests : FunSpec({

  context("v2") {
    test("redact backup - toString()") {
      CloudBackupV2WithFullAccountMock.toString().shouldBe("CloudBackupV2(██)")
    }

    test("redact backup - interpolation") {
      "$CloudBackupV2WithFullAccountMock".shouldBe("CloudBackupV2(██)")
    }
  }
})
