package bitkey.securitycenter

import build.wallet.cloud.backup.health.MobileKeyBackupStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock

class MobileKeyCloudBackupHealthActionTest : FunSpec({
  test("Test MobileKeyCloudBackupHealthAction functionality") {
    val healthyAction = MobileKeyBackupHealthAction(MobileKeyBackupStatus.Healthy(lastUploaded = Clock.System.now()))
    healthyAction.getRecommendations().shouldBeEmpty()
    healthyAction.category() shouldBe SecurityActionCategory.RECOVERY

    val backupMissingAction = MobileKeyBackupHealthAction(MobileKeyBackupStatus.ProblemWithBackup.BackupMissing)
    backupMissingAction.getRecommendations() shouldBe listOf(SecurityActionRecommendation.BACKUP_MOBILE_KEY)
    backupMissingAction.category() shouldBe SecurityActionCategory.RECOVERY
  }
})
