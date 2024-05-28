package build.wallet.ui.components.layout

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import build.wallet.android.ui.core.R
import build.wallet.ui.components.label.shimmer

@Composable
internal fun CollapsedMoneyView(
  height: Dp,
  modifier: Modifier = Modifier,
) {
  Image(
    painter = painterResource(R.drawable.hidden_hero_asterisk),
    contentDescription = "value is hidden",
    contentScale = ContentScale.FillHeight,
    alignment = Alignment.Center,
    modifier =
      modifier
        .height(height)
        .wrapContentWidth(align = Alignment.CenterHorizontally)
        .shimmer()
  )
}
