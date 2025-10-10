package build.wallet.ui.components.coachmark

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.inter_semibold
import build.wallet.ui.model.coachmark.CoachmarkLabelTreatment
import build.wallet.ui.model.list.CoachmarkLabelModel
import build.wallet.ui.theme.WalletTheme
import org.jetbrains.compose.resources.Font

/**
 * A label that can be used to indicate features, updates, or content status.
 *
 * @param model The coachmark model containing the text and treatment to display.
 */
@Composable
fun CoachmarkLabel(model: CoachmarkLabelModel) {
  Box(
    modifier = Modifier
      .clip(CircleShape)
      .background(
        when (model.treatment) {
          CoachmarkLabelTreatment.Light -> WalletTheme.colors.newCoachmarkLightBackground
          CoachmarkLabelTreatment.Dark -> WalletTheme.colors.bitkeyPrimary
          CoachmarkLabelTreatment.Disabled -> WalletTheme.colors.subtleBackground
        }
      ).padding(horizontal = 8.dp, vertical = 3.dp)
  ) {
    Text(
      text = model.text,
      style = TextStyle(
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontFamily = FontFamily(Font(Res.font.inter_semibold)),
        fontWeight = FontWeight(600),
        color = when (model.treatment) {
          CoachmarkLabelTreatment.Light -> WalletTheme.colors.newCoachmarkLightText
          CoachmarkLabelTreatment.Dark -> WalletTheme.colors.primaryForeground
          CoachmarkLabelTreatment.Disabled -> WalletTheme.colors.foreground10
        },
        textAlign = TextAlign.Center
      )
    )
  }
}
