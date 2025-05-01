package bitkey.ui.screens.demo

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import bitkey.ui.screens.demo.DemoCodeTrackerScreenId.DEMO_CODE_CONFIG
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.BodyModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.alertdialog.AlertDialog
import build.wallet.ui.components.switch.SwitchCard
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.ui.model.switch.SwitchCardModel
import build.wallet.ui.model.switch.SwitchModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

data object DemoModeDisabledScreen : Screen

@BitkeyInject(ActivityScope::class)
class DemoModeDisabledScreenPresenter : ScreenPresenter<DemoModeDisabledScreen> {
  @Composable
  override fun model(
    navigator: Navigator,
    screen: DemoModeDisabledScreen,
  ) = EnableDemoModeBodyModel(
    onBack = { navigator.exit() },
    switchIsChecked = false,
    onSwitchCheckedChange = { navigator.goTo(DemoModeCodeEntryScreen) },
    disableAlertModel = null
  ).asRootScreen()
}

data class EnableDemoModeBodyModel(
  override val onBack: () -> Unit,
  val switchIsChecked: Boolean,
  val disableAlertModel: ButtonAlertModel?,
  val onSwitchCheckedChange: (Boolean) -> Unit,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? =
    EventTrackerScreenInfo(DEMO_CODE_CONFIG),
) : BodyModel() {
  @Composable
  override fun render(modifier: Modifier) {
    val onBack = disableAlertModel?.onDismiss ?: onBack
    FormScreen(
      modifier = modifier,
      onBack = onBack,
      toolbarContent = {
        Toolbar(ToolbarModel(leadingAccessory = BackAccessory(onBack)))
      },
      mainContent = {
        val switchCardModel = SwitchCardModel(
          title = "Enable demo mode",
          subline = "Demo mode enables you to test the app without having a physical hardware device.",
          switchModel = SwitchModel(
            checked = switchIsChecked,
            onCheckedChange = onSwitchCheckedChange
          ),
          actionRows = emptyImmutableList()
        )
        SwitchCard(model = switchCardModel)

        disableAlertModel?.let { alertModel ->
          AlertDialog(alertModel)
        }
      }
    )
  }
}
