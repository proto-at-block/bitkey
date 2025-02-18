package build.wallet.ui.components.forms

import androidx.compose.runtime.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.forms.TextFieldOverflowCharacteristic.*
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.button.ButtonModel.Treatment.Secondary
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun TextFieldNoTextAndNoFocusPreview() {
  PreviewWalletTheme {
    TextField(
      placeholderText = "Email Address",
      value = TextFieldValue(""),
      onValueChange = {}
    )
  }
}

@Preview
@Composable
internal fun TextFieldNoTextWithTrailingButtonPreview() {
  PreviewWalletTheme {
    TextField(
      placeholderText = "Email Address",
      value = TextFieldValue(""),
      onValueChange = {},
      textFieldOverflowCharacteristic = Multiline,
      trailingButtonModel =
        ButtonModel(
          text = "Paste",
          leadingIcon = Icon.SmallIconClipboard,
          treatment = Secondary,
          size = Compact,
          onClick = StandardClick {}
        )
    )
  }
}

@Preview
@Composable
fun TextFieldWithTextAndNoFocusPreview() {
  PreviewWalletTheme {
    TextField(
      placeholderText = "Email Address",
      value = TextFieldValue("asdf@block.xyz"),
      onValueChange = {}
    )
  }
}

@Preview
@Composable
internal fun TextFieldWithOverflowFitText() {
  PreviewWalletTheme {
    TextField(
      placeholderText = "Bitcoin Address",
      value = TextFieldValue("bc1p5d7rjq7g6rdk2yhzks9smlaqtedr4dekq08ge8ztwac72sfr9rusxg3297"),
      textFieldOverflowCharacteristic =
        Resize(
          maxLines = 1,
          minFontSize = TextUnit(10f, TextUnitType.Sp),
          scaleFactor = 0.9f
        ),
      onValueChange = {}
    )
  }
}

@Preview
@Composable
internal fun TextFieldWithOverflowMultilineText() {
  PreviewWalletTheme {
    TextField(
      placeholderText = "Bitcoin Address",
      value = TextFieldValue("bc1p5d7rjq7g6rdk2yhzks9smlaqtedr4dekq08ge8ztwac72sfr9rusxg3297"),
      textFieldOverflowCharacteristic = Multiline,
      labelType = LabelType.Body2Mono,
      onValueChange = {}
    )
  }
}

@Preview
@Composable
fun TextFieldWithInviteCodeTransformation() {
  PreviewWalletTheme {
    TextField(
      placeholderText = "",
      value = TextFieldValue("xxxxxxxx"),
      onValueChange = {},
      visualTransformation = InviteCodeTransformation
    )
  }
}
