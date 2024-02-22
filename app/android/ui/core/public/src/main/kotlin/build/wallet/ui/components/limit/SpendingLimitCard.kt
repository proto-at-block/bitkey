package build.wallet.ui.components.limit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.settings.full.mobilepay.SpendingLimitCardModel
import build.wallet.ui.components.card.Card
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment.Secondary
import build.wallet.ui.components.progress.LinearProgressIndicator
import build.wallet.ui.tokens.LabelType

@Composable
fun SpendingLimitCard(
  modifier: Modifier = Modifier,
  model: SpendingLimitCardModel,
) {
  SpendingLimitCard(
    modifier = modifier,
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
  titleText: String,
  resetText: String,
  progress: Float,
  spentText: String,
  remainingText: String,
) {
  Card(
    modifier = modifier,
    paddingValues = PaddingValues(20.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Label(text = titleText, type = LabelType.Title2)
      Label(text = resetText, type = LabelType.Body4Regular, treatment = Secondary)
    }
    Spacer(modifier = Modifier.height(12.dp))
    LinearProgressIndicator(
      modifier = Modifier.fillMaxWidth(),
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

@Preview
@Composable
internal fun PreviewSpendingLimitCard() {
  SpendingLimitCard(
    modifier = Modifier.fillMaxWidth(),
    model =
      SpendingLimitCardModel(
        dailyResetTimezoneText = "Resets at 3:00am PDT",
        spentAmountText = "$50.00 spent",
        remainingAmountText = "$50.00 remaining",
        progressPercentage = .5f
      )
  )
}
