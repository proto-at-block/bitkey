package build.wallet.ui.app.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.android.ui.core.R
import build.wallet.statemachine.account.BitkeyGetStartedModel
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.compose.resId
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
fun BitkeyGetStartedScreen(model: BitkeyGetStartedModel) {
  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(WalletTheme.colors.bitkeyGetStartedBackground),
    contentAlignment = Alignment.Center
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(horizontal = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Bottom
    ) {
      Button(model = model.getStartedButtonModel)
      Spacer(modifier = Modifier.height(42.dp))
    }
    Box(
      modifier =
        Modifier
          .align(Companion.Center)
          .resId("logo")
          .clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
          ) { model.onLogoClick() }
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
          modifier = Modifier.width(160.dp),
          painter = painterResource(R.drawable.bitkey_full_logo),
          contentDescription = "Bitkey Logo",
          tint = WalletTheme.colors.bitkeyGetStartedTint
        )
        Label(
          text = "Take Back Your Bitcoin",
          type = LabelType.Title1,
          treatment = LabelTreatment.Unspecified,
          color = WalletTheme.colors.bitkeyGetStartedTint
        )
      }
    }
  }
}

@Preview
@Composable
internal fun BitkeyGetStartedScreenPreview() {
  PreviewWalletTheme {
    BitkeyGetStartedScreen(
      model =
        BitkeyGetStartedModel(
          onLogoClick = {},
          onGetStartedClick = {}
        )
    )
  }
}
