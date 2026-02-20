package build.wallet.ui.app.dev

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon.SmallIconCaretDown
import build.wallet.statemachine.dev.DebugMenuBodyModel
import build.wallet.ui.components.alertdialog.AlertDialog
import build.wallet.ui.components.forms.TextField
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.list.ListGroup
import build.wallet.ui.components.sheet.Sheet
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.system.BackHandler
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

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

    Spacer(Modifier.height(16.dp))

    // Search field
    TextField(
      modifier = Modifier.fillMaxWidth(),
      model = TextFieldModel(
        value = model.filterText,
        placeholderText = "Search debug options...",
        onValueChange = { newValue, _ -> model.onFilterChange(newValue) },
        keyboardType = TextFieldModel.KeyboardType.Default,
        focusByDefault = false
      )
    )

    Spacer(Modifier.height(16.dp))

    Column {
      model.groups.forEach { group ->
        val header = group.header
        val isFiltering = model.filterText.isNotBlank()

        if (header != null && !isFiltering) {
          // Collapsible group with header
          val isCollapsed = model.collapsedGroupHeaders.contains(header)
          CollapsibleListGroup(
            group = group,
            isCollapsed = isCollapsed,
            onToggleCollapse = { model.onToggleGroupCollapse(header) }
          )
        } else {
          // Non-collapsible group (no header or filtering active)
          ListGroup(model = group)
        }
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

@Composable
private fun CollapsibleListGroup(
  group: ListGroupModel,
  isCollapsed: Boolean,
  onToggleCollapse: () -> Unit,
) {
  val header = group.header ?: return

  Column {
    // Clickable header with collapse indicator
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onToggleCollapse)
        .padding(vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Label(
        modifier = Modifier.weight(1f),
        text = header,
        type = LabelType.Title3,
        treatment = LabelTreatment.Secondary
      )

      // Animated chevron rotation
      val rotationAngle by animateFloatAsState(
        targetValue = if (isCollapsed) -90f else 0f,
        label = "chevron-rotation"
      )
      Box(
        modifier = Modifier
          .size(24.dp)
          .rotate(rotationAngle),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          icon = SmallIconCaretDown,
          size = IconSize.Small,
          color = WalletTheme.colors.foreground60
        )
      }
    }

    // Animated content visibility
    AnimatedVisibility(
      visible = !isCollapsed,
      enter = expandVertically(),
      exit = shrinkVertically()
    ) {
      // Render group without the header since we already rendered it
      ListGroup(
        model = group.copy(header = null)
      )
    }
  }
}
