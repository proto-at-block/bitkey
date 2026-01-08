package build.wallet.cloud.backup

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.SharedCloudBackupsFeatureFlag
import build.wallet.time.ClockFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

class CloudBackupRepositoryKeysTests : FunSpec({
  val accountId = FullAccountId("urn:wallet-account:01KBFS9P38V24K2FHTCK1EXWPK")

  // Added a fake toCloudBackup function as the original could not be found.
  val backup: CloudBackup = CloudBackupV3WithFullAccountMock.copy(accountId = accountId.serverId)
  val clock = ClockFake()
  val featureFlagDao = FeatureFlagDaoFake()
  val sharedCloudBackupsFeatureFlag = SharedCloudBackupsFeatureFlag(featureFlagDao)

  val cloudBackupRepositoryKeys = CloudBackupRepositoryKeysImpl(
    sharedCloudBackupsFeatureFlag = sharedCloudBackupsFeatureFlag,
    clock = clock
  )

  context("isValidArchivedKey") {
    test("returns true for valid v2 key") {
      val key = "cloud-backup-2024-01-01T12:00:00Z"
      cloudBackupRepositoryKeys.isValidArchivedKey(key) shouldBe true
    }

    test("returns true for valid v3 key") {
      val key = "cb-urn:wallet-account:01KBFS9P38V24K2FHTCK1EXWPK-2024-01-01T12:00:00Z"
      cloudBackupRepositoryKeys.isValidArchivedKey(key) shouldBe true
    }

    test("returns false for invalid key") {
      val key = "invalid-key"
      cloudBackupRepositoryKeys.isValidArchivedKey(key) shouldBe false
    }

    test("returns false for active v2 key") {
      val key = "cloud-backup"
      cloudBackupRepositoryKeys.isValidArchivedKey(key) shouldBe false
    }

    test("returns false for active v3 key") {
      val key = "cb-urn:wallet-account:01KBFS9P38V24K2FHTCK1EXWPK"
      cloudBackupRepositoryKeys.isValidArchivedKey(key) shouldBe false
    }
  }

  context("isValidBackupKey") {
    test("returns true for valid v2 key") {
      val key = "cloud-backup"
      cloudBackupRepositoryKeys.isValidBackupKey(key) shouldBe true
    }

    test("returns true for valid v3 key") {
      val key = "cb-urn:wallet-account:01KBFS9P38V24K2FHTCK1EXWPK"
      cloudBackupRepositoryKeys.isValidBackupKey(key) shouldBe true
    }

    test("returns false for archived v2 key") {
      val key = "cloud-backup-2024-01-01T12:00:00Z"
      cloudBackupRepositoryKeys.isValidBackupKey(key) shouldBe false
    }

    test("returns false for archived v3 key") {
      val key = "cb-urn:wallet-account:01KBFS9P38V24K2FHTCK1EXWPK-2024-01-01T12:00:00Z"
      cloudBackupRepositoryKeys.isValidBackupKey(key) shouldBe false
    }

    test("returns false for invalid key") {
      val key = "invalid-key"
      cloudBackupRepositoryKeys.isValidBackupKey(key) shouldBe false
    }
  }

  context("archiveFormatKey") {
    test("returns v2 format when feature flag is disabled") {
      sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
      val now = Instant.fromEpochSeconds(1704110400)
      clock.now = now
      val expectedKey = "cloud-backup-$now"
      cloudBackupRepositoryKeys.archiveFormatKey(backup) shouldBe expectedKey
    }

    test("returns v3 format when feature flag is enabled") {
      sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
      val now = Instant.fromEpochSeconds(1704110400)
      clock.now = now
      val expectedKey = "cb-${backup.accountId}-$now"
      cloudBackupRepositoryKeys.archiveFormatKey(backup) shouldBe expectedKey
    }
  }

  context("activeBackupFormatKeyV3") {
    test("returns correct v3 key format") {
      val expectedKey = "cb-${accountId.serverId}"
      cloudBackupRepositoryKeys.activeBackupFormatAccountSpecificKey(accountId) shouldBe expectedKey
    }
  }

  context("activeBackupFormatKey") {
    test("returns v2 format when feature flag is disabled") {
      sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
      val expectedKey = "cloud-backup"
      cloudBackupRepositoryKeys.activeBackupFormatKey(backup) shouldBe expectedKey
    }

    test("returns v3 format when feature flag is enabled") {
      sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
      val expectedKey = "cb-${backup.accountId}"
      cloudBackupRepositoryKeys.activeBackupFormatKey(backup) shouldBe expectedKey
    }
  }
})
