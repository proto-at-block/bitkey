@file:Suppress("TooManyFunctions")

package build.wallet.ui.components.button

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.loading.LoadingIndicator
import build.wallet.ui.components.sheet.LocalSheetCloser
import build.wallet.ui.compose.resId
import build.wallet.ui.compose.scalingClickable
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.Click
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size
import build.wallet.ui.model.button.ButtonModel.Size.Regular
import build.wallet.ui.model.button.ButtonModel.Treatment
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.theme.WalletTheme
import kotlinx.coroutines.launch

@Composable
fun Button(
  model: ButtonModel,
  cornerRadius: Dp = 16.dp,
  modifier: Modifier = Modifier,
) {
  with(model) {
    Button(
      text = text,
      modifier = modifier,
      enabled = isEnabled,
      isLoading = isLoading,
      leadingIcon = leadingIcon,
      treatment = treatment,
      size = size,
      cornerRadius = cornerRadius,
      testTag = testTag,
      onClick = model.onClick
    )
  }
}

/**
 * @param isLoading - if `true`, button's content (text and leading icon) will be hidden, and a
 * loading indicator will be shown, without changing button's size (based on otherwise shown content).
 * When [isLoading] is `true`, the [onClick] is disabled. [isLoading] is useful for showing some
 * loading state if clicking the button results in an async operation.
 */
@Composable
fun Button(
  text: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  isLoading: Boolean = false,
  leadingIcon: Icon? = null,
  treatment: Treatment = Primary,
  size: Size = Regular,
  cornerRadius: Dp = 16.dp,
  testTag: String? = null,
  onClick: Click,
) {
  // when the onClick is of type [Click.SheetClosingClick], we need to close the sheet first and then
  // invoke the onClick callback.
  // Otherwise, we can just invoke the onClick callback.
  val clickHandler: () -> Unit =
    when (onClick) {
      is SheetClosingClick -> {
        val scope = rememberStableCoroutineScope()
        val sheetCloser = LocalSheetCloser.current
        {
          scope.launch {
            sheetCloser()
          }.invokeOnCompletion { onClick() }
        }
      }

      is StandardClick -> onClick::invoke
    }

  Button(
    text = text,
    modifier = modifier,
    enabled = enabled,
    isLoading = isLoading,
    leadingIcon = leadingIcon,
    style =
      WalletTheme.buttonStyle(
        treatment = treatment,
        size = size,
        cornerRadius = cornerRadius,
        enabled = enabled
      ),
    testTag = testTag,
    onClick = clickHandler
  )
}

@Composable
fun Button(
  text: String,
  modifier: Modifier = Modifier,
  leadingIcon: Icon? = null,
  isLoading: Boolean = false,
  enabled: Boolean = true,
  style: ButtonStyle,
  testTag: String? = null,
  onClick: () -> Unit,
) {
  val disabledContentAlpha = 0.3f

  Button(
    modifier = modifier,
    enabled = enabled,
    isLoading = isLoading,
    style = style,
    testTag = testTag,
    onClick = onClick
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      leadingIcon?.let {
        Icon(
          icon = leadingIcon,
          size = style.iconSize,
          color = style.iconColor,
          modifier = if (enabled) Modifier else Modifier.alpha(disabledContentAlpha)
        )
        if (text.isNotBlank()) Spacer(modifier = Modifier.width(4.dp))
      }
      Label(
        text = text,
        style = style.textStyle,
        modifier = if (enabled) Modifier else Modifier.alpha(disabledContentAlpha)
      )
    }
  }
}

@Composable
internal fun Button(
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  isLoading: Boolean = false,
  style: ButtonStyle,
  testTag: String? = null,
  onClick: () -> Unit,
  content: @Composable () -> Unit,
) {
  Box(
    modifier =
      modifier
        .resId(testTag)
        .run {
          style.height?.let { height(it) } ?: this
        }
        .defaultMinSize(minWidth = style.minWidth)
        .thenIf(style.fillWidth) {
          Modifier.fillMaxWidth()
        }
        .scalingClickable(
          enabled = enabled && !isLoading,
          onClick = onClick
        )
        .clip(style.shape)
        .thenIf(!style.isTextButton) {
          Modifier.background(
            color = style.backgroundColor,
            shape = style.shape
          )
        },
    contentAlignment = Alignment.Center
  ) {
    Box(
      modifier =
        Modifier
          .defaultMinSize(minHeight = 48.dp)
          .padding(
            vertical = style.verticalPadding,
            horizontal = style.horizontalPadding
          ),
      contentAlignment = Alignment.Center
    ) {
      AnimatedVisibility(
        visible = isLoading,
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        LoadingIndicator(
          modifier = Modifier.size(24.dp),
          color = style.iconColor
        )
      }

      AnimatedVisibility(
        visible = !isLoading,
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        content()
      }
    }
  }
}
