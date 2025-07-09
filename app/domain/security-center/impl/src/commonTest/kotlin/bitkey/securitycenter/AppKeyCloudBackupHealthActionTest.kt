package bitkey.securitycenter

import build.wallet.availability.FunctionalityFeatureStates.FeatureState.Available
import build.wallet.availability.FunctionalityFeatureStates.FeatureState.Unavailable
import build.wallet.cloud.backup.health.AppKeyBackupStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock

class AppKeyCloudBackupHealthActionTest : FunSpec({
  test("Test AppKeyCloudBackupHealthAction functionality") {
    val healthyAction = AppKeyBackupHealthAction(
      AppKeyBackupStatus.Healthy(lastUploaded = Clock.System.now()),
      featureState = Available
    )
    healthyAction.getRecommendations().shouldBeEmpty()
    healthyAction.category() shouldBe SecurityActionCategory.RECOVERY
    healthyAction.state() shouldBe SecurityActionState.Secure

    val backupMissingAction = AppKeyBackupHealthAction(
      AppKeyBackupStatus.ProblemWithBackup.BackupMissing,
      featureState = Available
    )
    backupMissingAction.getRecommendations() shouldBe listOf(SecurityActionRecommendation.BACKUP_MOBILE_KEY)
    backupMissingAction.category() shouldBe SecurityActionCategory.RECOVERY
    backupMissingAction.state() shouldBe SecurityActionState.HasCriticalActions

    val backupDisabledAction = AppKeyBackupHealthAction(
      AppKeyBackupStatus.ProblemWithBackup.BackupMissing,
      featureState = Unavailable
    )
    backupDisabledAction.getRecommendations() shouldBe listOf(SecurityActionRecommendation.BACKUP_MOBILE_KEY)
    backupDisabledAction.category() shouldBe SecurityActionCategory.RECOVERY
    backupDisabledAction.state() shouldBe SecurityActionState.Disabled
  }
})
