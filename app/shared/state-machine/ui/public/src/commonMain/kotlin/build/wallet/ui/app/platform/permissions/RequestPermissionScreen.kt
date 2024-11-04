package build.wallet.ui.app.platform.permissions

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.platform.permissions.RequestPermissionBodyModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.header.Header
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.tooling.PreviewWalletTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun RequestPermissionScreen(
  modifier: Modifier = Modifier,
  model: RequestPermissionBodyModel,
) {
  if (model.showingSystemPermission.not()) {
    FormScreen(
      modifier = modifier,
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
          onClick = StandardClick(model.onRequest)
        )
      }
    )
  }
}

@Preview
@Composable
fun PreviewRequestPermissionScreen() {
  PreviewWalletTheme {
    RequestPermissionScreen(
      model = RequestPermissionBodyModel(
        title = "Requesting Permission",
        explanation = "This permission is needed in order to use this feature to change the world",
        showingSystemPermission = false,
        onBack = {},
        onRequest = {}
      )
    )
  }
}
