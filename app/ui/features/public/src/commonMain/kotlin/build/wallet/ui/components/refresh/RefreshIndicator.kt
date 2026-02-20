package build.wallet.ui.components.refresh

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import build.wallet.ui.theme.WalletTheme

/**
 * A Box that provides pull-to-refresh functionality.
 * The indicator is automatically shown at the top center.
 *
 * @param refreshing Whether a refresh is currently occurring
 * @param onRefresh Callback when user triggers a refresh
 * @param modifier Modifier to be applied to the Box
 * @param content The scrollable content
 */
@Composable
fun PullToRefreshBox(
  refreshing: Boolean,
  onRefresh: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit,
) {
  val state = rememberPullToRefreshState()

  Box(
    modifier = modifier.pullToRefresh(
      state = state,
      isRefreshing = refreshing,
      onRefresh = onRefresh
    )
  ) {
    content()

    PullToRefreshDefaults.Indicator(
      state = state,
      isRefreshing = refreshing,
      modifier = Modifier.align(Alignment.TopCenter),
      containerColor = WalletTheme.colors.refreshIndicatorBackground,
      color = WalletTheme.colors.refreshIndicatorContent
    )
  }
}
