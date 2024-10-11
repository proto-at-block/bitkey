package build.wallet.ui.components.fee

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign.Companion.Center
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment.Secondary
import build.wallet.ui.components.list.ListItem
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun FeeOption(
  modifier: Modifier = Modifier,
  leadingText: String,
  trailingPrimaryText: String,
  trailingSecondaryText: String,
  selected: Boolean,
  enabled: Boolean,
  infoText: String?,
  onClick: (() -> Unit)?,
) {
  Box(
    modifier =
      modifier
        .border(
          width = 2.dp,
          color =
            if (selected && enabled) {
              WalletTheme.colors.foreground
            } else {
              WalletTheme.colors.foreground10
            },
          shape = RoundedCornerShape(20.dp)
        )
        .thenIf(enabled) {
          Modifier.selectable(
            selected = selected,
            onClick = { onClick?.invoke() },
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.RadioButton
          )
        }
        .background(
          color = WalletTheme.colors.containerBackground,
          shape = RoundedCornerShape(20.dp)
        )
  ) {
    Column(
      modifier =
        Modifier.padding(
          start = 20.dp,
          end = 20.dp,
          top = 4.dp,
          bottom = if (!infoText.isNullOrBlank()) 16.dp else 4.dp
        )
    ) {
      ListItem(
        model =
          ListItemModel(
            title = leadingText,
            sideText = trailingPrimaryText,
            secondarySideText = trailingSecondaryText,
            enabled = enabled,
            onClick = onClick
          )
      )
      if (!infoText.isNullOrBlank()) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .background(
                color = WalletTheme.colors.foreground10,
                shape = RoundedCornerShape(12.dp)
              )
              .padding(8.dp),
          contentAlignment = Alignment.Center
        ) {
          Label(
            text = infoText,
            type = LabelType.Body3Medium,
            treatment = Secondary,
            alignment = Center
          )
        }
      }
    }
  }
}

@Preview
@Composable
fun PreviewFeeOptionSelected() {
  FeeOption(
    modifier = Modifier.fillMaxWidth(),
    leadingText = "Priority",
    trailingPrimaryText = "~30 mins",
    trailingSecondaryText = "$0.33 (1,086 sats)",
    selected = true,
    enabled = true,
    infoText = "",
    onClick = {}
  )
}

@Preview
@Composable
fun PreviewFeeOptionNotSelected() {
  FeeOption(
    modifier = Modifier.fillMaxWidth(),
    leadingText = "Priority",
    trailingPrimaryText = "~30 mins",
    trailingSecondaryText = "$0.33 (1,086 sats)",
    selected = false,
    enabled = true,
    infoText = "",
    onClick = {}
  )
}

@Preview
@Composable
fun PreviewFeeOptionDisabled() {
  FeeOption(
    modifier = Modifier.fillMaxWidth(),
    leadingText = "Priority",
    trailingPrimaryText = "~30 mins",
    trailingSecondaryText = "$0.33 (1,086 sats)",
    selected = false,
    enabled = false,
    infoText = "Not enough balance",
    onClick = {}
  )
}

@Preview
@Composable
internal fun PreviewFeeOptionEnabledWithInfoText() {
  FeeOption(
    modifier = Modifier.fillMaxWidth(),
    leadingText = "Priority",
    trailingPrimaryText = "~30 mins",
    trailingSecondaryText = "$0.33 (1,086 sats)",
    selected = false,
    enabled = true,
    infoText = "All fees are equal – we’ve selected the fastest option for you",
    onClick = {}
  )
}
