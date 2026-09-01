package build.wallet.ui.components.forms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.then
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.forms.TextFieldOverflowCharacteristic.*
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.compose.resId
import build.wallet.ui.compose.resolveTestTag
import build.wallet.ui.compose.textFieldTestTag
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.input.TextFieldModel.Capitalization
import build.wallet.ui.model.input.TextFieldModel.KeyboardType.*
import build.wallet.ui.model.input.TextFieldModel.TextTransformation.INVITE_CODE
import build.wallet.ui.model.input.TextFieldModel.TextTransformation.PASSWORD
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import kotlinx.coroutines.flow.drop

/**
 * Model-driven text field.
 *
 * Input state is owned locally by a [TextFieldState]; the IME edits it directly and
 * Compose Foundation keeps the input connection in sync. The hoisted [TextFieldModel.value]
 * is *not* the source of truth while the user is typing — asynchronous echoes of previously
 * reported edits are ignored via [TextFieldModelSync], and only genuine external changes
 * (formatting, paste buttons, programmatic set/clear) are adopted into local state.
 *
 * This is deliberate: re-initializing field state from every hoisted value round-trip drops
 * rapid keystrokes and can permanently desync some Android IMEs (only the first character
 * ever committing — BKW-580).
 */
@Composable
fun TextField(
  modifier: Modifier = Modifier,
  model: TextFieldModel,
  testTag: String? = null,
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

  fun normalizeModelValue(value: String) =
    when (model.transformation) {
      INVITE_CODE -> value.replace("-", "")
      else -> value
    }

  val state = remember(model.transformation) {
    TextFieldState(
      initialText = normalizeModelValue(model.value),
      initialSelection = model.selectionOverride
        ?.let { TextRange(it.first, it.last) }
        ?: TextRange(normalizeModelValue(model.value).length)
    )
  }
  val sync = remember(state) { TextFieldModelSync(state.text.toString()) }

  // Report local (user/IME) edits upward. Runs after composition, observing snapshot state.
  val currentOnValueChange by rememberUpdatedState(model.onValueChange)
  LaunchedEffect(state, sync) {
    snapshotFlow { state.text.toString() to state.selection }
      .drop(1) // Skip initial value; only user edits and adoptions flow through here.
      .collect { (text, selection) ->
        if (sync.onLocalTextChanged(text)) {
          currentOnValueChange(text, selection.start..selection.end)
        }
      }
  }

  // Adopt genuine external model changes (formatting, paste button, programmatic set/clear).
  LaunchedEffect(state, sync, model.value, model.selectionOverride) {
    reconcileExternalUpdate(
      state = state,
      sync = sync,
      incoming = normalizeModelValue(model.value),
      selectionOverride = model.selectionOverride
    )
  }

  val inputTransformation = remember(model.transformation, model.maxLength) {
    model.toInputTransformation()
  }

  val outputTransformation = when (model.transformation) {
    INVITE_CODE -> InviteCodeOutputTransformation
    else -> null
  }

  TextField(
    modifier = modifier,
    placeholderText = model.placeholderText,
    state = state,
    testTag = resolveTestTag(testTag ?: model.testTag, textFieldTestTag(model.placeholderText)),
    labelType = labelType,
    focusRequester = focusRequester,
    textFieldOverflowCharacteristic = textFieldOverflowCharacteristic,
    trailingButtonModel = trailingButtonModel,
    keyboardOptions = model.toKeyboardOptions(),
    onKeyboardAction = model.onDone?.let { onDone ->
      KeyboardActionHandler { onDone() }
    },
    inputTransformation = inputTransformation,
    outputTransformation = outputTransformation,
    secure = model.transformation == PASSWORD
  )
}

/**
 * Applies an external model update to the locally-owned [state]:
 * - Genuine corrections (per [TextFieldModelSync.shouldAdoptExternal]) replace text and selection.
 * - Selection-only corrections (echoed text but a new [selectionOverride]) reposition the cursor
 *   without touching text.
 * - Stale echoes are ignored entirely.
 */
private fun reconcileExternalUpdate(
  state: TextFieldState,
  sync: TextFieldModelSync,
  incoming: String,
  selectionOverride: IntRange?,
) {
  val selection = selectionOverride
    ?.let { TextRange(it.first.coerceIn(0, incoming.length), it.last.coerceIn(0, incoming.length)) }
  if (sync.shouldAdoptExternal(incoming)) {
    state.edit {
      replace(0, length, incoming)
      this.selection = selection ?: TextRange(incoming.length)
    }
  } else if (selection != null && selection != state.selection) {
    state.edit { this.selection = selection }
  }
}

/** Builds the [InputTransformation] chain for this model: hyphen stripping and max length. */
private fun TextFieldModel.toInputTransformation(): InputTransformation? {
  var transformation: InputTransformation? = when (transformation) {
    // Strip user-entered or pasted hyphens; formatting is applied by OutputTransformation.
    INVITE_CODE -> StripCharacterTransformation('-')
    else -> null
  }
  maxLength?.let { max ->
    transformation = transformation?.then(TruncateToMaxLengthTransformation(max))
      ?: TruncateToMaxLengthTransformation(max)
  }
  return transformation
}

/**
 * Truncates (rather than rejects) input that exceeds [maxLength], so that pasting long
 * text keeps the first [maxLength] characters instead of doing nothing. This intentionally
 * differs from [InputTransformation.maxLength], which reverts the whole edit.
 */
private data class TruncateToMaxLengthTransformation(val maxLength: Int) : InputTransformation {
  override fun TextFieldBuffer.transformInput() {
    if (length > maxLength) {
      delete(maxLength, length)
    }
  }
}

private fun TextFieldModel.toKeyboardOptions() =
  KeyboardOptions(
    keyboardType = when (keyboardType) {
      Default -> KeyboardType.Text
      Email -> KeyboardType.Email
      Decimal -> KeyboardType.Decimal
      Number -> KeyboardType.Number
      Phone -> KeyboardType.Phone
      Uri -> KeyboardType.Uri
    },
    autoCorrectEnabled = enableAutoCorrect,
    capitalization = when (capitalization) {
      Capitalization.None -> None
      Capitalization.Characters -> Characters
      Capitalization.Words -> Words
      Capitalization.Sentences -> Sentences
    }
  )

/** Removes all occurrences of [character] from any edit (typed or pasted). */
private data class StripCharacterTransformation(val character: Char) : InputTransformation {
  override fun TextFieldBuffer.transformInput() {
    var index = 0
    while (index < length) {
      if (asCharSequence()[index] == character) {
        delete(index, index + 1)
      } else {
        index++
      }
    }
  }
}

/** Renders invite codes as 4-character hyphen-separated groups, ex: `xxxx-xxxx-xxxx`. */
internal object InviteCodeOutputTransformation : OutputTransformation {
  override fun TextFieldBuffer.transformOutput() {
    var insertAt = 4
    while (insertAt < length) {
      insert(insertAt, "-")
      insertAt += 5
    }
  }
}

@Composable
fun TextField(
  placeholderText: String,
  state: TextFieldState,
  modifier: Modifier = Modifier,
  focusRequester: FocusRequester = remember { FocusRequester() },
  testTag: String? = null,
  labelType: LabelType = LabelType.Body2Regular,
  textFieldOverflowCharacteristic: TextFieldOverflowCharacteristic = Truncate,
  trailingButtonModel: ButtonModel? = null,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  onKeyboardAction: KeyboardActionHandler? = null,
  inputTransformation: InputTransformation? = null,
  outputTransformation: OutputTransformation? = null,
  secure: Boolean = false,
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
                text = state.text.toString(),
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
          state = state,
          testTag = testTag,
          textStyle = textStyle,
          textFieldOverflowCharacteristic = textFieldOverflowCharacteristic,
          trailingButtonModel = trailingButtonModel,
          keyboardOptions = keyboardOptions,
          onKeyboardAction = onKeyboardAction,
          inputTransformation = inputTransformation,
          outputTransformation = outputTransformation,
          secure = secure
        )
      }
    }
    else -> {
      TextFieldWithCharacteristic(
        modifier = modifier.focusRequester(focusRequester),
        placeholderText = placeholderText,
        state = state,
        testTag = testTag,
        textStyle = textStyle,
        textFieldOverflowCharacteristic = textFieldOverflowCharacteristic,
        trailingButtonModel = trailingButtonModel,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        inputTransformation = inputTransformation,
        outputTransformation = outputTransformation,
        secure = secure
      )
    }
  }
}

@Composable
fun TextFieldWithCharacteristic(
  placeholderText: String,
  state: TextFieldState,
  textStyle: TextStyle,
  textFieldOverflowCharacteristic: TextFieldOverflowCharacteristic,
  trailingButtonModel: ButtonModel?,
  keyboardOptions: KeyboardOptions,
  modifier: Modifier = Modifier,
  testTag: String? = null,
  onKeyboardAction: KeyboardActionHandler? = null,
  inputTransformation: InputTransformation? = null,
  outputTransformation: OutputTransformation? = null,
  secure: Boolean = false,
) {
  val shape =
    RoundedCornerShape(
      size = 8.dp
    )
  val backgroundColor =
    when (LocalTheme.current) {
      Theme.LIGHT -> WalletTheme.colors.subtleBackground
      else -> WalletTheme.colors.foreground10
    }

  Row(
    modifier =
      modifier
        .resId(resolveTestTag(testTag, textFieldTestTag(placeholderText)))
        .clip(shape)
        .background(color = backgroundColor),
    verticalAlignment = Alignment.CenterVertically
  ) {
    val decorator = TextFieldDecorator { innerTextField ->
      // Material3 TextField default content padding: 16dp horizontal, 8dp vertical
      Box(
        modifier = Modifier
          .defaultMinSize(minWidth = 280.dp, minHeight = 56.dp)
          .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
      ) {
        // Show placeholder when text is empty
        if (state.text.isEmpty()) {
          Label(
            text = placeholderText,
            type = LabelType.Body2Regular,
            treatment = LabelTreatment.Secondary
          )
        }
        innerTextField()
      }
    }

    if (secure) {
      BasicSecureTextField(
        modifier = Modifier.weight(1F),
        state = state,
        textStyle = textStyle,
        cursorBrush = SolidColor(WalletTheme.colors.bitkeyPrimary),
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        inputTransformation = inputTransformation,
        decorator = decorator
      )
    } else {
      BasicTextField(
        modifier = Modifier.weight(1F),
        state = state,
        textStyle = textStyle,
        cursorBrush = SolidColor(WalletTheme.colors.bitkeyPrimary),
        lineLimits = when (textFieldOverflowCharacteristic) {
          is Multiline -> TextFieldLineLimits.MultiLine()
          else -> TextFieldLineLimits.SingleLine
        },
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        inputTransformation = inputTransformation,
        outputTransformation = outputTransformation,
        decorator = decorator
      )
    }

    trailingButtonModel?.let {
      Button(
        modifier = Modifier.padding(end = 12.dp),
        model = trailingButtonModel
      )
    }
  }
}
