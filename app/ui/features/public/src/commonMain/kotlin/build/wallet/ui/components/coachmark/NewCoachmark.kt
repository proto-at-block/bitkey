package build.wallet.ui.components.coachmark

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.inter_semibold
import build.wallet.ui.model.coachmark.NewCoachmarkTreatment
import build.wallet.ui.theme.WalletTheme
import org.jetbrains.compose.resources.Font

/**
 * A new label that can be used to indicate new features or content.
 *
 * @param treatment The treatment to apply to the new label.
 */
@Composable
fun NewCoachmark(treatment: NewCoachmarkTreatment) {
  Box(
    modifier = Modifier
      .clip(CircleShape)
      .background(
        when (treatment) {
          NewCoachmarkTreatment.Light -> WalletTheme.colors.newCoachmarkLightBackground
          NewCoachmarkTreatment.Dark -> WalletTheme.colors.bitkeyPrimary
          NewCoachmarkTreatment.Disabled -> WalletTheme.colors.foreground30
        }
      ).padding(horizontal = 8.dp, vertical = 3.dp)
  ) {
    Text(
      text = "New",
      style = TextStyle(
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontFamily = FontFamily(Font(Res.font.inter_semibold)),
        fontWeight = FontWeight(600),
        color = when (treatment) {
          NewCoachmarkTreatment.Light -> WalletTheme.colors.newCoachmarkLightText
          NewCoachmarkTreatment.Dark -> WalletTheme.colors.primaryForeground
          NewCoachmarkTreatment.Disabled -> Color.Gray
        },
        textAlign = TextAlign.Center
      )
    )
  }
}
