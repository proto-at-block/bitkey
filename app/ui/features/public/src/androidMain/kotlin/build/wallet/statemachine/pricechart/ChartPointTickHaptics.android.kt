package build.wallet.statemachine.pricechart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import build.wallet.platform.haptics.Haptics
import build.wallet.pricechart.DataPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

@Composable
internal actual fun GenerateChartPointTickHapticFeedback(
  pointTickEvents: Flow<DataPoint>,
  @Suppress("UNUSED_PARAMETER") haptics: Haptics,
) {
  // Android uses LocalHapticFeedback for SegmentFrequentTick instead of the
  // common Haptics abstraction, which does not expose this effect.
  val hapticFeedback = LocalHapticFeedback.current

  LaunchedEffect(pointTickEvents) {
    pointTickEvents.collect {
      hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
  }
}
