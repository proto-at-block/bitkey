package bitkey.securitycenter

import build.wallet.availability.FunctionalityFeatureStates
import build.wallet.cloud.backup.health.EekBackupStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock

class EekCloudBackupHealthActionTest : FunSpec({
  test("Test EekCloudBackupHealthAction functionality") {
    val healthyAction = EekBackupHealthAction(
      EekBackupStatus.Healthy(lastUploaded = Clock.System.now()),
      FunctionalityFeatureStates.FeatureState.Available
    )
    healthyAction.getRecommendations().shouldBeEmpty()
    healthyAction.category() shouldBe SecurityActionCategory.RECOVERY

    val backupMissingAction = EekBackupHealthAction(
      EekBackupStatus.ProblemWithBackup.BackupMissing,
      FunctionalityFeatureStates.FeatureState.Available
    )
    backupMissingAction.getRecommendations() shouldBe listOf(SecurityActionRecommendation.BACKUP_EAK)
    backupMissingAction.category() shouldBe SecurityActionCategory.RECOVERY
  }

  test("Test EekCloudBackupHealthAction with disabled feature") {
    val action = EekBackupHealthAction(
      EekBackupStatus.Healthy(lastUploaded = Clock.System.now()),
      FunctionalityFeatureStates.FeatureState.Unavailable
    )
    action.getRecommendations().shouldBeEmpty()
    action.category() shouldBe SecurityActionCategory.RECOVERY
    action.state() shouldBe SecurityActionState.Disabled
  }
})
