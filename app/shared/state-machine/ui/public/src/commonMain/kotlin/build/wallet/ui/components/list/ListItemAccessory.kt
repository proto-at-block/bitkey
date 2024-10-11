package build.wallet.ui.components.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.switch.Switch
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemAccessory.ButtonAccessory
import build.wallet.ui.model.list.ListItemAccessory.CircularCharacterAccessory
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.list.ListItemAccessory.SwitchAccessory
import build.wallet.ui.model.list.ListItemAccessory.TextAccessory
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

@Composable
internal fun ListItemAccessory(model: ListItemAccessory) {
  when (model) {
    is IconAccessory ->
      IconImage(
        modifier =
          Modifier
            .padding(model.iconPadding?.dp ?: 0.dp)
            .let { modifier ->
              model.onClick?.let {
                modifier.clickable(
                  onClick = it
                )
              } ?: modifier
            },
        model = model.model
      )

    is SwitchAccessory -> Switch(model.model)
    is ButtonAccessory -> Button(model.model)
    is TextAccessory ->
      Label(
        text = model.text,
        modifier = Modifier.padding(end = 12.dp),
        type = LabelType.Body2Regular
      )
    is CircularCharacterAccessory ->
      CircularCharacterAccessory(model)
  }
}

@Composable
private fun CircularCharacterAccessory(model: CircularCharacterAccessory) {
  Box(
    modifier =
      Modifier
        .padding(end = 4.dp)
  ) {
    Box(
      modifier =
        Modifier
          .size(24.dp)
          .background(
            color = WalletTheme.colors.foreground10,
            shape = CircleShape
          ),
      contentAlignment = Alignment.Center
    ) {
      Label(
        text = model.character.toString(),
        type = LabelType.Label3
      )
    }
  }
}
