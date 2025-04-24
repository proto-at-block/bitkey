package build.wallet.ui.components.coachmark

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import build.wallet.ui.model.coachmark.CoachmarkModel

/**
 * Presenter composable to help display a popover coachmark (Coachmark.kt)
 * @param yOffset The y offset to apply to the coachmark.
 * @param model The model to use to display the coachmark.
 */
@Composable
fun CoachmarkPresenter(
  yOffset: Float,
  model: CoachmarkModel,
  renderedSize: (IntSize) -> Unit = {},
) {
  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .zIndex(Float.MAX_VALUE)
        .background(Color.Transparent)
  ) {
    Coachmark(
      modifier = Modifier.onGloballyPositioned { layoutCoordinates ->
        renderedSize(layoutCoordinates.size)
      },
      model = model,
      offset = Offset(0f, yOffset)
    )
  }
}
