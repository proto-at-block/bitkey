package build.wallet.ui.app.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.dev.DebugMenuBodyModel
import build.wallet.ui.components.alertdialog.AlertDialog
import build.wallet.ui.components.list.ListGroup
import build.wallet.ui.components.sheet.Sheet
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.system.BackHandler
import build.wallet.ui.theme.WalletTheme

@Composable
fun DebugMenuScreen(
  modifier: Modifier = Modifier,
  model: DebugMenuBodyModel,
) {
  BackHandler(onBack = model.onBack)
  model.alertModel?.let { alertModel ->
    AlertDialog(model = alertModel)
  }
  Column(
    modifier = modifier
      .background(WalletTheme.colors.background)
      .padding(horizontal = 20.dp)
      .fillMaxSize()
      .verticalScroll(state = rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Toolbar(
      model =
        ToolbarModel(
          leadingAccessory = BackAccessory(onClick = model.onBack),
          middleAccessory = ToolbarMiddleAccessoryModel(model.title)
        )
    )

    Spacer(Modifier.height(24.dp))

    Column {
      model.groups.forEach { group ->
        ListGroup(model = group)
        Spacer(Modifier.height(24.dp))
      }
    }
  }
  if (model.bottomSheetModel != null) {
    Sheet(
      model = model.bottomSheetModel
    )
  }
}
