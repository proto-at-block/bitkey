package build.wallet.ui.app.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.bitkey_icon_mark
import build.wallet.statemachine.core.SplashLockModel
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import org.jetbrains.compose.resources.painterResource

@Composable
fun SplashLockScreen(
  modifier: Modifier = Modifier,
  model: SplashLockModel,
) {
  val lockupTint = Color(0xFF808080)
  Column(
    modifier = modifier
      .background(Color.Black)
      .padding(horizontal = 20.dp)
      .systemBarsPadding()
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 40.dp)
    ) {
      androidx.compose.foundation.Image(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .size(48.dp),
        painter = painterResource(Res.drawable.bitkey_icon_mark),
        contentDescription = "Bitkey Icon Mark",
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(lockupTint)
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
        modifier = Modifier.size(80.dp),
        icon = build.wallet.statemachine.core.Icon.DotSecurity,
        size = IconSize.XLarge,
        color = lockupTint
      )
      Spacer(modifier = Modifier.height(8.dp))
      Label(
        text = "Locked",
        type = LabelType.Body3Mono,
        color = lockupTint,
        treatment = LabelTreatment.Unspecified,
        alignment = TextAlign.Center
      )
    }
    Spacer(Modifier.height(24.dp))
    CompositionLocalProvider(LocalTheme provides Theme.DARK) {
      WalletTheme {
        Button(
          modifier = Modifier.padding(horizontal = 4.dp),
          model = model.unlockButtonModel
        )
      }
    }
    Spacer(Modifier.height(28.dp))
  }
}
