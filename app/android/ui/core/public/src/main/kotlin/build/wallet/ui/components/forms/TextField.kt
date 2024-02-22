package build.wallet.ui.components.forms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.Paragraph
import androidx.compose.ui.text.ParagraphIntrinsics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization.Companion.None
import androidx.compose.ui.text.input.KeyboardCapitalization.Companion.Words
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.forms.TextFieldOverflowCharacteristic.Multiline
import build.wallet.ui.components.forms.TextFieldOverflowCharacteristic.Resize
import build.wallet.ui.components.forms.TextFieldOverflowCharacteristic.Truncate
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.button.ButtonModel.Treatment.Secondary
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.input.TextFieldModel.KeyboardType.Decimal
import build.wallet.ui.model.input.TextFieldModel.KeyboardType.Default
import build.wallet.ui.model.input.TextFieldModel.KeyboardType.Email
import build.wallet.ui.model.input.TextFieldModel.KeyboardType.Number
import build.wallet.ui.model.input.TextFieldModel.KeyboardType.Phone
import build.wallet.ui.model.input.TextFieldModel.KeyboardType.Uri
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme
import androidx.compose.material3.TextField as MaterialTextField

@Composable
fun TextField(
  modifier: Modifier = Modifier,
  model: TextFieldModel,
  labelType: LabelType = LabelType.Body2Regular,
  textFieldOverflowCharacteristic: TextFieldOverflowCharacteristic = Truncate,
  trailingButtonModel: ButtonModel? = null,
) {
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect("request-default-focus") {
    if (model.focusByDefault && focusRequester.captureFocus()) {
      focusRequester.requestFocus()
    }
  }

  // State for managing TextField's text value and cursor position
  var textState by remember(model.value, model.selectionOverride) {
    mutableStateOf(
      TextFieldValue(
        text = model.value,
        selection =
          model.selectionOverride
            // Apply the overridden selection from the model if present
            ?.let { TextRange(it.first, it.last) }
            // Otherwise, use value's length as initial.
            ?: TextRange(model.value.length)
      )
    )
  }

  TextField(
    modifier = modifier,
    placeholderText = model.placeholderText,
    value = textState,
    labelType = labelType,
    onValueChange = { newTextFieldValue ->
      textState = newTextFieldValue
      model.onValueChange(
        newTextFieldValue.text,
        newTextFieldValue.selection.start..newTextFieldValue.selection.end
      )
    },
    focusRequester = focusRequester,
    textFieldOverflowCharacteristic = textFieldOverflowCharacteristic,
    trailingButtonModel = trailingButtonModel,
    keyboardOptions =
      KeyboardOptions(
        keyboardType =
          when (model.keyboardType) {
            Default -> KeyboardType.Text
            Email -> KeyboardType.Email
            Decimal -> KeyboardType.Decimal
            Number -> KeyboardType.Number
            Phone -> KeyboardType.Phone
            Uri -> KeyboardType.Uri
          },
        autoCorrect = model.enableAutoCorrect,
        capitalization =
          when (model.enableWordAutoCapitalization) {
            true -> Words
            false -> None
          }
      ),
    keyboardActions =
      KeyboardActions(
        onDone = model.onDone?.let { { it.invoke() } }
      ),
    visualTransformation =
      when (model.masksText) {
        true -> PasswordVisualTransformation()
        false -> VisualTransformation.None
      }
  )
}

@Composable
fun TextField(
  modifier: Modifier = Modifier,
  focusRequester: FocusRequester = remember { FocusRequester() },
  placeholderText: String,
  value: TextFieldValue,
  labelType: LabelType = LabelType.Body2Regular,
  textFieldOverflowCharacteristic: TextFieldOverflowCharacteristic = Truncate,
  trailingButtonModel: ButtonModel? = null,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  onValueChange: (TextFieldValue) -> Unit,
) {
  var textStyle =
    WalletTheme.labelStyle(
      type = labelType,
      treatment = LabelTreatment.Primary
    )

  when (textFieldOverflowCharacteristic) {
    // If TextFieldOverflowCharacteristic is Resize, we want to wrap our TextField in
    // BoxWithConstraints so we can use it to compute paragraph intrinsics to
    is Resize -> {
      BoxWithConstraints {
        val calculateParagraph = @Composable {
          Paragraph(
            paragraphIntrinsics =
              ParagraphIntrinsics(
                text = value.text,
                style = textStyle,
                spanStyles = emptyList(),
                placeholders = emptyList(),
                density = LocalDensity.current,
                fontFamilyResolver = LocalFontFamilyResolver.current
              ),
            constraints = Constraints(),
            maxLines = textFieldOverflowCharacteristic.maxLines,
            ellipsis = false
          )
        }

        var intrinsics = calculateParagraph()
        with(LocalDensity.current) {
          while (
            (intrinsics.width.toDp() > maxWidth || intrinsics.didExceedMaxLines) &&
            textStyle.fontSize >= textFieldOverflowCharacteristic.minFontSize
          ) {
            textStyle =
              textStyle.copy(
                fontSize = textStyle.fontSize * textFieldOverflowCharacteristic.scaleFactor
              )
            intrinsics = calculateParagraph()
          }
        }

        TextFieldWithCharacteristic(
          modifier = modifier.focusRequester(focusRequester),
          placeholderText = placeholderText,
          value = value,
          textStyle = textStyle,
          textFieldOverflowCharacteristic = textFieldOverflowCharacteristic,
          trailingButtonModel = trailingButtonModel,
          keyboardOptions = keyboardOptions,
          keyboardActions = keyboardActions,
          visualTransformation = visualTransformation,
          onValueChange = onValueChange
        )
      }
    }
    else -> {
      TextFieldWithCharacteristic(
        modifier = modifier.focusRequester(focusRequester),
        placeholderText = placeholderText,
        value = value,
        textStyle = textStyle,
        textFieldOverflowCharacteristic = textFieldOverflowCharacteristic,
        trailingButtonModel = trailingButtonModel,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        onValueChange = onValueChange
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TextFieldWithCharacteristic(
  modifier: Modifier = Modifier,
  placeholderText: String,
  value: TextFieldValue,
  textStyle: TextStyle,
  textFieldOverflowCharacteristic: TextFieldOverflowCharacteristic,
  trailingButtonModel: ButtonModel?,
  keyboardOptions: KeyboardOptions,
  keyboardActions: KeyboardActions,
  visualTransformation: VisualTransformation,
  onValueChange: (TextFieldValue) -> Unit,
) {
  Row(
    modifier =
      modifier
        .clip(RoundedCornerShape(size = 32.dp))
        .background(color = WalletTheme.colors.foreground10),
    verticalAlignment = Alignment.CenterVertically
  ) {
    MaterialTextField(
      modifier = Modifier.weight(1F),
      value = value,
      onValueChange = onValueChange,
      textStyle = textStyle,
      placeholder = {
        Label(
          text = placeholderText,
          type = LabelType.Body2Regular,
          treatment = LabelTreatment.Secondary
        )
      },
      singleLine = textFieldOverflowCharacteristic !is Multiline,
      colors =
        TextFieldDefaults.textFieldColors(
          containerColor = WalletTheme.colors.foreground10,
          cursorColor = WalletTheme.colors.primary,
          focusedIndicatorColor = Color.Unspecified,
          unfocusedIndicatorColor = Color.Unspecified
        ),
      keyboardOptions = keyboardOptions,
      visualTransformation = visualTransformation,
      keyboardActions = keyboardActions
    )

    trailingButtonModel?.let {
      Button(
        modifier = Modifier.padding(end = 12.dp),
        model = trailingButtonModel
      )
    }
  }
}

@Preview
@Composable
internal fun TextFieldNoTextAndNoFocusPreview() {
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
          onClick = Click.StandardClick { }
        )
    )
  }
}

@Preview
@Composable
internal fun TextFieldWithTextAndNoFocusPreview() {
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
