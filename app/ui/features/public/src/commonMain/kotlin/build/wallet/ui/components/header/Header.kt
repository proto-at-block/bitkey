package build.wallet.ui.components.header

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.CENTER
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.LEADING
import build.wallet.statemachine.core.form.FormHeaderModel.SublineTreatment.*
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.LabelTreatment.*
import build.wallet.ui.components.label.buildAnnotatedString
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.tokens.LabelType

@Composable
fun Header(
  modifier: Modifier = Modifier,
  model: FormHeaderModel,
  headlineLabelType: LabelType = LabelType.Title1,
  sublineLabelTreatment: LabelTreatment = Secondary,
  theme: Theme = LocalTheme.current,
) {
  Header(
    modifier = modifier,
    iconModel = model.iconModel,
    customContent = model.customContent,
    headline = model.headline,
    subline = model.sublineModel?.buildAnnotatedString(),
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
      when (theme) {
        Theme.DARK -> Color.White
        else -> Color.Unspecified
      },
    headlineLabelType = headlineLabelType,
    headlineLabelTreatment =
      when (theme) {
        Theme.DARK -> Unspecified
        else -> Primary
      },
    sublineLabelColor =
      when (theme) {
        Theme.DARK -> Color.White
        else -> Color.Unspecified
      },
    sublineLabelType =
      when (model.sublineTreatment) {
        REGULAR -> LabelType.Body2Regular
        SMALL -> LabelType.Body3Regular
        MONO -> LabelType.Body2Mono
      },
    sublineLabelTreatment =
      when (theme) {
        Theme.DARK -> Unspecified
        else -> sublineLabelTreatment
      }
  )
}

@Composable
fun Header(
  modifier: Modifier = Modifier,
  iconModel: IconModel? = null,
  customContent: FormHeaderModel.CustomContent? = null,
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
    customContent = customContent,
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
  customContent: FormHeaderModel.CustomContent? = null,
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
    customContent = {
      customContent?.let {
        CustomHeaderContent(model = customContent)
      }
    },
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
  customContent: @Composable () -> Unit,
  headlineContent: @Composable () -> Unit,
  sublineContent: @Composable () -> Unit,
  fillsMaxWidth: Boolean = true,
) {
  Column(
    modifier = modifier.thenIf(fillsMaxWidth) { Modifier.fillMaxWidth() },
    horizontalAlignment = horizontalAlignment
  ) {
    iconContent()
    customContent()
    headlineContent()
    sublineContent()
  }
}
