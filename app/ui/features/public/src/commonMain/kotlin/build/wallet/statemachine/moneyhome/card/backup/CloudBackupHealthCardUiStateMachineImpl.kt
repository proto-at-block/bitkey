package build.wallet.statemachine.moneyhome.card.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import build.wallet.availability.AppFunctionalityService
import build.wallet.availability.FunctionalityFeatureStates.FeatureState.Unavailable
import build.wallet.cloud.backup.CloudBackupHealthRepository
import build.wallet.cloud.backup.health.MobileKeyBackupStatus
import build.wallet.cloud.backup.health.MobileKeyBackupStatus.ProblemWithBackup.NoCloudAccess
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.moneyhome.card.CardModel

@BitkeyInject(ActivityScope::class)
class CloudBackupHealthCardUiStateMachineImpl(
  private val cloudBackupHealthRepository: CloudBackupHealthRepository,
  private val appFunctionalityService: AppFunctionalityService,
) : CloudBackupHealthCardUiStateMachine {
  @Composable
  override fun model(props: CloudBackupHealthCardUiProps): CardModel? {
    val appFunctionalityStatus by remember { appFunctionalityService.status }.collectAsState()
    if (appFunctionalityStatus.featureStates.cloudBackupHealth == Unavailable) return null

    val mobileKeyBackupStatus by
      remember { cloudBackupHealthRepository.mobileKeyBackupStatus() }.collectAsState()

    return mobileKeyBackupStatus?.let { status ->
      when (status) {
        is MobileKeyBackupStatus.ProblemWithBackup ->
          CloudBackupHealthCardModel(
            title = when (status) {
              NoCloudAccess -> "Problem with ${cloudServiceProvider().name}\naccount access"
              else -> "Mobile Key backup missing"
            },
            onActionClick = { props.onActionClick(status) }
          )
        else -> null
      }
    }
  }
}
