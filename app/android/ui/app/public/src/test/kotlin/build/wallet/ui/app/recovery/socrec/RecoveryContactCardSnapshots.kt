package build.wallet.ui.app.recovery.socrec

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.bitkey.relationships.InvitationFake
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.trustedcontact.model.TrustedContactCardModel
import build.wallet.ui.app.moneyhome.card.NewCard
import io.kotest.core.spec.style.FunSpec
import kotlinx.datetime.Instant.Companion.DISTANT_FUTURE

class RecoveryContactCardSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Pending recovery contact card with inverse background and design system v2") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp)
      ) {
        NewCard(
          modifier = Modifier.fillMaxWidth(),
          model = TrustedContactCardModel(
            contact =
              InvitationFake.copy(
                trustedContactAlias = TrustedContactAlias("Bela"),
                expiresAt = DISTANT_FUTURE
              ),
            buttonText = "Pending",
            backgroundColor = CardModel.CardStyle.Gradient.BackgroundColor.InverseBackground,
            onClick = {}
          )
        )
      }
    }
  }
})
