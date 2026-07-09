package build.wallet.ui.app.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.Icon
import build.wallet.ui.app.moneyhome.receive.PartnerActionButton
import build.wallet.ui.components.button.Button
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.theme.WalletTheme
import io.kotest.core.spec.style.FunSpec

class ButtonLoadingSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("loading buttons") {
    paparazzi.snapshot {
      Box(
        modifier =
          Modifier
            .background(WalletTheme.colors.background)
            .padding(24.dp)
      ) {
        Column(
          verticalArrangement = Arrangement.spacedBy(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Button(
            text = "Continue",
            treatment = ButtonModel.Treatment.Primary,
            size = ButtonModel.Size.Regular,
            isLoading = true,
            onClick = StandardClick {}
          )

          PartnerActionButton(
            iconModel = IconModel(
              icon = Icon.Copy,
              iconSize = IconSize.Small,
              iconTint = IconTint.Foreground
            ),
            text = "Copy",
            onClick = {},
            isLoading = true
          )
        }
      }
    }
  }
})
