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
import bitkey.shared.ui_core_public.generated.resources.Res
import bitkey.shared.ui_core_public.generated.resources.inter_semibold
import build.wallet.ui.model.coachmark.NewCoachmarkTreatment
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tooling.PreviewWalletTheme
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.ui.tooling.preview.Preview

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
          NewCoachmarkTreatment.Light -> WalletTheme.colors.bitkeyPrimary.copy(0.10f)
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
          NewCoachmarkTreatment.Light -> WalletTheme.colors.bitkeyPrimary
          NewCoachmarkTreatment.Dark -> Color.White
          NewCoachmarkTreatment.Disabled -> Color.Gray
        },
        textAlign = TextAlign.Center
      )
    )
  }
}

@Preview
@Composable
internal fun NewLabelPreviews() {
  PreviewWalletTheme {
    Box(modifier = Modifier.padding(16.dp)) {
      Column {
        NewCoachmark(NewCoachmarkTreatment.Light)
        Spacer(Modifier.height(8.dp))
        NewCoachmark(NewCoachmarkTreatment.Dark)
        Spacer(Modifier.height(8.dp))
        NewCoachmark(NewCoachmarkTreatment.Disabled)
      }
    }
  }
}
