package build.wallet.ui.app.recovery.socrec

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.bitkey.relationships.InvitationFake
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.trustedcontact.model.TrustedContactCardModel
import build.wallet.ui.app.moneyhome.card.StatusCard
import io.kotest.core.spec.style.FunSpec
import kotlinx.datetime.Instant.Companion.DISTANT_FUTURE

class RecoveryContactCardSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Pending recovery contact card with inverse background") {
    paparazzi.snapshot {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp)
      ) {
        StatusCard(
          modifier = Modifier.fillMaxWidth(),
          model = TrustedContactCardModel(
            contact =
              InvitationFake.copy(
                trustedContactAlias = TrustedContactAlias("Bela"),
                expiresAt = DISTANT_FUTURE
              ),
            buttonText = "Pending",
            inverse = true,
            onClick = {}
          )
        )
      }
    }
  }
})
