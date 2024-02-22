package build.wallet.statemachine.moneyhome.card.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import build.wallet.LoadableValue
import build.wallet.cloud.backup.CloudBackupHealthFeatureFlag
import build.wallet.cloud.backup.CloudBackupHealthRepository
import build.wallet.cloud.backup.health.MobileKeyBackupStatus
import build.wallet.cloud.backup.health.MobileKeyBackupStatus.ProblemWithBackup.NoCloudAccess
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.statemachine.moneyhome.card.CardModel

class CloudBackupHealthCardUiStateMachineImpl(
  private val cloudBackupHealthFeatureFlag: CloudBackupHealthFeatureFlag,
  private val cloudBackupHealthRepository: CloudBackupHealthRepository,
) : CloudBackupHealthCardUiStateMachine {
  @Composable
  override fun model(props: CloudBackupHealthCardUiProps): CardModel? {
    // Do not show the card if the feature flag is off.
    val flagValue = remember { cloudBackupHealthFeatureFlag.flagValue() }.collectAsState()
    if (!flagValue.value.value) return null

    val mobileKeyBackupStatus by
      remember { cloudBackupHealthRepository.mobileKeyBackupStatus() }.collectAsState()

    return when (val statusValue = mobileKeyBackupStatus) {
      is LoadableValue.LoadedValue -> {
        when (val status = statusValue.value) {
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

      else -> null
    }
  }
}
