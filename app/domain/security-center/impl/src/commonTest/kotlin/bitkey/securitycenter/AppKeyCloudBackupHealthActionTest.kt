package bitkey.securitycenter

import build.wallet.cloud.backup.health.AppKeyBackupStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock

class AppKeyCloudBackupHealthActionTest : FunSpec({
  test("Test AppKeyCloudBackupHealthAction functionality") {
    val healthyAction = AppKeyBackupHealthAction(AppKeyBackupStatus.Healthy(lastUploaded = Clock.System.now()))
    healthyAction.getRecommendations().shouldBeEmpty()
    healthyAction.category() shouldBe SecurityActionCategory.RECOVERY

    val backupMissingAction = AppKeyBackupHealthAction(AppKeyBackupStatus.ProblemWithBackup.BackupMissing)
    backupMissingAction.getRecommendations() shouldBe listOf(SecurityActionRecommendation.BACKUP_MOBILE_KEY)
    backupMissingAction.category() shouldBe SecurityActionCategory.RECOVERY
  }
})
