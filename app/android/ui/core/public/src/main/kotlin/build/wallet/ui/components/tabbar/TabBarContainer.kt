package build.wallet.ui.components.tabbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun TabBarContainer(
  modifier: Modifier = Modifier,
  statusBannerContent: @Composable (() -> Unit)? = null,
  bodyContent: @Composable () -> Unit,
  tabBarContent: @Composable (() -> Unit)? = null,
) {
  Column(modifier = modifier.fillMaxSize()) {
    statusBannerContent?.let {
      statusBannerContent()
    }
    Box(modifier = Modifier.weight(1F)) {
      bodyContent()
    }
    tabBarContent?.let {
      tabBarContent()
    }
  }
}
