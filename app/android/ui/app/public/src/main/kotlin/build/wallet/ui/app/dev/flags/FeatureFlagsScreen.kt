package build.wallet.ui.app.dev.flags

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.dev.featureFlags.FeatureFlagsBodyModel
import build.wallet.ui.components.list.ListGroup
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory.SwitchAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.switch.SwitchModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
fun FeatureFlagsScreen(model: FeatureFlagsBodyModel) {
  BackHandler(onBack = model.onBack)
  LazyColumn(
    modifier =
      Modifier
        .padding(horizontal = 20.dp)
        .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    item {
      Toolbar(
        model =
          ToolbarModel(
            leadingAccessory = BackAccessory(onClick = model.onBack),
            middleAccessory = ToolbarMiddleAccessoryModel(title = "Feature Flags"),
            trailingAccessory = ToolbarAccessoryModel.ButtonAccessory(
              ButtonModel(
                text = "Reset",
                size = ButtonModel.Size.Compact,
                onClick = StandardClick(model.onReset)
              )
            )
          )
      )
    }
    item {
      Spacer(Modifier.height(24.dp))
    }

    item {
      ListGroup(model = model.flagsModel)
    }
  }
}

@Preview
@Composable
internal fun FeatureFlagsScreenPreview() {
  PreviewWalletTheme {
    FeatureFlagsScreen(
      FeatureFlagsBodyModel(
        flagsModel =
          ListGroupModel(
            header = null,
            style = ListGroupStyle.DIVIDER,
            items =
              immutableListOf(
                ListItemModel(
                  title = "Flag Title",
                  secondaryText = "Flag Description",
                  trailingAccessory =
                    SwitchAccessory(
                      model =
                        SwitchModel(
                          checked = false,
                          onCheckedChange = {}
                        )
                    )
                )
              )
          ),
        onBack = {},
        onReset = {}
      )
    )
  }
}
