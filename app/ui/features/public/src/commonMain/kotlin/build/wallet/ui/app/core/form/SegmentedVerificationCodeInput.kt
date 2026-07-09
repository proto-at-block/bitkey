package build.wallet.ui.app.core.form

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization.Companion.Characters
import androidx.compose.ui.text.input.KeyboardCapitalization.Companion.None
import androidx.compose.ui.text.input.KeyboardCapitalization.Companion.Sentences
import androidx.compose.ui.text.input.KeyboardCapitalization.Companion.Words
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.compose.resId
import build.wallet.ui.compose.resolveTestTag
import build.wallet.ui.compose.textFieldTestTag
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.input.TextFieldModel.Capitalization
import build.wallet.ui.model.input.TextFieldModel.KeyboardType.Decimal
import build.wallet.ui.model.input.TextFieldModel.KeyboardType.Default
import build.wallet.ui.model.input.TextFieldModel.KeyboardType.Email
import build.wallet.ui.model.input.TextFieldModel.KeyboardType.Number
import build.wallet.ui.model.input.TextFieldModel.KeyboardType.Phone
import build.wallet.ui.model.input.TextFieldModel.KeyboardType.Uri
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

private val VERIFICATION_CODE_CELL_SHAPE = RoundedCornerShape(8.dp)
private val VERIFICATION_CODE_CELL_MAX_WIDTH = 50.dp
private val VERIFICATION_CODE_CELL_MAX_HEIGHT = 60.dp
private val VERIFICATION_CODE_CELL_SPACING = 4.dp
private val VERIFICATION_CODE_SEPARATOR_WIDTH = 12.dp
private const val VERIFICATION_CODE_SEPARATOR_GROUP_SIZE = 3
private const val VERIFICATION_DIGIT_POSITION_ANIMATION_DURATION_MS = 220
private const val VERIFICATION_DIGIT_VISIBILITY_ANIMATION_DURATION_MS = 140
private const val VERIFICATION_CODE_CELL_HEIGHT_TO_WIDTH_RATIO = 60f / 50f
private const val VERIFICATION_DIGIT_ENTRY_TRANSLATION_RATIO = 16f / 68f

@Composable
internal fun SegmentedVerificationCodeInput(
  model: TextFieldModel,
  expectedCodeLength: Int,
  modifier: Modifier = Modifier,
  testTag: String? = null,
) {
  val focusRequester = remember { FocusRequester() }
  val keyboardController = LocalSoftwareKeyboardController.current
  val requestFocusAndShowKeyboard = {
    focusRequester.requestFocus()
    keyboardController?.show()
  }

  LaunchedEffect("request-default-focus") {
    if (model.focusByDefault) {
      requestFocusAndShowKeyboard()
    }
  }

  var textValue by remember(model.value, expectedCodeLength) {
    mutableStateOf(
      sanitizeVerificationCodeInput(
        rawValue = model.value,
        expectedCodeLength = expectedCodeLength
      )
    )
  }

  val backgroundColor =
    when (LocalTheme.current) {
      Theme.LIGHT -> WalletTheme.colors.subtleBackground
      else -> WalletTheme.colors.foreground10
    }

  val activeIndex = textValue.length.coerceAtMost(expectedCodeLength - 1)
  val separatorIndex =
    expectedCodeLength.takeIf { it == VERIFICATION_CODE_SEPARATOR_GROUP_SIZE * 2 }
      ?.let { it / 2 }

  BoxWithConstraints(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable { requestFocusAndShowKeyboard() }
  ) {
    val itemCount = expectedCodeLength + if (separatorIndex != null) 1 else 0
    val totalHorizontalSpacing = VERIFICATION_CODE_CELL_SPACING * (itemCount - 1)
    val separatorWidth = if (separatorIndex != null) VERIFICATION_CODE_SEPARATOR_WIDTH else 0.dp
    val availableWidthForCells =
      (maxWidth - totalHorizontalSpacing - separatorWidth).coerceAtLeast(0.dp)
    val cellWidth =
      (availableWidthForCells / expectedCodeLength).coerceAtMost(VERIFICATION_CODE_CELL_MAX_WIDTH)
    val cellHeight =
      (cellWidth * VERIFICATION_CODE_CELL_HEIGHT_TO_WIDTH_RATIO)
        .coerceAtMost(VERIFICATION_CODE_CELL_MAX_HEIGHT)

    BasicTextField(
      modifier =
        Modifier
          .matchParentSize()
          .alpha(0f)
          .focusRequester(focusRequester)
          .resId(resolveTestTag(testTag ?: model.testTag, textFieldTestTag(model.placeholderText))),
      value = TextFieldValue(text = textValue, selection = TextRange(textValue.length)),
      onValueChange = { newValue ->
        val filteredValue =
          nextSanitizedVerificationCodeInput(
            currentValue = textValue,
            rawValue = newValue.text,
            expectedCodeLength = expectedCodeLength
          ) ?: return@BasicTextField

        textValue = filteredValue
        model.onValueChange(
          filteredValue,
          filteredValue.length..filteredValue.length
        )
      },
      textStyle =
        WalletTheme.labelStyle(
          type = LabelType.Body2Mono,
          treatment = LabelTreatment.Primary
        ).copy(color = Color.Transparent),
      cursorBrush = SolidColor(Color.Transparent),
      singleLine = true,
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
          autoCorrectEnabled = model.enableAutoCorrect,
          capitalization =
            when (model.capitalization) {
              Capitalization.None -> None
              Capitalization.Characters -> Characters
              Capitalization.Words -> Words
              Capitalization.Sentences -> Sentences
            },
          imeAction = if (model.onDone != null) ImeAction.Done else ImeAction.Default
        ),
      keyboardActions =
        KeyboardActions(
          onDone = model.onDone?.let { { it.invoke() } }
        )
    )

    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(cellHeight),
      horizontalArrangement = Arrangement.spacedBy(VERIFICATION_CODE_CELL_SPACING, Alignment.Start),
      verticalAlignment = Alignment.CenterVertically
    ) {
      repeat(expectedCodeLength) { index ->
        if (separatorIndex == index) {
          Box(
            modifier = Modifier.width(separatorWidth),
            contentAlignment = Alignment.Center
          ) {
            Label(
              text = "-",
              type = LabelType.Title2,
              treatment = LabelTreatment.Secondary
            )
          }
        }

        VerificationCodeCell(
          modifier = Modifier.width(cellWidth),
          digit = textValue.getOrNull(index)?.toString().orEmpty(),
          isActive = textValue.length < expectedCodeLength && activeIndex == index,
          backgroundColor = backgroundColor,
          cellHeight = cellHeight
        )
      }
    }
  }
}

internal fun sanitizeVerificationCodeInput(
  rawValue: String,
  expectedCodeLength: Int,
): String = rawValue.filter(Char::isDigit).take(expectedCodeLength)

internal fun nextSanitizedVerificationCodeInput(
  currentValue: String,
  rawValue: String,
  expectedCodeLength: Int,
): String? =
  sanitizeVerificationCodeInput(
    rawValue = rawValue,
    expectedCodeLength = expectedCodeLength
  ).takeUnless { it == currentValue }

@Composable
private fun VerificationCodeCell(
  modifier: Modifier = Modifier,
  digit: String,
  isActive: Boolean,
  backgroundColor: Color,
  cellHeight: Dp,
) {
  val entryTranslationY = with(androidx.compose.ui.platform.LocalDensity.current) {
    (cellHeight * VERIFICATION_DIGIT_ENTRY_TRANSLATION_RATIO).roundToPx()
  }

  Box(
    modifier =
      modifier
        .height(cellHeight)
        .clip(VERIFICATION_CODE_CELL_SHAPE)
        .background(
          color = backgroundColor,
          shape = VERIFICATION_CODE_CELL_SHAPE
        ).then(
          if (isActive) {
            Modifier.border(
              width = 1.dp,
              color = WalletTheme.colors.inverseBackground,
              shape = VERIFICATION_CODE_CELL_SHAPE
            )
          } else {
            Modifier
          }
        ),
    contentAlignment = Alignment.Center
  ) {
    AnimatedContent(
      modifier = Modifier.fillMaxSize(),
      targetState = digit,
      contentAlignment = Alignment.Center,
      transitionSpec = {
        (
          slideInVertically(
            animationSpec = tween(
              durationMillis = VERIFICATION_DIGIT_POSITION_ANIMATION_DURATION_MS,
              easing = FastOutSlowInEasing
            )
          ) { entryTranslationY } +
            fadeIn(
              animationSpec = tween(durationMillis = VERIFICATION_DIGIT_VISIBILITY_ANIMATION_DURATION_MS)
            )
          ).togetherWith(
            slideOutVertically(
              animationSpec = tween(
                durationMillis = VERIFICATION_DIGIT_POSITION_ANIMATION_DURATION_MS,
                easing = FastOutSlowInEasing
              )
            ) { entryTranslationY } +
              fadeOut(
                animationSpec = tween(durationMillis = VERIFICATION_DIGIT_VISIBILITY_ANIMATION_DURATION_MS)
              )
          ).using(SizeTransform(clip = false))
      },
      label = "verification-digit"
    ) { animatedDigit ->
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        if (animatedDigit.isNotEmpty()) {
          Label(
            text = animatedDigit,
            type = LabelType.Title2,
            treatment = LabelTreatment.Primary
          )
        }
      }
    }
  }
}
