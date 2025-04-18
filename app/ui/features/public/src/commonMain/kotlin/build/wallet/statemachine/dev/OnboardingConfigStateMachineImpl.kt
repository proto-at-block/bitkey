package build.wallet.statemachine.dev

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import bitkey.account.AccountConfigService
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory.SwitchAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.switch.SwitchModel
import kotlinx.coroutines.launch

@BitkeyInject(ActivityScope::class)
class OnboardingConfigStateMachineImpl(
  private val appVariant: AppVariant,
  private val accountConfigService: AccountConfigService,
) : OnboardingConfigStateMachine {
  @Composable
  override fun model(props: Unit): ListGroupModel? {
    // Do not show this option in Customer builds
    if (appVariant == Customer) return null

    val defaultConfig = remember { accountConfigService.defaultConfig() }
      .collectAsState(initial = null).value ?: return null

    val scope = rememberStableCoroutineScope()

    return ListGroupModel(
      header = "Onboarding",
      style = ListGroupStyle.DIVIDER,
      items = immutableListOf(
        ListItemModel(
          title = "Skip Cloud Backup",
          secondaryText = "New wallet won’t be backed up to cloud",
          trailingAccessory = SwitchAccessory(
            model = SwitchModel(
              checked = defaultConfig.skipCloudBackupOnboarding,
              onCheckedChange = { shouldSkip ->
                scope.launch {
                  accountConfigService.setSkipCloudBackupOnboarding(shouldSkip)
                }
              },
              testTag = "skip-cloud-backup"
            )
          )
        ),
        ListItemModel(
          title = "Skip Notifications",
          secondaryText = "New wallet won’t have notifications set up",
          trailingAccessory = SwitchAccessory(
            model = SwitchModel(
              checked = defaultConfig.skipNotificationsOnboarding,
              onCheckedChange = { shouldSkip ->
                scope.launch {
                  accountConfigService.setSkipNotificationsOnboarding(shouldSkip)
                }
              },
              testTag = "skip-notifications"
            )
          )
        )
      )
    )
  }
}
