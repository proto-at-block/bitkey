package build.wallet.ui.components.limit

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.settings.full.mobilepay.SpendingLimitCardModel
import build.wallet.ui.components.card.Card
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment.Secondary
import build.wallet.ui.components.progress.LinearProgressIndicator
import build.wallet.ui.model.icon.IconSize.Regular
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.tokens.LabelType
import build.wallet.statemachine.core.Icon as WalletIcon

@Composable
fun SpendingLimitCard(
  modifier: Modifier = Modifier,
  model: SpendingLimitCardModel,
  icon: WalletIcon? = null,
) {
  SpendingLimitCard(
    modifier = modifier,
    icon = icon,
    titleText = model.titleText,
    resetText = model.dailyResetTimezoneText,
    progress = model.progressPercentage,
    spentText = model.spentAmountText,
    remainingText = model.remainingAmountText
  )
}

@Composable
fun SpendingLimitCard(
  modifier: Modifier = Modifier,
  icon: WalletIcon? = null,
  titleText: String,
  resetText: String,
  // TODO(W-8034): use Progress type.
  progress: Float,
  spentText: String,
  remainingText: String,
) {
  val isDesignSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current

  if (isDesignSystemV2Enabled) {
    SpendingLimitCardContent(
      modifier = modifier.fillMaxWidth(),
      icon = icon,
      titleText = titleText,
      titleType = LabelType.Body2MonoCaps,
      resetText = resetText,
      progress = progress,
      spentText = spentText,
      remainingText = remainingText
    )
  } else {
    Card(
      modifier = modifier,
      paddingValues = PaddingValues(20.dp)
    ) {
      SpendingLimitCardContent(
        modifier = Modifier.fillMaxWidth(),
        icon = null,
        titleText = titleText,
        titleType = LabelType.Title2,
        resetText = resetText,
        progress = progress,
        spentText = spentText,
        remainingText = remainingText
      )
    }
  }
}

@Composable
private fun SpendingLimitCardContent(
  modifier: Modifier = Modifier,
  icon: WalletIcon?,
  titleText: String,
  titleType: LabelType,
  resetText: String,
  progress: Float,
  spentText: String,
  remainingText: String,
) {
  Column(modifier = modifier) {
    icon?.let { currentIcon ->
      Icon(
        icon = currentIcon,
        size = Regular
      )
      Spacer(modifier = Modifier.height(12.dp))
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Label(text = titleText, type = titleType)
      Label(text = resetText, type = LabelType.Body4Regular, treatment = Secondary)
    }
    Spacer(modifier = Modifier.height(12.dp))
    LinearProgressIndicator(
      progress = progress
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Label(text = spentText, type = LabelType.Body4Regular, treatment = Secondary)
      Label(text = remainingText, type = LabelType.Body4Medium, treatment = Secondary)
    }
  }
}
