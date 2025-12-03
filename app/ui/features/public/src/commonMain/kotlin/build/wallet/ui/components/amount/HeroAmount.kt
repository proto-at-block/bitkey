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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.label.AutoResizedLabel
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.loadingScrim
import build.wallet.ui.components.layout.CollapsedMoneyView
import build.wallet.ui.components.layout.CollapsibleLabelContainer
import build.wallet.ui.components.layout.MeasureWithoutPlacement
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.icon.IconSize.Small
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

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
  primaryAmountLabelType: LabelType = LabelType.Display2,
  contextLine: String?,
  hideBalance: Boolean = false,
  disabled: Boolean = false,
  onSwapClick: (() -> Unit)? = null,
  isLoading: Boolean = false,
) {
  CollapsibleLabelContainer(
    modifier = modifier,
    collapsed = hideBalance,
    verticalArrangement = Arrangement.spacedBy((-4).dp),
    horizontalAlignment = Alignment.CenterHorizontally,
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
    },
    bottomContent = contextLine?.let {
      {
        HeroAmountBottom(
          contextLine = contextLine,
          disabled = disabled,
          onSwapClick = onSwapClick,
          isLoading = isLoading
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
private fun HeroAmountBottom(
  contextLine: String?,
  disabled: Boolean = false,
  onSwapClick: (() -> Unit)? = null,
  isLoading: Boolean = false,
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Spacer(Modifier.height(2.dp))
    Row(
      modifier = Modifier
        .thenIf(onSwapClick != null) {
          Modifier.clickable {
            onSwapClick?.invoke()
          }
        },
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
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
              treatment = LabelTreatment.Secondary
            )
          }
        }
        AutoResizedLabel(
          modifier = Modifier.hideWhenLoading(isLoading),
          text = contextLine.orEmpty(),
          type = LabelType.Body1Medium,
          treatment =
            if (disabled) {
              LabelTreatment.Disabled
            } else {
              LabelTreatment.Secondary
            }
        )
      }
      if (onSwapClick != null) {
        Spacer(Modifier.width(4.dp))
        Icon(
          icon = Icon.SmallIconSwap,
          size = Small,
          color =
            if (disabled) {
              WalletTheme.colors.foreground10
            } else {
              WalletTheme.colors.foreground60
            }
        )
      }
    }
  }
}
