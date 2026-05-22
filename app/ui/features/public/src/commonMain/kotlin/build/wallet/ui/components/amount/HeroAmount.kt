package build.wallet.ui.components.amount

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.label.AnimatedAmount
import build.wallet.ui.components.label.AnimatedAmountAutoResizedLabel
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.AutoResizedLabel
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.components.label.loadingScrim
import build.wallet.ui.components.layout.CollapsedMoneyView
import build.wallet.ui.components.layout.CollapsibleLabelContainer
import build.wallet.ui.components.layout.MeasureWithoutPlacement
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconSize.Small
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tokens.market.MarketIcons

/**
 * Helper extension to hide content when loading by setting alpha to 0.
 */
private fun Modifier.hideWhenLoading(isLoading: Boolean): Modifier =
  if (isLoading) alpha(0f) else this

/**
 * @param onSwapClick: When nonnull, a swap icon will be shown to the right
 * of the secondary amount and tapping on the secondary amount or swap icon
 * will trigger this callback.
 */
@Composable
fun HeroAmount(
  modifier: Modifier = Modifier,
  primaryAmount: AnnotatedString,
  primaryAmountValue: Long? = null,
  primaryAmountAnimationKey: Long = 0L,
  primaryAmountLabelType: LabelType = LabelType.Display2,
  contextLine: String?,
  contextLineTreatment: LabelTreatment = LabelTreatment.Secondary,
  hideBalance: Boolean = false,
  disabled: Boolean = false,
  centerWhenDesignSystemV2: Boolean = false,
  onSwapClick: (() -> Unit)? = null,
  isLoading: Boolean = false,
  animateValueChanges: Boolean = false,
) {
  val isDesignSystemV2Enabled = true
  HeroAmountContainer(
    modifier = modifier,
    topContent = {
      Box(
        modifier = Modifier
          .wrapContentSize()
          .loadingScrim(isLoading),
        contentAlignment = Alignment.Center
      ) {
        if (isLoading) {
          MeasureWithoutPlacement {
            AutoResizedLabel(
              text = AnnotatedString("$88,888"),
              type = primaryAmountLabelType,
              treatment = LabelTreatment.Primary
            )
          }
        }
        if (isDesignSystemV2Enabled && primaryAmountValue != null && primaryAmount.hasNoInlineStyles()) {
          val primaryTreatment =
            if (disabled) {
              LabelTreatment.Disabled
            } else {
              LabelTreatment.Primary
            }
          AnimatedAmountAutoResizedLabel(
            modifier = Modifier.hideWhenLoading(isLoading),
            amount = AnimatedAmount(
              text = primaryAmount.text,
              value = primaryAmountValue,
              animationKey = primaryAmountAnimationKey
            ),
            type = primaryAmountLabelType,
            animate = animateValueChanges,
            animationLabel = "HeroAmountPrimary",
            treatment = primaryTreatment,
            minTextSize = heroAmountMinTextSize(primaryAmountLabelType, primaryTreatment)
          )
        } else {
          AutoResizedLabel(
            modifier = Modifier.hideWhenLoading(isLoading),
            text = primaryAmount,
            type = primaryAmountLabelType,
            treatment =
              if (disabled) {
                LabelTreatment.Disabled
              } else {
                LabelTreatment.Primary
              }
          )
        }
      }
    },
    contextLine = contextLine,
    contextLineTreatment = contextLineTreatment,
    hideBalance = hideBalance,
    disabled = disabled,
    centerWhenDesignSystemV2 = centerWhenDesignSystemV2,
    onSwapClick = onSwapClick,
    isLoading = isLoading
  )
}

@Composable
internal fun HeroAmountContainer(
  modifier: Modifier = Modifier,
  contextLine: String?,
  contextLineTreatment: LabelTreatment,
  hideBalance: Boolean,
  disabled: Boolean,
  centerWhenDesignSystemV2: Boolean,
  onSwapClick: (() -> Unit)?,
  isLoading: Boolean,
  topContent: @Composable () -> Unit,
) {
  val isDesignSystemV2Enabled = true
  val shouldUseStartAlignment = isDesignSystemV2Enabled && !centerWhenDesignSystemV2
  val horizontalAlignment = if (shouldUseStartAlignment) Alignment.Start else Alignment.CenterHorizontally

  CollapsibleLabelContainer(
    modifier = modifier,
    collapsed = hideBalance,
    verticalArrangement = Arrangement.spacedBy((-4).dp),
    horizontalAlignment = horizontalAlignment,
    topContent = { topContent() },
    bottomContent = {
      if (contextLine != null) {
        HeroAmountBottom(
          contextLine = contextLine,
          contextLineTreatment = contextLineTreatment,
          disabled = disabled,
          centerWhenDesignSystemV2 = centerWhenDesignSystemV2,
          onSwapClick = onSwapClick,
          isLoading = isLoading
        )
      } else {
        Spacer(
          Modifier.height(
            heroAmountBottomHeight(
              contextLineTreatment =
                if (disabled) {
                  LabelTreatment.Disabled
                } else {
                  contextLineTreatment
                }
            )
          )
        )
      }
    },
    collapsedContent = { placeholder ->
      CollapsedMoneyView(
        height = 42.dp,
        modifier = Modifier,
        shimmer = !placeholder
      )
    }
  )
}

@Composable
internal fun HeroAmountBottom(
  contextLine: String,
  contextLineTreatment: LabelTreatment = LabelTreatment.Secondary,
  disabled: Boolean = false,
  centerWhenDesignSystemV2: Boolean = false,
  onSwapClick: (() -> Unit)? = null,
  isLoading: Boolean = false,
) {
  val isDesignSystemV2Enabled = true
  val shouldUseStartAlignment = isDesignSystemV2Enabled && !centerWhenDesignSystemV2
  val columnAlignment = if (shouldUseStartAlignment) Alignment.Start else Alignment.CenterHorizontally
  val rowArrangement = if (shouldUseStartAlignment) Arrangement.Start else Arrangement.Center

  Column(horizontalAlignment = columnAlignment) {
    Spacer(Modifier.height(2.dp))
    Row(
      modifier = Modifier
        .thenIf(onSwapClick != null) {
          Modifier.clickable {
            onSwapClick?.invoke()
          }
        },
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = rowArrangement
    ) {
      Box(
        modifier = Modifier.loadingScrim(isLoading),
        contentAlignment = Alignment.Center
      ) {
        if (isLoading) {
          MeasureWithoutPlacement {
            AutoResizedLabel(
              text = "88,888 sats",
              type = LabelType.Body1Medium,
              treatment = contextLineTreatment
            )
          }
        }
        AutoResizedLabel(
          modifier = Modifier.hideWhenLoading(isLoading),
          text = contextLine,
          type = LabelType.Body1Medium,
          treatment =
            if (disabled) {
              LabelTreatment.Disabled
            } else {
              contextLineTreatment
            }
        )
      }
      if (onSwapClick != null) {
        Spacer(Modifier.width(4.dp))
        val iconColor = if (disabled) WalletTheme.colors.foreground10 else WalletTheme.colors.foreground60
        if (isDesignSystemV2Enabled) {
          IconImage(
            modifier = Modifier.rotate(90f),
            model = IconModel(
              icon = MarketIcons.DualRotatingArrows,
              iconSize = IconSize.Custom(20)
            ),
            color = iconColor
          )
        } else {
          Icon(
            icon = Icon.SmallIconSwap,
            size = Small,
            color = iconColor
          )
        }
      }
    }
  }
}

@Composable
private fun heroAmountBottomHeight(
  contextLineTreatment: LabelTreatment,
): Dp {
  val contextLineHeight = with(LocalDensity.current) {
    WalletTheme.labelStyle(
      type = LabelType.Body1Medium,
      treatment = contextLineTreatment
    ).lineHeight.toDp()
  }
  return 2.dp + maxOf(contextLineHeight, 20.dp)
}

@Composable
private fun heroAmountMinTextSize(
  primaryAmountLabelType: LabelType,
  treatment: LabelTreatment,
): TextUnit {
  val minLabelType =
    when (primaryAmountLabelType) {
      LabelType.Display1,
      LabelType.Display2,
      -> LabelType.Title1
      LabelType.Display3 -> LabelType.Title2
      else -> LabelType.Body1Regular
    }
  return WalletTheme.labelStyle(
    type = minLabelType,
    treatment = treatment
  ).fontSize
}

private fun AnnotatedString.hasNoInlineStyles(): Boolean {
  return spanStyles.isEmpty() && paragraphStyles.isEmpty()
}
