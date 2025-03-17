package build.wallet.ui.components.keypad

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.amount.KeypadButton
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.icon.IconStyle
import build.wallet.ui.components.label.Label
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

@Composable
fun Keypad(
  modifier: Modifier = Modifier,
  showDecimal: Boolean,
  onButtonPress: (KeypadButton) -> Unit,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.SpaceAround
  ) {
    KeypadButtonsRow {
      KeypadButton(button = KeypadButton.Digit.One, onClick = onButtonPress)
      KeypadButton(button = KeypadButton.Digit.Two, onClick = onButtonPress)
      KeypadButton(button = KeypadButton.Digit.Three, onClick = onButtonPress)
    }
    KeypadButtonsRow {
      KeypadButton(button = KeypadButton.Digit.Four, onClick = onButtonPress)
      KeypadButton(button = KeypadButton.Digit.Five, onClick = onButtonPress)
      KeypadButton(button = KeypadButton.Digit.Six, onClick = onButtonPress)
    }
    KeypadButtonsRow {
      KeypadButton(button = KeypadButton.Digit.Seven, onClick = onButtonPress)
      KeypadButton(button = KeypadButton.Digit.Eight, onClick = onButtonPress)
      KeypadButton(button = KeypadButton.Digit.Nine, onClick = onButtonPress)
    }
    KeypadButtonsRow {
      KeypadButton(show = showDecimal, button = KeypadButton.Decimal, onClick = onButtonPress)
      KeypadButton(button = KeypadButton.Digit.Zero, onClick = onButtonPress)
      KeypadButton(button = KeypadButton.Delete, onClick = onButtonPress)
    }
  }
}

@Composable
private fun KeypadButtonsRow(buttons: @Composable (RowScope.() -> Unit)) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceAround
  ) {
    buttons()
  }
}

@Composable
private fun RowScope.KeypadButton(
  show: Boolean = true,
  button: KeypadButton,
  onClick: (KeypadButton) -> Unit,
) {
  Box(
    modifier =
      Modifier
        .weight(1F)
        .height(72.dp)
        .clickable(
          interactionSource = MutableInteractionSource(),
          indication = null,
          enabled = show,
          onClick = { onClick(button) }
        ),
    contentAlignment = Alignment.Center
  ) {
    if (show) {
      KeypadButtonContent(button)
    } else {
      // Only hide button content and keep the box to keep keypad layout.
    }
  }
}

@Composable
private fun KeypadButtonContent(button: KeypadButton) {
  when (button) {
    KeypadButton.Decimal -> {
      DecimalIcon()
    }
    KeypadButton.Delete -> {
      DeleteIcon()
    }
    is KeypadButton.Digit -> {
      Label(
        text = button.value.toString(),
        type = LabelType.Keypad
      )
    }
  }
}

@Composable
private fun DeleteIcon() {
  IconImage(
    model =
      IconModel(
        icon = Icon.SmallIconCaretLeft,
        iconSize = IconSize.Keypad
      ),
    style =
      IconStyle(
        color = WalletTheme.colors.primaryIcon
      )
  )
}

@Composable
private fun DecimalIcon() {
  Box(
    modifier =
      Modifier
        .size(6.dp)
        .background(
          color = WalletTheme.colors.primaryIcon,
          shape = CircleShape
        )
  )
}
