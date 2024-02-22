package build.wallet.ui.app.platform.permissions

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.platform.permissions.RequestPermissionBodyModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.header.Header
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
fun RequestPermissionScreen(model: RequestPermissionBodyModel) {
  if (model.showingSystemPermission.not()) {
    FormScreen(
      onBack = model.onBack,
      toolbarContent = {
        Toolbar(
          model =
            ToolbarModel(
              leadingAccessory = CloseAccessory(onClick = model.onBack)
            )
        )
      },
      headerContent = {
        Header(
          model = FormHeaderModel(
            headline = model.title,
            subline = model.explanation,
            icon = Icon.LargeIconWarningFilled
          )
        )
      },
      footerContent = {
        Button(
          text = "Request",
          treatment = Primary,
          size = Footer,
          onClick = Click.StandardClick { model.onRequest() }
        )
      }
    )
  }
}

@Preview
@Composable
internal fun PreviewRequestPermissionScreen() {
  PreviewWalletTheme {
    RequestPermissionScreen(
      RequestPermissionBodyModel(
        title = "Requesting Permission",
        explanation = "This permission is needed in order to use this feature to change the world",
        showingSystemPermission = false,
        onBack = {},
        onRequest = {}
      )
    )
  }
}
