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
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.bitkey_full_logo
import bitkey.ui.framework_public.generated.resources.bitkey_logo_mark
import bitkey.ui.framework_public.generated.resources.bitkey_word_mark
import build.wallet.statemachine.core.SplashLockModel
import build.wallet.ui.app.loading.LogoMarkHeight
import build.wallet.ui.app.loading.WordMarkHeight
import build.wallet.ui.app.loading.WordMarkTopPadding
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
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
      .systemBarsPadding()
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    val isDesignSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current
    if (isDesignSystemV2Enabled) {
      Row(
        modifier = Modifier
          .padding(horizontal = 20.dp, vertical = 52.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        androidx.compose.foundation.Image(
          modifier = Modifier.height(LogoMarkHeight),
          painter = painterResource(Res.drawable.bitkey_logo_mark),
          contentDescription = "Bitkey Logo",
          colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White.copy(alpha = 0.5F))
        )
        androidx.compose.foundation.Image(
          modifier = Modifier
            .padding(top = WordMarkTopPadding)
            .height(WordMarkHeight),
          painter = painterResource(Res.drawable.bitkey_word_mark),
          contentDescription = null,
          colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White.copy(alpha = 0.5F))
        )
      }
    } else {
      Box(
        modifier = Modifier
          .padding(horizontal = 20.dp, vertical = 52.dp)
          .height(25.dp)
      ) {
        androidx.compose.foundation.Image(
          painter = painterResource(Res.drawable.bitkey_full_logo),
          contentDescription = "Bitkey Logo",
          colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White.copy(alpha = 0.5F))
        )
      }
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
