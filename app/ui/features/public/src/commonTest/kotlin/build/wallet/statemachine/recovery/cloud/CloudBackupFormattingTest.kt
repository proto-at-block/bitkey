package build.wallet.statemachine.recovery.cloud

import build.wallet.cloud.backup.CloudBackupV2WithFullAccountMock
import build.wallet.cloud.backup.CloudBackupV2WithLiteAccountMock
import build.wallet.cloud.backup.CloudBackupV3WithFullAccountMock
import build.wallet.cloud.backup.CloudBackupV3WithLiteAccountMock
import build.wallet.statemachine.core.Icon
import build.wallet.time.DateTimeFormatterMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone

class CloudBackupFormattingTest : FunSpec({
  val dateTimeFormatter = DateTimeFormatterMock()
  val timeZone = TimeZone.UTC

  test("formats V3 full account backup with device nickname") {
    val backup = CloudBackupV3WithFullAccountMock.copy(
      deviceNickname = "Joel's iPhone (2)"
    )

    val model = formatCloudBackupItemModel(backup, dateTimeFormatter, timeZone)

    model.displayLabel.shouldBe("Joel's iPhone (2)")
    model.secondaryText.shouldContain("Last backed up:")
    model.icon.shouldBe(Icon.SmallIconBitkey)
    model.backup.shouldBe(backup)
  }

  test("formats V3 full account backup without device nickname") {
    val backup = CloudBackupV3WithFullAccountMock.copy(deviceNickname = null)

    val model = formatCloudBackupItemModel(backup, dateTimeFormatter, timeZone)

    model.displayLabel.shouldContain("Wallet Backup")
    model.displayLabel.shouldContain(backup.accountId.take(8))
    model.secondaryText.shouldContain("Last backed up:")
    model.icon.shouldBe(Icon.SmallIconBitkey)
  }

  test("formats V3 lite account backup") {
    val backup = CloudBackupV3WithLiteAccountMock

    val model = formatCloudBackupItemModel(backup, dateTimeFormatter, timeZone)

    model.displayLabel.shouldContain("Lite Test Device")
    model.secondaryText.shouldBe("Recovery Contact Backup")
    model.icon.shouldBe(Icon.SmallIconShieldPerson)
  }

  test("formats V2 full account backup") {
    val backup = CloudBackupV2WithFullAccountMock

    val model = formatCloudBackupItemModel(backup, dateTimeFormatter, timeZone)

    model.displayLabel.shouldContain("Wallet Backup")
    model.displayLabel.shouldContain(backup.accountId.take(8))
    model.secondaryText.shouldContain("Account ID:")
    model.secondaryText.shouldContain(backup.accountId.take(12))
    model.icon.shouldBe(Icon.SmallIconBitkey)
  }

  test("formats V2 lite account backup") {
    val backup = CloudBackupV2WithLiteAccountMock

    val model = formatCloudBackupItemModel(backup, dateTimeFormatter, timeZone)

    model.displayLabel.shouldContain("Recovery Contact Backup")
    model.displayLabel.shouldContain(backup.accountId.take(8))
    model.secondaryText.shouldBe("Recovery Contact Backup")
    model.icon.shouldBe(Icon.SmallIconShieldPerson)
  }

  test("truncates account ID prefix to 8 characters in fallback label") {
    val backup = CloudBackupV3WithFullAccountMock.copy(
      deviceNickname = null,
      accountId = "very-long-account-id-that-should-be-truncated"
    )

    val model = formatCloudBackupItemModel(backup, dateTimeFormatter, timeZone)

    model.displayLabel.shouldBe("Wallet Backup (very-lon...)")
  }

  test("uses appropriate icon for full vs lite accounts") {
    val fullBackup = CloudBackupV3WithFullAccountMock
    val liteBackup = CloudBackupV3WithLiteAccountMock

    val fullModel = formatCloudBackupItemModel(fullBackup, dateTimeFormatter, timeZone)
    val liteModel = formatCloudBackupItemModel(liteBackup, dateTimeFormatter, timeZone)

    fullModel.icon.shouldBe(Icon.SmallIconBitkey)
    liteModel.icon.shouldBe(Icon.SmallIconShieldPerson)
  }

  test("formats V3 backup date using provided time zone") {
    val dateTimeFormatter = DateTimeFormatterMock(
      timeToFormattedTime = mapOf(
        // 2025-11-15T00:30:00Z
        LocalDateTime(year = 2025, monthNumber = 11, dayOfMonth = 15, hour = 0, minute = 30) to
          "11/15/2025 at 12:30am",
        // 2025-11-14T14:30:00-10:00 (Pacific/Honolulu)
        LocalDateTime(year = 2025, monthNumber = 11, dayOfMonth = 14, hour = 14, minute = 30) to
          "11/14/2025 at 2:30pm"
      )
    )

    val utcModel = formatCloudBackupItemModel(
      backup = CloudBackupV3WithFullAccountMock.copy(
        deviceNickname = null,
        createdAt = Instant.parse("2025-11-15T00:30:00Z")
      ),
      dateTimeFormatter = dateTimeFormatter,
      timeZone = TimeZone.UTC
    )

    val honoluluModel = formatCloudBackupItemModel(
      backup = CloudBackupV3WithFullAccountMock.copy(
        deviceNickname = null,
        createdAt = Instant.parse("2025-11-15T00:30:00Z")
      ),
      dateTimeFormatter = dateTimeFormatter,
      timeZone = TimeZone.of("Pacific/Honolulu")
    )

    utcModel.secondaryText.shouldBe("Last backed up: 11/15/2025 at 12:30am")
    honoluluModel.secondaryText.shouldBe("Last backed up: 11/14/2025 at 2:30pm")
    utcModel.secondaryText.shouldNotBe(honoluluModel.secondaryText)
  }
})
