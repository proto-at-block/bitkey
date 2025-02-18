package bitkey.sample.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.label.Label
import build.wallet.ui.model.StandardClick
import kotlinx.collections.immutable.ImmutableList

data class SettingsListBodyModel(
  override val onBack: () -> Unit,
  val rows: ImmutableList<SettingsRowModel>,
) : BodyModel() {
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null

  @Composable
  override fun render(modifier: Modifier) {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Label("Settings")
      rows.forEach { row ->
        Button(text = row.title, onClick = StandardClick(row.onClick))
      }
      Spacer(modifier = Modifier.height(16.dp))
      Button(text = "Go back", onClick = StandardClick(onBack))
    }
  }

  data class SettingsRowModel(
    val title: String,
    val onClick: () -> Unit,
  )
}
