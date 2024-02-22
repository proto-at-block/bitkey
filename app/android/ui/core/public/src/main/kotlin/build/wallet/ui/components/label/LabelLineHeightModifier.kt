package build.wallet.ui.components.label

import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Applies [LabelLineHeightModifier].
 */
@Stable
@Suppress("ModifierInspectorInfo")
internal fun Modifier.lineHeight(lineHeight: TextUnit) =
  this.then(LabelLineHeightModifier(lineHeight))

/**
 * Ensure the label's height is an exact multiple of [lineHeight].
 * Why? https://dev.to/canyudev/android-and-figma-typography-and-how-to-achieve-100-fidelity-l40.
 *
 * TODO(W-1783): Delete this logic when `LineHeightStyle` API stabilizes:
 *       https://issuetracker.google.com/issues/181155707.
 *       Make sure this doesn't break Label previews.
 *
 * Implementation borrowed from go/market label implementation.
 */
private class LabelLineHeightModifier(
  val lineHeight: TextUnit,
) : LayoutModifier {
  /**
   * Measures the original node and then forces its height to be line count * [lineHeight].
   *
   * The original node is vertically centered.
   */
  override fun MeasureScope.measure(
    measurable: Measurable,
    constraints: Constraints,
  ): MeasureResult {
    val placeable = measurable.measure(constraints)
    val lineCount = lineCount(placeable)
    val fullHeight = (lineHeight.toPx() * lineCount).roundToInt()
    return layout(width = placeable.width, height = fullHeight) {
      // Alignment lines are recorded with the parents automatically.
      placeable.placeRelative(
        x = 0,
        y = Alignment.CenterVertically.align(placeable.height, fullHeight)
      )
    }
  }

  override fun IntrinsicMeasureScope.maxIntrinsicHeight(
    measurable: IntrinsicMeasurable,
    width: Int,
  ): Int {
    // Intrinsic height calculation does not provide [FirstBaseline] nor [LastBaseline], but we can
    // infer the right height by ceiling it to a multiple of [lineHeight].
    //
    // We know that:
    //
    //  - Android lays out the text as N text lines + N-1 spaces in between.
    //  - [lineHeight] = text height + space in between.
    //
    // Therefore total text height will be (N-1) lineHeight + text height.
    // By ceiling to the nearest multiple of [lineHeight] we make sure we obtain the right height.
    // An ill behaved case is if [lineHeight] is smaller than the text height, but that's a wrong
    // usage of [MarketLabel] and it's ok to return a sensible intrinsic height (larger than
    // original height).
    return ceilToLineHeight(measurable.maxIntrinsicHeight(width))
  }

  override fun IntrinsicMeasureScope.minIntrinsicHeight(
    measurable: IntrinsicMeasurable,
    width: Int,
  ): Int {
    // See maxIntrinsicHeight.
    return ceilToLineHeight(measurable.minIntrinsicHeight(width))
  }

  override fun IntrinsicMeasureScope.minIntrinsicWidth(
    measurable: IntrinsicMeasurable,
    height: Int,
  ): Int {
    // Intrinsic width is unchanged. [BasicText]'s intrinsic width does not depend on height.
    return measurable.minIntrinsicWidth(height)
  }

  override fun IntrinsicMeasureScope.maxIntrinsicWidth(
    measurable: IntrinsicMeasurable,
    height: Int,
  ): Int {
    // Intrinsic width is unchanged. [BasicText]'s intrinsic width does not depend on height.
    return measurable.maxIntrinsicWidth(height)
  }

  /**
   * Returns the number of lines a [Placeable] must have based on our [lineHeight].
   */
  private fun Density.lineCount(placeable: Placeable): Int {
    // We infer the number of lines from the difference in pixels from the first line's baseline to
    // the last line's baseline. It will be a multiple of [lineHeight].
    val firstToLast = (placeable[LastBaseline] - placeable[FirstBaseline]).toFloat()
    // roundToInt could technically be just toInt, but this will make sure a bad ceil result
    // like 2.99999998 becomes 3.
    return (firstToLast / lineHeight.toPx()).roundToInt() + 1
  }

  /**
   * Rounds a height [value] to a whole number of [lineHeight]s.
   *
   * For instance, if [lineHeight] is 10, and [value] is 25 it will return 30.
   */
  private fun Density.ceilToLineHeight(value: Int): Int {
    val lineHeightPx = lineHeight.toPx()
    return (ceil(value.toFloat() / lineHeightPx) * lineHeightPx).roundToInt()
  }
}
