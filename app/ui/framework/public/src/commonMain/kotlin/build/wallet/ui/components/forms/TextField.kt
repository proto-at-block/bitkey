package build.wallet.ui.components.forms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.Paragraph
import androidx.compose.ui.text.ParagraphIntrinsics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization.Companion.Characters
import androidx.compose.ui.text.input.KeyboardCapitalization.Companion.None
import androidx.compose.ui.text.input.KeyboardCapitalization.Companion.Sentences
import androidx.compose.ui.text.input.KeyboardCapitalization.Companion.Words
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.forms.TextFieldOverflowCharacteristic.*
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.input.TextFieldModel.Capitalization
import build.wallet.ui.model.input.TextFieldModel.KeyboardType.*
import build.wallet.ui.model.input.TextFieldModel.TextTransformation.INVITE_CODE
import build.wallet.ui.model.input.TextFieldModel.TextTransformation.PASSWORD
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

@Suppress("CyclomaticComplexMethod")
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

  // State for managing TextField's text value separate from cursor position
  var textValue by remember(model.value, model.transformation) {
    val initialValue = when (model.transformation) {
      // Remove formatting from invite code to be applied via VisualTransformation
      INVITE_CODE -> model.value.replace("-", "")
      else -> model.value
    }
    mutableStateOf(initialValue)
  }

  // State for managing TextField's cursor position
  var textSelection by remember(model.selectionOverride) {
    mutableStateOf(
      model.selectionOverride
        // Apply the overridden selection from the model if present
        ?.let { TextRange(it.first, it.last) }
        // Otherwise, use value's length as initial.
        ?: TextRange(textValue.length)
    )
  }

  val textState = TextFieldValue(
    text = textValue,
    selection = textSelection
  )

  TextField(
    modifier = modifier,
    placeholderText = model.placeholderText,
    value = textState,
    labelType = labelType,
    onValueChange = { newTextFieldValue ->
      val newValue = when {
        model.transformation == INVITE_CODE && newTextFieldValue.text.contains('-') -> {
          // Remove any user entered hyphens from single character input or pasteboard action
          val newText = newTextFieldValue.text.replace("-", "")
          TextFieldValue(
            text = newText,
            selection = textSelection
              // maintain cursor position if we are ignoring a single new character
              .takeIf { it.start == it.end && it.end != textValue.length }
              // likely pasted complete text, move cursor to end
              ?: TextRange(newText.length)
          )
        }
        else -> newTextFieldValue
      }
      model.maxLength?.let { maxLength ->
        if (newValue.text.length <= maxLength) {
          textValue = newValue.text
          textSelection = newValue.selection
          model.onValueChange(
            newValue.text,
            newValue.selection.start..newValue.selection.end
          )
        }
      } ?: run {
        textValue = newValue.text
        textSelection = newValue.selection
        model.onValueChange(
          newValue.text,
          newValue.selection.start..newValue.selection.end
        )
      }
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
          when (model.capitalization) {
            Capitalization.None -> None
            Capitalization.Characters -> Characters
            Capitalization.Words -> Words
            Capitalization.Sentences -> Sentences
          }
      ),
    keyboardActions =
      KeyboardActions(
        onDone = model.onDone?.let { { it.invoke() } }
      ),
    // While a VisualTransformation is applied, the iOS cursor
    // will not automatically appear with the 'paste' option
    // when selecting a text field.
    // Here we disable any VisualTransformation while the TextField
    // is blank to ensure the option is provided up front.
    // See https://youtrack.jetbrains.com/issue/CMP-4502
    visualTransformation =
      if (textValue.isEmpty()) {
        VisualTransformation.None
      } else {
        when (model.transformation) {
          PASSWORD -> PasswordVisualTransformation()
          INVITE_CODE -> InviteCodeTransformation
          null -> VisualTransformation.None
        }
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
                annotations = emptyList(),
                placeholders = emptyList(),
                density = LocalDensity.current,
                fontFamilyResolver = LocalFontFamilyResolver.current
              ),
            constraints = Constraints(),
            maxLines = textFieldOverflowCharacteristic.maxLines,
            overflow = TextOverflow.Clip
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

@Composable
fun TextFieldWithCharacteristic(
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
    BasicTextField(
      modifier = Modifier.weight(1F),
      value = value,
      onValueChange = onValueChange,
      textStyle = textStyle,
      cursorBrush = SolidColor(WalletTheme.colors.bitkeyPrimary),
      singleLine = textFieldOverflowCharacteristic !is Multiline,
      keyboardOptions = keyboardOptions,
      visualTransformation = visualTransformation,
      keyboardActions = keyboardActions,
      decorationBox = { innerTextField ->
        // Material3 TextField default content padding: 16dp horizontal, 8dp vertical
        Box(
          modifier = Modifier
            .defaultMinSize(minWidth = 280.dp, minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
          contentAlignment = Alignment.CenterStart
        ) {
          // Show placeholder when text is empty
          if (value.text.isEmpty()) {
            Label(
              text = placeholderText,
              type = LabelType.Body2Regular,
              treatment = LabelTreatment.Secondary
            )
          }
          innerTextField()
        }
      }
    )

    trailingButtonModel?.let {
      Button(
        modifier = Modifier.padding(end = 12.dp),
        model = trailingButtonModel
      )
    }
  }
}
