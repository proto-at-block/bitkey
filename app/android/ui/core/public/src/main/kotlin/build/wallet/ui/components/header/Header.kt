package build.wallet.ui.components.header

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.LabelModel.StringWithStyledSubstringModel.SubstringStyle.BoldStyle
import build.wallet.statemachine.core.LabelModel.StringWithStyledSubstringModel.SubstringStyle.ColorStyle
import build.wallet.statemachine.core.ScreenColorMode
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.CENTER
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.LEADING
import build.wallet.statemachine.core.form.FormHeaderModel.SublineTreatment.*
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.LabelTreatment.*
import build.wallet.ui.components.label.toWalletTheme
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
fun Header(
  modifier: Modifier = Modifier,
  model: FormHeaderModel,
  headlineLabelType: LabelType = LabelType.Title1,
  sublineLabelTreatment: LabelTreatment = Secondary,
  colorMode: ScreenColorMode = ScreenColorMode.SystemPreference,
) {
  Header(
    modifier = modifier,
    iconModel = model.iconModel,
    headline = model.headline,
    subline =
      model.sublineModel?.let {
        buildAnnotatedString {
          append(it.string)
          when (it) {
            is LabelModel.StringWithStyledSubstringModel ->
              it.styledSubstrings.forEach { styledSubstring ->
                addStyle(
                  style =
                    when (val substringStyle = styledSubstring.style) {
                      is ColorStyle -> SpanStyle(color = substringStyle.color.toWalletTheme())
                      is BoldStyle -> SpanStyle(fontWeight = FontWeight.W600)
                    },
                  start = styledSubstring.range.first, end = styledSubstring.range.last + 1
                )
              }
            is LabelModel.LinkSubstringModel ->
              it.linkedSubstrings.forEach { linkedSubstring ->
                addStyle(
                  style = SpanStyle(
                    color = WalletTheme.colors.bitkeyPrimary,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.W600
                  ),
                  start = linkedSubstring.range.first,
                  end = linkedSubstring.range.last + 1
                )
              }
            is LabelModel.StringModel -> Unit
          }
        }
      },
    sublineOnClick = { index ->
      model.sublineModel?.let {
        when (it) {
          is LabelModel.LinkSubstringModel ->
            it.linkedSubstrings.forEach { link ->
              if (link.range.contains(index)) {
                link.onClick()
              }
            }
          else -> Unit
        }
      }
    },
    horizontalAlignment =
      when (model.alignment) {
        LEADING -> Alignment.Start
        CENTER -> Alignment.CenterHorizontally
      },
    textAlignment =
      when (model.alignment) {
        LEADING -> TextAlign.Start
        CENTER -> TextAlign.Center
      },
    headlineLabelColor =
      when (colorMode) {
        ScreenColorMode.Dark -> Color.White
        else -> Color.Unspecified
      },
    headlineLabelType = headlineLabelType,
    headlineLabelTreatment =
      when (colorMode) {
        ScreenColorMode.Dark -> Unspecified
        else -> Primary
      },
    sublineLabelColor =
      when (colorMode) {
        ScreenColorMode.Dark -> Color.White
        else -> Color.Unspecified
      },
    sublineLabelType =
      when (model.sublineTreatment) {
        REGULAR -> LabelType.Body2Regular
        SMALL -> LabelType.Body3Regular
        MONO -> LabelType.Body2Mono
      },
    sublineLabelTreatment =
      when (colorMode) {
        ScreenColorMode.Dark -> Unspecified
        else -> sublineLabelTreatment
      }
  )
}

@Composable
fun Header(
  modifier: Modifier = Modifier,
  iconModel: IconModel? = null,
  headline: String?,
  subline: AnnotatedString? = null,
  sublineOnClick: ((Int) -> Unit)? = null,
  textAlignment: TextAlign = TextAlign.Start,
  horizontalAlignment: Alignment.Horizontal = Alignment.Start,
  headlineLabelColor: Color = Color.Unspecified,
  headlineLabelType: LabelType = LabelType.Title1,
  headlineLabelTreatment: LabelTreatment = Primary,
  sublineLabelColor: Color = Color.Unspecified,
  sublineLabelType: LabelType = LabelType.Body2Regular,
  sublineLabelTreatment: LabelTreatment = Secondary,
  iconTopSpacing: Dp = 24.dp,
  headlineTopSpacing: Dp = 16.dp,
  sublineTopSpacing: Dp = 8.dp,
  fillsMaxWidth: Boolean = true,
) {
  Header(
    modifier = modifier.thenIf(fillsMaxWidth) { Modifier.fillMaxWidth() },
    iconContent = {
      iconModel?.let {
        Spacer(modifier = Modifier.height(iconModel.iconTopSpacing?.dp ?: iconTopSpacing))
        IconImage(model = iconModel)
      }
    },
    headline = headline,
    subline = subline,
    sublineOnClick = sublineOnClick,
    textAlignment = textAlignment,
    horizontalAlignment = horizontalAlignment,
    headlineLabelColor = headlineLabelColor,
    headlineLabelType = headlineLabelType,
    headlineLabelTreatment = headlineLabelTreatment,
    sublineLabelColor = sublineLabelColor,
    sublineLabelType = sublineLabelType,
    sublineLabelTreatment = sublineLabelTreatment,
    headlineTopSpacing = headlineTopSpacing,
    sublineTopSpacing = sublineTopSpacing,
    fillsMaxWidth = fillsMaxWidth
  )
}

@Composable
fun Header(
  modifier: Modifier = Modifier,
  iconContent: @Composable () -> Unit,
  headline: String?,
  subline: AnnotatedString? = null,
  sublineOnClick: ((Int) -> Unit)? = null,
  textAlignment: TextAlign = TextAlign.Start,
  horizontalAlignment: Alignment.Horizontal = Alignment.Start,
  headlineLabelColor: Color = Color.Unspecified,
  headlineLabelType: LabelType = LabelType.Title1,
  headlineLabelTreatment: LabelTreatment = Primary,
  sublineLabelColor: Color = Color.Unspecified,
  sublineLabelType: LabelType = LabelType.Body2Regular,
  sublineLabelTreatment: LabelTreatment = Secondary,
  headlineTopSpacing: Dp = 16.dp,
  sublineTopSpacing: Dp = 8.dp,
  fillsMaxWidth: Boolean = true,
) {
  Header(
    modifier = modifier,
    horizontalAlignment = horizontalAlignment,
    iconContent = iconContent,
    headlineContent = {
      headline?.let {
        Label(
          modifier = Modifier.padding(top = headlineTopSpacing),
          text = headline,
          type = headlineLabelType,
          treatment = headlineLabelTreatment,
          alignment = textAlignment,
          color = headlineLabelColor
        )
      }
    },
    sublineContent = {
      subline?.let {
        Label(
          modifier = Modifier.padding(top = sublineTopSpacing),
          text = it,
          type = sublineLabelType,
          treatment = sublineLabelTreatment,
          alignment = textAlignment,
          color = sublineLabelColor,
          onClick = sublineOnClick
        )
      }
    },
    fillsMaxWidth = fillsMaxWidth
  )
}

/**
 * Slot-based implementation.
 */
@Composable
fun Header(
  modifier: Modifier = Modifier,
  horizontalAlignment: Alignment.Horizontal,
  iconContent: @Composable () -> Unit,
  headlineContent: @Composable () -> Unit,
  sublineContent: @Composable () -> Unit,
  fillsMaxWidth: Boolean = true,
) {
  Column(
    modifier = modifier.thenIf(fillsMaxWidth) { Modifier.fillMaxWidth() },
    horizontalAlignment = horizontalAlignment
  ) {
    iconContent()
    headlineContent()
    sublineContent()
  }
}

@Preview
@Composable
internal fun HeaderWithIconHeadlineAndSublinePreview() {
  PreviewWalletTheme {
    Header(
      iconModel = IconModel(Icon.LargeIconCheckStroked, IconSize.Avatar),
      headline = "Headline",
      subline = AnnotatedString("Subline")
    )
  }
}

@Preview
@Composable
internal fun HeaderWithIconAndHeadlinePreview() {
  PreviewWalletTheme {
    Header(
      iconModel = IconModel(Icon.LargeIconCheckStroked, IconSize.Avatar),
      headline = "Headline",
      subline = null
    )
  }
}

@Preview
@Composable
internal fun HeaderWithHeadlineAndSublinePreview() {
  PreviewWalletTheme {
    Header(
      iconModel = null,
      headline = "Headline",
      subline = AnnotatedString("Subline")
    )
  }
}

@Preview
@Composable
internal fun HeaderWithHeadlineAndSublineDarkPreview() {
  PreviewWalletTheme {
    Header(
      modifier = Modifier.background(Color.Black),
      model =
        FormHeaderModel(
          headline = "Headline",
          subline = "Subline"
        ),
      colorMode = ScreenColorMode.Dark
    )
  }
}

@Preview
@Composable
internal fun HeaderWithHeadlineAndSublineCenteredPreview() {
  PreviewWalletTheme {
    Header(
      iconModel = null,
      headline = "Headline",
      subline = AnnotatedString("Subline"),
      horizontalAlignment = Alignment.CenterHorizontally
    )
  }
}

@Preview
@Composable
internal fun HeaderWithIconHeadlineAndSublineCenteredPreview() {
  PreviewWalletTheme {
    Header(
      iconModel = IconModel(Icon.LargeIconCheckStroked, IconSize.Avatar),
      headline = "Headline",
      subline = AnnotatedString("Subline"),
      horizontalAlignment = Alignment.CenterHorizontally
    )
  }
}
