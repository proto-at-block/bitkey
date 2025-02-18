package build.wallet.ui.typography.font

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import bitkey.shared.ui_core_public.generated.resources.*
import bitkey.shared.ui_core_public.generated.resources.Res
import bitkey.shared.ui_core_public.generated.resources.inter_bold
import bitkey.shared.ui_core_public.generated.resources.inter_medium
import bitkey.shared.ui_core_public.generated.resources.inter_regular
import org.jetbrains.compose.resources.Font

internal val interFontFamily: FontFamily
  @Composable
  get() = FontFamily(
    Font(
      resource = Res.font.inter_bold,
      weight = FontWeight.Bold,
      style = FontStyle.Normal
    ),
    Font(
      resource = Res.font.inter_medium,
      weight = FontWeight.Medium,
      style = FontStyle.Normal
    ),
    Font(
      resource = Res.font.inter_regular,
      weight = FontWeight.Normal,
      style = FontStyle.Normal
    ),
    Font(
      resource = Res.font.inter_semibold,
      weight = FontWeight.SemiBold,
      style = FontStyle.Normal
    )
  )

internal val robotoMonoFontFamily: FontFamily
  @Composable
  get() = FontFamily(
    Font(
      resource = Res.font.roboto_mono,
      weight = FontWeight.Normal,
      style = FontStyle.Normal
    )
  )

internal val foundersGroteskFontFamily: FontFamily
  @Composable
  get() = FontFamily(
    Font(
      resource = Res.font.founders_grotesk_x_condensed_bold,
      weight = FontWeight.W700,
      style = FontStyle.Normal
    )
  )
