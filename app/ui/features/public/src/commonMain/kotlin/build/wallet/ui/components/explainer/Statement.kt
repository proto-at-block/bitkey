package build.wallet.ui.components.explainer

import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

@Composable
fun Statement(
  modifier: Modifier = Modifier,
  title: String?,
  body: String? = null,
  icon: Icon?,
  leadingIconSize: IconSize = IconSize.Small,
  leadingContentTopPadding: Dp = 0.dp,
  leadingContentSpacing: Dp = 16.dp,
  leadingText: String? = null,
  leadingTextType: LabelType = LabelType.Body2MonoCaps,
  leadingTextTreatment: LabelTreatment? = null,
  tint: Color = WalletTheme.colors.foreground,
  titleType: LabelType = LabelType.Body2Bold,
  titleTreatment: LabelTreatment? = null,
  bodyType: LabelType = LabelType.Body2Regular,
  bodyTreatment: LabelTreatment? = null,
) {
  return Statement(
    modifier = modifier,
    title = title,
    body = body?.let { AnnotatedString(it) },
    icon = icon,
    leadingIconSize = leadingIconSize,
    leadingContentTopPadding = leadingContentTopPadding,
    leadingContentSpacing = leadingContentSpacing,
    leadingText = leadingText,
    leadingTextType = leadingTextType,
    leadingTextTreatment = leadingTextTreatment,
    tint = tint,
    titleType = titleType,
    titleTreatment = titleTreatment,
    bodyType = bodyType,
    bodyTreatment = bodyTreatment
  )
}

@Composable
fun Statement(
  modifier: Modifier = Modifier,
  title: String?,
  body: AnnotatedString?,
  icon: Icon?,
  leadingIconSize: IconSize = IconSize.Small,
  leadingContentTopPadding: Dp = 0.dp,
  leadingContentSpacing: Dp = 16.dp,
  leadingText: String? = null,
  leadingTextType: LabelType = LabelType.Body2MonoCaps,
  leadingTextTreatment: LabelTreatment? = null,
  tint: Color = WalletTheme.colors.foreground,
  titleType: LabelType = LabelType.Body2Bold,
  titleTreatment: LabelTreatment? = null,
  bodyType: LabelType = LabelType.Body2Regular,
  bodyTreatment: LabelTreatment? = null,
  onClick: ((Int) -> Unit)? = null,
) {
  Statement(
    modifier = modifier,
    leadingContent = {
      Box(modifier = Modifier.padding(top = leadingContentTopPadding)) {
        when {
          leadingText != null ->
            Label(
              text = leadingText,
              type = leadingTextType,
              treatment = leadingTextTreatment ?: LabelTreatment.Unspecified,
              color = if (leadingTextTreatment == null) tint else Color.Unspecified
            )
          icon != null ->
            Icon(
              icon = icon,
              size = leadingIconSize,
              color = tint
            )
        }
      }
    },
    hasTitle = title != null,
    hasBody = body != null,
    hasLeadingContent = icon != null || leadingText != null,
    leadingContentSpacing = leadingContentSpacing,
    titleContent = {
      title?.let {
        Label(
          text = it,
          type = titleType,
          treatment = titleTreatment ?: LabelTreatment.Unspecified,
          color = if (titleTreatment == null) tint else Color.Unspecified
        )
      }
    },
    bodyContent = {
      body?.let {
        Label(
          text = it,
          type = bodyType,
          treatment = bodyTreatment ?: LabelTreatment.Unspecified,
          color = if (bodyTreatment == null) tint else Color.Unspecified,
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
  leadingContent: @Composable () -> Unit,
  titleContent: @Composable () -> Unit,
  bodyContent: @Composable () -> Unit,
  hasLeadingContent: Boolean,
  hasTitle: Boolean,
  hasBody: Boolean,
  leadingContentSpacing: Dp = 16.dp,
) {
  Row(
    modifier =
      modifier
        .padding(top = 8.dp)
        .fillMaxWidth()
  ) {
    leadingContent()
    if (hasLeadingContent) {
      Spacer(Modifier.width(leadingContentSpacing))
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
