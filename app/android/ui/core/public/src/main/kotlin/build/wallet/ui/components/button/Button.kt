@file:Suppress("TooManyFunctions")

package build.wallet.ui.components.button

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.loading.LoadingIndicator
import build.wallet.ui.components.sheet.LocalSheetCloser
import build.wallet.ui.compose.resId
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Size.Regular
import build.wallet.ui.model.button.ButtonModel.Treatment
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tooling.PreviewWalletTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import androidx.compose.material3.Button as MaterialButton

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
      is Click.SheetClosingClick -> {
        val scope = rememberStableCoroutineScope()
        val sheetCloser = LocalSheetCloser.current
        {
          scope.launch {
            sheetCloser()
          }.invokeOnCompletion { onClick() }
        }
      }

      is Click.StandardClick -> onClick::invoke
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
          color = style.iconColor
        )
        if (text.isNotBlank()) Spacer(modifier = Modifier.width(4.dp))
      }
      Label(
        text = text,
        style = style.textStyle
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
  val interactionSource = remember { MutableInteractionSource() }

  MaterialButton(
    interactionSource = interactionSource,
    onClick = {
      if (!isLoading) onClick()
    },
    modifier =
      modifier
        .resId(testTag)
        .height(style.height),
    enabled = enabled,
    shape = style.shape,
    contentPadding = PaddingValues(0.dp),
    colors =
      when {
        style.isTextButton -> ButtonDefaults.textButtonColors(contentColor = style.textStyle.color)
        else ->
          ButtonDefaults.buttonColors(
            containerColor = style.backgroundColor,
            disabledContainerColor = style.backgroundColor
          )
      }
  ) {
    Box(
      modifier =
        Modifier
          .fillMaxHeight()
          .defaultMinSize(minWidth = style.minWidth)
          .thenIf(style.fillWidth) {
            Modifier.fillMaxWidth()
          },
      contentAlignment = Alignment.Center
    ) {
      Box(
        modifier =
          Modifier
            .defaultMinSize(minHeight = ButtonDefaults.MinHeight)
            .padding(
              vertical = style.verticalPadding,
              horizontal = style.horizontalPadding
            ),
        contentAlignment = Alignment.Center
      ) {
        this@MaterialButton.AnimatedVisibility(
          visible = isLoading,
          enter = fadeIn(),
          exit = fadeOut()
        ) {
          LoadingIndicator(
            modifier = Modifier.size(24.dp),
            color = style.iconColor
          )
        }

        this@MaterialButton.AnimatedVisibility(
          visible = !isLoading,
          enter = fadeIn(),
          exit = fadeOut()
        ) {
          content()
        }
      }
    }
  }
}

@Preview
@Composable
internal fun RegularButtonsWithIconEnabled() {
  PreviewWalletTheme {
    AllButtonsForSizeAndIcon(
      size = Regular,
      showLeadingIcon = true,
      enabled = true
    )
  }
}

@Preview
@Composable
internal fun RegularButtonsWithIconDisabled() {
  PreviewWalletTheme {
    AllButtonsForSizeAndIcon(
      size = Regular,
      showLeadingIcon = true,
      enabled = false
    )
  }
}

@Preview
@Composable
internal fun RegularButtonsWithoutIconEnabled() {
  PreviewWalletTheme {
    AllButtonsForSizeAndIcon(
      size = Regular,
      showLeadingIcon = false,
      enabled = true
    )
  }
}

@Preview
@Composable
internal fun RegularButtonsWithoutIconDisabled() {
  PreviewWalletTheme {
    AllButtonsForSizeAndIcon(
      size = Regular,
      showLeadingIcon = false,
      enabled = false
    )
  }
}

@Preview
@Composable
internal fun CompactButtonsWithIconEnabled() {
  PreviewWalletTheme {
    AllButtonsForSizeAndIcon(
      size = Compact,
      showLeadingIcon = true,
      enabled = true
    )
  }
}

@Preview
@Composable
internal fun CompactButtonsWithIconDisabled() {
  PreviewWalletTheme {
    AllButtonsForSizeAndIcon(
      size = Compact,
      showLeadingIcon = true,
      enabled = false
    )
  }
}

@Preview
@Composable
internal fun CompactButtonsWithoutIconEnabled() {
  PreviewWalletTheme {
    AllButtonsForSizeAndIcon(
      size = Compact,
      showLeadingIcon = false,
      enabled = true
    )
  }
}

@Preview
@Composable
internal fun CompactButtonsWithoutIconDisabled() {
  PreviewWalletTheme {
    AllButtonsForSizeAndIcon(
      size = Compact,
      showLeadingIcon = false,
      enabled = false
    )
  }
}

@Preview
@Composable
internal fun FooterButtonsWithIconEnabled() {
  PreviewWalletTheme {
    AllButtonsForSizeAndIcon(
      size = Footer,
      showLeadingIcon = true,
      enabled = true
    )
  }
}

@Preview
@Composable
internal fun FooterButtonsWithIconDisabled() {
  PreviewWalletTheme {
    AllButtonsForSizeAndIcon(
      size = Footer,
      showLeadingIcon = true,
      enabled = false
    )
  }
}

@Preview
@Composable
internal fun FooterButtonsWithoutIconEnabled() {
  PreviewWalletTheme {
    AllButtonsForSizeAndIcon(
      size = Footer,
      showLeadingIcon = false,
      enabled = true
    )
  }
}

@Preview
@Composable
internal fun FooterButtonsWithoutIconDisabled() {
  PreviewWalletTheme {
    AllButtonsForSizeAndIcon(
      size = Footer,
      showLeadingIcon = false,
      enabled = false
    )
  }
}

@Preview
@Composable
internal fun ElevatedRegularButtonsEnabled() {
  PreviewWalletTheme {
    AllButtonsForSizeAndIcon(
      size = Regular,
      showLeadingIcon = false,
      enabled = true
    )
  }
}

@Preview
@Composable
internal fun ElevatedRegularButtonsDisabled() {
  PreviewWalletTheme {
    AllButtonsForSizeAndIcon(
      size = Regular,
      showLeadingIcon = false,
      enabled = false
    )
  }
}

@Preview
@Composable
internal fun RegularButtonLoading() {
  PreviewWalletTheme {
    AllButtonsForSizeAndIcon(
      size = Regular,
      showLeadingIcon = false,
      isLoading = true,
      enabled = true
    )
  }
}

@Composable
private fun AllButtonsForSizeAndIcon(
  size: Size,
  showLeadingIcon: Boolean,
  enabled: Boolean,
  isLoading: Boolean = false,
) {
  Box(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(color = Color(0xffeeeeee))
        .padding(
          horizontal = 20.dp,
          vertical = 5.dp
        ),
    contentAlignment = Alignment.Center
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(10.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Treatment.entries.forEach { treatment ->
        Button(
          text = treatment.name.readable(),
          treatment = treatment,
          isLoading = isLoading,
          leadingIcon = if (showLeadingIcon) Icon.SmallIconBitkey else null,
          size = size,
          enabled = enabled,
          onClick = Click.StandardClick { }
        )
      }
    }
  }
}

@Preview
@Composable
private fun ButtonLoadingPreview() {
  var isLoading by remember { mutableStateOf(false) }
  LaunchedEffect(isLoading) {
    delay(2.seconds)
    isLoading = !isLoading
  }

  PreviewWalletTheme {
    Box(modifier = Modifier.padding(5.dp)) {
      Button(
        text = "Hello Bitcoin!",
        treatment = Primary,
        isLoading = isLoading,
        size = Regular,
        onClick = Click.StandardClick { }
      )
    }
  }
}

private fun String.readable(): String {
  // Insert space before uppercase letters and convert the string to lowercase
  val withSpaces = this.replace("(?<!^)(?=[A-Z])".toRegex(), " ").lowercase(Locale.getDefault())
  // Capitalize the first character of the resulting string
  return withSpaces.replaceFirstChar {
    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
  }
}
