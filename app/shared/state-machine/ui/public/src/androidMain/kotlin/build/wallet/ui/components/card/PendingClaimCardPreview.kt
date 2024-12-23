package build.wallet.ui.components.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.Progress
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.ui.app.moneyhome.card.MoneyHomeCard
import build.wallet.ui.theme.WalletTheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

@Preview
@Composable
fun PendingClaimCardPreview() {
  Box(
    modifier =
      Modifier
        .background(color = WalletTheme.colors.background)
        .padding(24.dp)
  ) {
    Column {
      MoneyHomeCard(
        model =
          CardModel(
            title = null,
            content = CardModel.CardContent.PendingClaim(
              title = "Inheritance claim pending",
              subtitle = "Funds available 11/22/2024",
              isPendingClaim = true,
              timeRemaining = 1.days,
              progress = Progress.Half,
              onClick = null
            ),
            style = CardModel.CardStyle.Plain
          )
      )

      Spacer(modifier = Modifier.padding(8.dp))

      MoneyHomeCard(
        model =
          CardModel(
            title = null,
            content = CardModel.CardContent.PendingClaim(
              title = "Claim approved",
              subtitle = "Transfer funds now.",
              isPendingClaim = false,
              timeRemaining = Duration.ZERO,
              progress = Progress.Full,
              onClick = null
            ),
            style = CardModel.CardStyle.Plain
          )
      )
    }
  }
}
