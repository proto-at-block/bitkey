package build.wallet.ui.components.refresh

import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import build.wallet.ui.theme.WalletTheme

@Composable
fun PullRefreshIndicator(
  modifier: Modifier = Modifier,
  refreshing: Boolean,
  onRefresh: () -> Unit,
) {
  PullRefreshIndicator(
    refreshing = refreshing,
    state = rememberPullRefreshState(refreshing, { onRefresh() }),
    modifier = modifier,
    backgroundColor = WalletTheme.colors.refreshIndicatorBackground,
    contentColor = WalletTheme.colors.refreshIndicatorContent
  )
}

fun Modifier.pullRefresh(
  refreshing: Boolean,
  onRefresh: () -> Unit,
): Modifier =
  composed {
    pullRefresh(
      state = rememberPullRefreshState(refreshing, { onRefresh() })
    )
  }
