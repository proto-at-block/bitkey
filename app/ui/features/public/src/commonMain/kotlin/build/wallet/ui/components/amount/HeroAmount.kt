package build.wallet.ui.components.amount

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.label.AutoResizedLabel
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.layout.CollapsedMoneyView
import build.wallet.ui.components.layout.CollapsibleLabelContainer
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.icon.IconSize.Small
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

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
  secondaryAmountWithCurrency: String?,
  hideBalance: Boolean = false,
  disabled: Boolean = false,
  onSwapClick: (() -> Unit)? = null,
) {
  CollapsibleLabelContainer(
    modifier = modifier,
    collapsed = hideBalance,
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
    topContent = {
      AutoResizedLabel(
        text = primaryAmount,
        type = primaryAmountLabelType,
        treatment =
          if (disabled) {
            LabelTreatment.Disabled
          } else {
            LabelTreatment.Primary
          }
      )
    },
    bottomContent = secondaryAmountWithCurrency?.let {
      {
        HeroAmountBottom(
          secondaryAmountWithCurrency = secondaryAmountWithCurrency,
          disabled = disabled,
          onSwapClick = onSwapClick
        )
      }
    },
    collapsedContent = { placeholder ->
      CollapsedMoneyView(
        height = 36.dp,
        modifier = Modifier,
        shimmer = !placeholder
      )
    }
  )
}

@Composable
private fun HeroAmountBottom(
  secondaryAmountWithCurrency: String?,
  disabled: Boolean = false,
  onSwapClick: (() -> Unit)? = null,
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Spacer(Modifier.height(8.dp))
    Row(
      modifier =
        Modifier
          .thenIf(onSwapClick != null) {
            Modifier.clickable {
              onSwapClick?.invoke()
            }
          },
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      AutoResizedLabel(
        text = secondaryAmountWithCurrency.orEmpty(),
        type = LabelType.Body1Medium,
        treatment =
          if (disabled) {
            LabelTreatment.Disabled
          } else {
            LabelTreatment.Secondary
          }
      )
      Spacer(Modifier.width(4.dp))
      if (onSwapClick != null) {
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
