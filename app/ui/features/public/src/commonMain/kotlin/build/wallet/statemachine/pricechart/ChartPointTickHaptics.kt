package build.wallet.statemachine.pricechart

import androidx.compose.runtime.Composable
import build.wallet.platform.haptics.Haptics
import build.wallet.pricechart.DataPoint
import kotlinx.coroutines.flow.Flow

@Composable
internal expect fun GenerateChartPointTickHapticFeedback(
  pointTickEvents: Flow<DataPoint>,
  haptics: Haptics,
)
