package build.wallet.ui.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.Data
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment.Secondary
import build.wallet.ui.tokens.LabelType

@Composable
internal fun DataRowTotal(
  modifier: Modifier = Modifier,
  model: FormMainContentModel.DataList.Data,
  contentHorizontalPadding: Dp = 16.dp,
  useContainedTypography: Boolean = false,
) {
  DataRowTotal(
    modifier = modifier.padding(horizontal = contentHorizontalPadding),
    leadingContent = {
      Column {
        Label(
          text = model.title,
          type = model.titleTextType.toTotalTitleLabelType(useContainedTypography),
          alignment = TextAlign.Start
        )
        model.secondaryTitle?.let { secondaryTitle ->
          Label(
            text = secondaryTitle,
            type =
              if (useContainedTypography) {
                LabelType.Body2Regular
              } else {
                LabelType.Body3Regular
              },
            alignment = TextAlign.Start,
            treatment = Secondary
          )
        }
      }
    },
    trailingContent = {
      Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.End
      ) {
        Label(
          text = model.sideText,
          type = model.sideTextType.toTotalSideLabelType(useContainedTypography),
          alignment = TextAlign.End
        )
        model.secondarySideText?.let { secondarySideText ->
          Label(
            text = secondarySideText,
            type = model.secondarySideTextType.toTotalSecondarySideLabelType(useContainedTypography),
            alignment = TextAlign.End,
            treatment = Secondary
          )
        }
      }
    }
  )
}

@Composable
private fun DataRowTotal(
  modifier: Modifier = Modifier,
  leadingContent: @Composable () -> Unit,
  trailingContent: @Composable () -> Unit,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(top = 12.dp, bottom = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    leadingContent()
    Spacer(Modifier.width(16.dp))
    trailingContent()
  }
}

private fun Data.TitleTextType.toTotalTitleLabelType(
  useContainedTypography: Boolean,
): LabelType {
  if (useContainedTypography) {
    return when (this) {
      Data.TitleTextType.BODY1REGULAR -> LabelType.Body1Regular
      Data.TitleTextType.BODY2REGULAR,
      Data.TitleTextType.REGULAR,
      Data.TitleTextType.BOLD,
      -> LabelType.Body2Regular
    }
  }

  return when (this) {
    Data.TitleTextType.BODY1REGULAR -> LabelType.Body1Regular
    Data.TitleTextType.BODY2REGULAR -> LabelType.Body2Regular
    Data.TitleTextType.BOLD, Data.TitleTextType.REGULAR -> LabelType.Body2Bold
  }
}

private fun Data.SideTextType.toTotalSideLabelType(
  useContainedTypography: Boolean,
): LabelType {
  if (useContainedTypography) {
    return when (this) {
      Data.SideTextType.BODY1REGULAR -> LabelType.Body1Regular
      Data.SideTextType.BODY2REGULAR,
      Data.SideTextType.BODY2BOLD,
      Data.SideTextType.REGULAR,
      Data.SideTextType.MEDIUM,
      Data.SideTextType.BOLD,
      -> LabelType.Body2Regular
    }
  }

  return when (this) {
    Data.SideTextType.BODY1REGULAR -> LabelType.Body1Regular
    Data.SideTextType.BODY2REGULAR -> LabelType.Body2Regular
    Data.SideTextType.REGULAR,
    Data.SideTextType.MEDIUM,
    Data.SideTextType.BOLD,
    Data.SideTextType.BODY2BOLD,
    -> LabelType.Body2Bold
  }
}

private fun Data.SideTextType.toTotalSecondarySideLabelType(
  useContainedTypography: Boolean,
): LabelType {
  if (useContainedTypography) {
    return when (this) {
      Data.SideTextType.BODY1REGULAR -> LabelType.Body1Regular
      Data.SideTextType.BODY2REGULAR,
      Data.SideTextType.BODY2BOLD,
      Data.SideTextType.REGULAR,
      Data.SideTextType.MEDIUM,
      Data.SideTextType.BOLD,
      -> LabelType.Body2Regular
    }
  }

  return when (this) {
    Data.SideTextType.BODY1REGULAR -> LabelType.Body1Regular
    Data.SideTextType.BODY2REGULAR -> LabelType.Body2Regular
    Data.SideTextType.REGULAR,
    Data.SideTextType.MEDIUM,
    Data.SideTextType.BOLD,
    Data.SideTextType.BODY2BOLD,
    -> LabelType.Body3Regular
  }
}
