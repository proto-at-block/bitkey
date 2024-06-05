package build.wallet.ui.app.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.android.ui.core.R
import build.wallet.statemachine.core.SplashLockModel
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
fun SplashLockScreen(model: SplashLockModel) {
  Column(
    modifier =
      Modifier
        .background(Color.Black)
        .padding(horizontal = 20.dp)
        .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Box(
      modifier = Modifier
        .padding(horizontal = 20.dp, vertical = 52.dp)
        .height(25.dp)
    ) {
      androidx.compose.material3.Icon(
        painter = painterResource(R.drawable.bitkey_full_logo),
        contentDescription = "Bitkey Logo",
        tint = Color.White.copy(alpha = 0.2F)
      )
    }
    Column(
      modifier =
        Modifier
          .verticalScroll(rememberScrollState())
          .weight(1F),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Icon(
        icon = build.wallet.statemachine.core.Icon.SmallIconLock,
        tint = IconTint.OnTranslucent,
        opacity = 0.2F,
        size = IconSize.Large
      )
      Label(
        text = "Locked",
        type = LabelType.Body1Medium,
        color = Color.White.copy(alpha = 0.2F),
        treatment = LabelTreatment.Disabled
      )
    }
    Spacer(Modifier.height(24.dp))
    Button(model.unlockButtonModel)
    Spacer(Modifier.height(28.dp))
  }
}

@Composable
@Preview
fun SplashLockScreenPreview() {
  PreviewWalletTheme {
    SplashLockScreen(
      SplashLockModel(
        unlockButtonModel = ButtonModel(
          text = "Unlock",
          treatment = ButtonModel.Treatment.Translucent,
          size = ButtonModel.Size.Footer,
          onClick = StandardClick {}
        )
      )
    )
  }
}
