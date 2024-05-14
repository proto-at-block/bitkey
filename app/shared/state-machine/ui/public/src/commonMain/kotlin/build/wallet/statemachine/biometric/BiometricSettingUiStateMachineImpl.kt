package build.wallet.statemachine.biometric

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.inappsecurity.BiometricPreference
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.switch.SwitchModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

class BiometricSettingUiStateMachineImpl(
  private val biometricPreference: BiometricPreference,
) : BiometricSettingUiStateMachine {
  @Composable
  override fun model(props: BiometricSettingUiProps): ScreenModel {
    val isEnabled by remember {
      biometricPreference.isEnabled()
    }.collectAsState(false)

    return FormBodyModel(
      toolbar = ToolbarModel(
        leadingAccessory = BackAccessory(props.onBack)
      ),
      header = FormHeaderModel(
        headline = "Face ID", // TODO W-7960 Use the appropriate string for biometrics
        subline = "Unlock the app using fingerprint or facial recognition."
      ),
      mainContentList = immutableListOf(
        FormMainContentModel.ListGroup(
          listGroupModel = ListGroupModel(
            items = immutableListOf(
              ListItemModel(
                title = "Enable Face ID", // TODO W-7960 Use the appropriate string for biometrics
                trailingAccessory = ListItemAccessory.SwitchAccessory(
                  model = SwitchModel(
                    checked = isEnabled,
                    onCheckedChange = {
                      // TODO W-7962 launch NFC here
                    }
                  )
                )
              )
            ),
            style = ListGroupStyle.DIVIDER
          )
        )
      ),
      primaryButton = null,
      onBack = props.onBack,
      id = SettingsEventTrackerScreenId.SETTING_BIOMETRICS
    ).asRootScreen()
  }
}
