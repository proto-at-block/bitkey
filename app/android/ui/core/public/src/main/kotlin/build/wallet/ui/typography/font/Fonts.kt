package build.wallet.ui.typography.font

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import build.wallet.android.ui.core.R

internal val interFontFamily =
  FontFamily(
    Font(
      resId = R.font.inter_bold,
      weight = FontWeight.Bold,
      style = FontStyle.Normal
    ),
    Font(
      resId = R.font.inter_medium,
      weight = FontWeight.Medium,
      style = FontStyle.Normal
    ),
    Font(
      resId = R.font.inter_regular,
      weight = FontWeight.Normal,
      style = FontStyle.Normal
    ),
    Font(
      resId = R.font.inter_semibold,
      weight = FontWeight.SemiBold,
      style = FontStyle.Normal
    )
  )

internal val robotoMonoFontFamily =
  FontFamily(
    Font(
      resId = R.font.roboto_mono,
      weight = FontWeight.Normal,
      style = FontStyle.Normal
    )
  )
