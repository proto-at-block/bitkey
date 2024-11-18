package build.wallet.ui.app.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import bitkey.shared.ui_core_public.generated.resources.Res
import bitkey.shared.ui_core_public.generated.resources.bitkey_full_logo
import build.wallet.statemachine.core.SplashLockModel
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.tokens.LabelType
import org.jetbrains.compose.resources.painterResource

@Composable
fun SplashLockScreen(
  modifier: Modifier = Modifier,
  model: SplashLockModel,
) {
  Column(
    modifier = modifier
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
        painter = painterResource(Res.drawable.bitkey_full_logo),
        contentDescription = "Bitkey Logo",
        tint = Color.White.copy(alpha = 0.5F)
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
        opacity = 0.5F,
        size = IconSize.Large
      )
      Label(
        text = "Locked",
        type = LabelType.Body1Medium,
        color = Color.White.copy(alpha = 0.5F),
        treatment = LabelTreatment.Unspecified
      )
    }
    Spacer(Modifier.height(24.dp))
    Button(
      modifier = Modifier.padding(horizontal = 4.dp),
      model = model.unlockButtonModel
    )
    Spacer(Modifier.height(28.dp))
  }
}
