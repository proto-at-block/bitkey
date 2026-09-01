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
import build.wallet.ui.app.moneyhome.card.Card
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
      Card(
        model =
          CardModel.PendingClaim(
            id = "preview-pending",
            title = "Inheritance claim pending",
            subtitle = "Funds available 11/22/2024",
            state = CardModel.PendingClaim.State.Pending,
            timeRemaining = 1.days,
            progress = Progress.Half,
            onClick = { }
          )
      )

      Spacer(modifier = Modifier.padding(8.dp))

      Card(
        model =
          CardModel.PendingClaim(
            id = "preview-approved",
            title = "Claim approved",
            subtitle = "Transfer funds now.",
            state = CardModel.PendingClaim.State.Locked,
            timeRemaining = Duration.ZERO,
            progress = Progress.Full,
            onClick = null
          )
      )
    }
  }
}
