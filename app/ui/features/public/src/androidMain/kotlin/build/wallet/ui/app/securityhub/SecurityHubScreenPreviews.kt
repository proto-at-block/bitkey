package build.wallet.ui.app.securityhub

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import bitkey.ui.SnapshotHost
import bitkey.ui.screens.securityhub.pendingRecommendations
import build.wallet.ui.model.render
import build.wallet.ui.theme.Theme
import build.wallet.ui.tooling.PreviewWalletTheme
import kotlinx.collections.immutable.toImmutableList

@Preview(name = "Security Hub Light")
@Composable
fun SecurityHubPreviewLight() {
  SecurityHubPreview(theme = Theme.LIGHT)
}

@Preview(name = "Security Hub Dark")
@Composable
fun SecurityHubPreviewDark() {
  SecurityHubPreview(theme = Theme.DARK)
}

@Composable
private fun SecurityHubPreview(theme: Theme) {
  PreviewWalletTheme(
    theme = theme,
  ) {
    val model = SnapshotHost.pendingRecommendations
    model.copy(
      recommendations = model.recommendations.take(3).toImmutableList()
    ).render()
  }
}
