package bitkey.securitycenter

import build.wallet.cloud.backup.health.EakBackupStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock

class EakCloudBackupHealthActionTest : FunSpec({
  test("Test EakCloudBackupHealthAction functionality") {
    val healthyAction = EakBackupHealthAction(EakBackupStatus.Healthy(lastUploaded = Clock.System.now()))
    healthyAction.getRecommendations().shouldBeEmpty()
    healthyAction.category() shouldBe SecurityActionCategory.RECOVERY

    val backupMissingAction = EakBackupHealthAction(EakBackupStatus.ProblemWithBackup.BackupMissing)
    backupMissingAction.getRecommendations() shouldBe listOf(SecurityActionRecommendation.BACKUP_EAK)
    backupMissingAction.category() shouldBe SecurityActionCategory.RECOVERY
  }
})
