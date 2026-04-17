package build.wallet.statemachine.pricechart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import build.wallet.platform.haptics.Haptics
import build.wallet.platform.haptics.HapticsEffect
import build.wallet.pricechart.DataPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

@Composable
internal actual fun GenerateChartPointTickHapticFeedback(
  pointTickEvents: Flow<DataPoint>,
  haptics: Haptics,
) {
  LaunchedEffect(pointTickEvents) {
    pointTickEvents.collect {
      haptics.vibrate(HapticsEffect.Selection)
    }
  }
}
