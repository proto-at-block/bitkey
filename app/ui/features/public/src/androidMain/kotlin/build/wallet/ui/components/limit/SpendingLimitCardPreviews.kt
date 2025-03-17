package build.wallet.ui.components.limit

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.settings.full.mobilepay.SpendingLimitCardModel

@Preview
@Composable
fun PreviewSpendingLimitCard() {
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
