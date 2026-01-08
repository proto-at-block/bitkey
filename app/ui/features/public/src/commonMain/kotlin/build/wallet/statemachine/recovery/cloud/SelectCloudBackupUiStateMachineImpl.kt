package build.wallet.statemachine.recovery.cloud

import androidx.compose.runtime.*
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.time.DateTimeFormatter
import build.wallet.time.TimeZoneProvider
import kotlinx.collections.immutable.toImmutableList

/**
 * URL for the help article explaining shared cloud accounts and backup selection.
 */
private const val MULTIPLE_CLOUD_BACKUPS_HELP_URL = "https://bitkey.world/hc/multiple-cloud-backups"

@BitkeyInject(ActivityScope::class)
class SelectCloudBackupUiStateMachineImpl(
  private val inAppBrowserNavigator: InAppBrowserNavigator,
  private val dateTimeFormatter: DateTimeFormatter,
  private val timeZoneProvider: TimeZoneProvider,
) : SelectCloudBackupUiStateMachine {
  @Composable
  override fun model(props: SelectCloudBackupUiProps): ScreenModel {
    var showLearnMoreWebview by remember { mutableStateOf(false) }

    return when {
      showLearnMoreWebview ->
        InAppBrowserModel(
          open = {
            inAppBrowserNavigator.open(
              url = MULTIPLE_CLOUD_BACKUPS_HELP_URL,
              onClose = {
                showLearnMoreWebview = false
              }
            )
          }
        ).asModalScreen()

      else ->
        SelectCloudBackupBodyModel(
          backupItems = props.backups.map { backup ->
            formatCloudBackupItemModel(
              backup = backup,
              dateTimeFormatter = dateTimeFormatter,
              timeZone = timeZoneProvider.current()
            )
          }.toImmutableList(),
          onBackupSelected = props.onBackupSelected,
          onLearnMoreClick = { showLearnMoreWebview = true },
          onBack = props.onBack
        ).asRootScreen()
    }
  }
}
