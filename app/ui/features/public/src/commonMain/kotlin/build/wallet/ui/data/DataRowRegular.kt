@file:JvmName("DataRowItemKt")

package build.wallet.ui.data

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.Data
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.Data.SideTextTreatment
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.Data.SideTextType
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.icon.IconButton
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.icon.iconStyle
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.icon.*
import build.wallet.ui.model.icon.IconSize.Accessory
import build.wallet.ui.model.icon.IconSize.Small
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import kotlin.jvm.JvmName
import build.wallet.statemachine.core.Icon as LegacyIcon

@Composable
internal fun DataRowRegular(
  modifier: Modifier = Modifier,
  model: Data,
  isFirst: Boolean,
  contentHorizontalPadding: Dp = 16.dp,
  useContainedDesignSystemV2Typography: Boolean = false,
) {
  val isDesignSystemV2Enabled = true
  val endIconColor = model.endIconColor(isDesignSystemV2Enabled)
  val endIconSize = model.endIconSize(isDesignSystemV2Enabled)
  val trailingAccessoryOpticalOffset = model.trailingAccessoryOpticalOffset(isDesignSystemV2Enabled)
  val helperBackgroundColor = helperBackgroundColor(isDesignSystemV2Enabled)
  val dividerColor = dividerColor(isDesignSystemV2Enabled)
  val helperContent: (@Composable () -> Unit)? =
    model.explainer?.let { explainer ->
      {
        DataRowHelperContent(
          explainer = explainer,
          contentHorizontalPadding = contentHorizontalPadding,
          helperBackgroundColor = helperBackgroundColor,
          dividerColor = dividerColor
        )
      }
    }
  DataRowRegular(
    modifier =
      modifier
        .thenIf(model.onClick != null) {
          Modifier.clickable {
            model.onClick?.invoke()
          }
        }
        .padding(
          top =
            if (isFirst) {
              16.dp
            } else {
              0.dp
            }
        ),
    showBottomDivider = model.showBottomDivider,
    leadingContent = {
      Row(
        modifier = Modifier.thenIf(model.onTitle != null) {
          Modifier.clickable {
            model.onTitle?.invoke()
          }
        },
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Label(
            text = model.title,
            type = model.titleTextType.toLabelType(useContainedDesignSystemV2Typography),
            alignment = TextAlign.Start,
            treatment = LabelTreatment.Secondary
          )
          model.secondaryTitle?.let { secondaryTitle ->
            Label(
              text = secondaryTitle,
              type =
                if (useContainedDesignSystemV2Typography) {
                  LabelType.Body2Regular
                } else {
                  LabelType.Body3Regular
                },
              alignment = TextAlign.Start,
              treatment = LabelTreatment.Secondary
            )
          }
        }

        model.titleIcon?.let { titleIcon ->
          IconImage(
            modifier = Modifier.padding(start = 4.dp),
            model = titleIcon
          )
        }
      }
    },
    trailingContent = {
      Row(
        modifier = Modifier.offset(x = trailingAccessoryOpticalOffset),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.End
        ) {
          Label(
            text = model.sideText,
            alignment = TextAlign.End,
            type = model.sideTextType.toLabelType(useContainedDesignSystemV2Typography),
            treatment = model.sideTextTreatment.toLabelTreatment()
          )
          model.secondarySideText?.let {
            Label(
              text = it,
              type = model.secondarySideTextType.toLabelType(useContainedDesignSystemV2Typography),
              treatment = model.secondarySideTextTreatment.toLabelTreatment(),
              alignment = TextAlign.End
            )
          }
        }
        model.onClick?.let {
          Spacer(modifier = Modifier.width(2.dp))
          Icon(
            icon = model.endIcon,
            size = endIconSize,
            color = endIconColor
          )
        }
      }
    },
    helperContent = helperContent,
    contentHorizontalPadding = contentHorizontalPadding
  )
}

@Composable
private fun Data.endIconColor(isDesignSystemV2Enabled: Boolean): Color {
  return if (isDesignSystemV2Enabled && endIcon == LegacyIcon.SmallIconCopy) {
    WalletTheme.colors.foreground
  } else {
    WalletTheme.colors.foreground30
  }
}

private fun Data.endIconSize(isDesignSystemV2Enabled: Boolean): IconSize {
  return if (isDesignSystemV2Enabled && endIcon == LegacyIcon.SmallIconCopy) {
    Accessory
  } else {
    Small
  }
}

private fun Data.trailingAccessoryOpticalOffset(isDesignSystemV2Enabled: Boolean): Dp {
  return if (isDesignSystemV2Enabled && onClick != null) {
    when (endIcon) {
      LegacyIcon.SmallIconCaretRight -> 4.dp
      LegacyIcon.SmallIconCopy -> 3.dp
      else -> 0.dp
    }
  } else {
    0.dp
  }
}

@Composable
private fun helperBackgroundColor(isDesignSystemV2Enabled: Boolean): Color {
  return if (isDesignSystemV2Enabled) {
    WalletTheme.colors.secondary
  } else {
    WalletTheme.colors.foreground10
  }
}

@Composable
private fun dividerColor(isDesignSystemV2Enabled: Boolean): Color {
  return if (isDesignSystemV2Enabled) {
    if (LocalTheme.current == Theme.DARK) WalletTheme.colors.foreground30 else WalletTheme.colors.foreground10
  } else {
    Color.Black.copy(alpha = 0.05F)
  }
}

@Composable
private fun DataRowHelperContent(
  explainer: Data.Explainer,
  contentHorizontalPadding: Dp,
  helperBackgroundColor: Color,
  dividerColor: Color,
) {
  Column(
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.Start
  ) {
    if (explainer.showTopDivider) {
      Divider(
        modifier = Modifier.padding(horizontal = contentHorizontalPadding),
        color = dividerColor
      )
    }
    Column(
      modifier =
        Modifier
          .background(color = helperBackgroundColor)
          .padding(16.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.Start
    ) {
      Row(verticalAlignment = Alignment.Top) {
        Column(
          modifier = Modifier.weight(1f)
        ) {
          Label(
            text = explainer.title,
            type = LabelType.Body3Bold,
            alignment = TextAlign.Start,
            treatment = LabelTreatment.Primary
          )
          Spacer(Modifier.height(6.dp))
          Label(
            text = explainer.subtitle,
            type = LabelType.Body3Regular,
            alignment = TextAlign.Start,
            treatment = LabelTreatment.Secondary
          )
        }
        explainer.iconButton?.let { iconButton ->
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(model = iconButton)
            Spacer(Modifier.width(24.dp))
          }
        }
      }
    }
  }
}

private fun SideTextType.toLabelType(useContainedDesignSystemV2Typography: Boolean): LabelType {
  if (useContainedDesignSystemV2Typography) {
    return when (this) {
      SideTextType.BODY1REGULAR -> LabelType.Body1Regular
      SideTextType.BODY2BOLD,
      SideTextType.BODY2REGULAR,
      SideTextType.REGULAR,
      SideTextType.MEDIUM,
      SideTextType.BOLD,
      -> LabelType.Body2Regular
    }
  }

  return when (this) {
    SideTextType.REGULAR -> LabelType.Body3Regular
    SideTextType.MEDIUM -> LabelType.Body3Medium
    SideTextType.BOLD -> LabelType.Body3Bold
    SideTextType.BODY2BOLD -> LabelType.Body2Bold
    SideTextType.BODY2REGULAR -> LabelType.Body2Regular
    SideTextType.BODY1REGULAR -> LabelType.Body1Regular
  }
}

private fun SideTextTreatment.toLabelTreatment(): LabelTreatment {
  return when (this) {
    SideTextTreatment.PRIMARY -> LabelTreatment.Primary
    SideTextTreatment.SECONDARY -> LabelTreatment.Secondary
    SideTextTreatment.WARNING -> LabelTreatment.Warning
    SideTextTreatment.STRIKETHROUGH -> LabelTreatment.Strikethrough
  }
}

private fun Data.TitleTextType.toLabelType(
  useContainedDesignSystemV2Typography: Boolean,
): LabelType {
  if (useContainedDesignSystemV2Typography) {
    return when (this) {
      Data.TitleTextType.BODY1REGULAR -> LabelType.Body1Regular
      Data.TitleTextType.BODY2REGULAR,
      Data.TitleTextType.REGULAR,
      Data.TitleTextType.BOLD,
      -> LabelType.Body2Regular
    }
  }

  return when (this) {
    Data.TitleTextType.REGULAR -> LabelType.Body3Regular
    Data.TitleTextType.BODY2REGULAR -> LabelType.Body2Regular
    Data.TitleTextType.BODY1REGULAR -> LabelType.Body1Regular
    Data.TitleTextType.BOLD -> LabelType.Body3Bold
  }
}

@Composable
internal fun DataRowRegular(
  modifier: Modifier = Modifier,
  showBottomDivider: Boolean,
  contentHorizontalPadding: Dp = 16.dp,
  leadingContent: @Composable () -> Unit,
  trailingContent: @Composable () -> Unit,
  helperContent: (@Composable () -> Unit)?,
) {
  val lineColor =
    if (LocalTheme.current == Theme.DARK) WalletTheme.colors.foreground30 else WalletTheme.colors.foreground10

  Column(
    modifier = modifier
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = contentHorizontalPadding),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      leadingContent()
      Spacer(Modifier.width(8.dp))
      trailingContent()
    }

    helperContent?.let {
      Spacer(Modifier.height(16.dp))
      it()
    }

    if (showBottomDivider) {
      Divider(
        modifier = Modifier.padding(horizontal = contentHorizontalPadding, vertical = 12.dp),
        color = lineColor
      )
    } else if (helperContent == null) {
      // Only add spacer if we do not have a helper content.
      Spacer(Modifier.height(16.dp))
    }
  }
}
