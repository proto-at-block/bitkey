@file:Suppress("TooManyFunctions")

package build.wallet.ui.components.explainer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun Statement(
  modifier: Modifier = Modifier,
  title: String?,
  body: String? = null,
  icon: Icon?,
  tint: Color = WalletTheme.colors.foreground,
) {
  return Statement(
    modifier = modifier,
    title = title,
    body = body?.let { AnnotatedString(it) },
    icon = icon,
    tint = tint
  )
}

@Composable
fun Statement(
  modifier: Modifier = Modifier,
  title: String?,
  body: AnnotatedString?,
  icon: Icon?,
  tint: Color = WalletTheme.colors.foreground,
  onClick: ((Int) -> Unit)? = null,
) {
  Statement(
    modifier = modifier,
    leadingIconContent = {
      icon?.let {
        Icon(
          icon = icon,
          size = IconSize.Small,
          color = tint
        )
      }
    },
    hasTitle = title != null,
    hasBody = body != null,
    hasLeadingIcon = icon != null,
    titleContent = {
      title?.let {
        Label(
          text = it,
          type = LabelType.Body2Bold,
          treatment = LabelTreatment.Unspecified,
          color = tint
        )
      }
    },
    bodyContent = {
      body?.let {
        Label(
          text = it,
          type = LabelType.Body2Regular,
          treatment = LabelTreatment.Unspecified,
          color = tint,
          onClick = onClick
        )
      }
    }
  )
}

/**
 * Slot-based implementation.
 */
@Composable
private fun Statement(
  modifier: Modifier = Modifier,
  leadingIconContent: @Composable () -> Unit,
  titleContent: @Composable () -> Unit,
  bodyContent: @Composable () -> Unit,
  hasLeadingIcon: Boolean,
  hasTitle: Boolean,
  hasBody: Boolean,
) {
  Row(
    modifier =
      modifier
        .padding(top = 8.dp)
        .fillMaxWidth()
  ) {
    leadingIconContent()
    if (hasLeadingIcon) {
      Spacer(Modifier.width(16.dp))
    }
    Column {
      titleContent()
      if (hasTitle && hasBody) {
        // Only add between title and body when both are present. We don't want to add extra top
        // padding when we only have body content and no title.
        Spacer(Modifier.height(4.dp))
      }
      bodyContent()
    }
  }
}

@Preview
@Composable
fun StatementWithIconAndTitleAndBodyPreview() {
  PreviewWalletTheme {
    Statement(
      icon = Icon.LargeIconCheckStroked,
      title = "Statement Title",
      body = "Statement Body"
    )
  }
}

@Preview
@Composable
fun StatementWithShortTitleAndLongBodyPreview() {
  PreviewWalletTheme {
    Statement(
      icon = Icon.LargeIconCheckStroked,
      title = "Statement Title",
      body = LONG_TEXT
    )
  }
}

@Preview
@Composable
fun StatementWithTitleOnlyPreview() {
  PreviewWalletTheme {
    Statement(
      icon = Icon.LargeIconCheckStroked,
      title = "Statement Title"
    )
  }
}

@Preview
@Composable
fun StatementWithLongTitleOnlyPreview() {
  PreviewWalletTheme {
    Statement(
      icon = Icon.LargeIconCheckStroked,
      title = LONG_TEXT
    )
  }
}

@Preview
@Composable
fun StatementWithBodyOnlyPreview() {
  PreviewWalletTheme {
    Statement(
      icon = Icon.LargeIconCheckStroked,
      title = null,
      body = "Statement Body"
    )
  }
}

@Preview
@Composable
fun StatementWithLongBodyOnlyPreview() {
  PreviewWalletTheme {
    Statement(
      icon = Icon.LargeIconCheckStroked,
      title = null,
      body = LONG_TEXT
    )
  }
}

@Preview
@Composable
fun NumberedStatementWithTitleAndBodyPreview() {
  PreviewWalletTheme {
    Statement(
      icon = Icon.SmallIconDigitTwo,
      title = "Statement Title",
      body = "Statement Body"
    )
  }
}

@Preview
@Composable
internal fun StatementWithNoIconPreview() {
  PreviewWalletTheme {
    Statement(
      icon = null,
      title = "Statement Title",
      body = "Statement Body"
    )
  }
}

private const val LONG_TEXT =
  "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Odio euismod lacinia at quis risus sed vulputate. Vulputate enim nulla aliquet porttitor lacus luctus accumsan tortor."
