package build.wallet.ui.components.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.model.toolbar.largeTitle
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

/**
 * A reusable collapsible top-of-screen toolbar that fades in an inline title as a
 * scroll-driven [collapseProgress] approaches 1f.
 *
 * Pair this with [Modifier.height] equal to [CollapsibleToolbarReservedHeight] reserved at
 * the top of the scroll content so the title block can scroll underneath. Use
 * [CollapsibleToolbarTitleCollapseRange] to derive [collapseProgress] from a scroll state.
 *
 * Place inside a [Box] that fills the screen so the toolbar overlays the scrollable content.
 *
 * @param toolbarModel Toolbar accessories (and, by default, the inline collapse title sourced
 *   from its [build.wallet.ui.model.toolbar.ToolbarTitleModel.Large]).
 * @param collapseProgress 0f (expanded) to 1f (collapsed).
 * @param title Text shown inline when fully collapsed. Defaults to [toolbarModel]'s large
 *   title; override when the collapse title comes from outside the toolbar model (e.g. a
 *   form header headline).
 * @param horizontalPadding Horizontal padding matching the underlying scroll content.
 * @param background Background color applied behind the toolbar and gradient.
 * @param inlineTitleAlpha Computes inline title alpha from [collapseProgress]. Defaults to
 *   a late fade-in starting at 95%.
 * @param backgroundAlpha Multiplier for [background] opacity, allowing the toolbar to remain
 *   visually transparent on top of decorative backgrounds.
 */
@Composable
fun CollapsibleToolbar(
  toolbarModel: ToolbarModel?,
  collapseProgress: Float,
  title: String? = toolbarModel?.largeTitle?.title,
  horizontalPadding: Dp = CollapsibleToolbarHorizontalPadding,
  background: Color = WalletTheme.colors.background,
  inlineTitleAlpha: (Float) -> Float = DefaultInlineTitleAlpha,
  backgroundAlpha: Float = 1f,
) {
  val leadingAccessory = toolbarModel?.leadingAccessory
  val trailingAccessory = toolbarModel?.trailingAccessory
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(
        CollapsibleToolbarTopPadding +
          CollapsibleToolbarHeight +
          CollapsibleToolbarBottomPadding +
          CollapsibleToolbarBottomGradientHeight
      )
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(CollapsibleToolbarTopPadding + CollapsibleToolbarHeight + CollapsibleToolbarBottomPadding)
        .thenIf(backgroundAlpha > 0f) {
          Modifier.background(background.copy(alpha = background.alpha * backgroundAlpha))
        }
    ) {
      Box(
        modifier = Modifier
          .padding(
            top = CollapsibleToolbarTopPadding,
            start = horizontalPadding,
            end = horizontalPadding
          )
          .fillMaxWidth()
          .height(CollapsibleToolbarHeight)
      ) {
        Toolbar(
          model = ToolbarModel(
            leadingAccessory = leadingAccessory,
            trailingAccessory = trailingAccessory
          ),
          showDesignSystemChrome = false
        )

        title?.let {
          Label(
            modifier = Modifier
              .fillMaxWidth()
              .padding(
                start = if (leadingAccessory != null) CollapsibleToolbarInlineTitleStartPadding else 0.dp,
                end = if (trailingAccessory != null) CollapsibleToolbarInlineTitleEndPadding else 0.dp
              )
              .align(Alignment.CenterStart)
              .alpha(inlineTitleAlpha(collapseProgress)),
            text = AnnotatedString(it),
            style = WalletTheme.labelStyle(type = LabelType.Title2),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }

    if (backgroundAlpha > 0f) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(CollapsibleToolbarBottomGradientHeight)
          .align(Alignment.BottomCenter)
          .background(
            brush = Brush.verticalGradient(
              colors = listOf(
                background.copy(alpha = background.alpha * backgroundAlpha),
                background.copy(alpha = background.alpha * backgroundAlpha * 0.65f),
                Color.Transparent
              )
            )
          )
      )
    }
  }
}

/** Linear fade-in across the full collapse-progress range. */
val LinearInlineTitleAlpha: (Float) -> Float = { it.coerceIn(0f, 1f) }

/** Late fade-in starting at 95% — matches the Form screen large-title toolbar. */
val LateInlineTitleAlpha: (Float) -> Float = inlineTitleFadeIn(start = 0.95f, end = 1f)

/** Default inline title alpha curve. */
private val DefaultInlineTitleAlpha: (Float) -> Float = LateInlineTitleAlpha

/** Returns a fade-in alpha curve for the inline title using the given range. */
fun inlineTitleFadeIn(
  start: Float,
  end: Float,
): (Float) -> Float = { progress ->
  when {
    progress <= start -> 0f
    progress >= end -> 1f
    else -> (progress - start) / (end - start)
  }
}

/** Returns a fade-out alpha curve, e.g. for an expanded headline above the toolbar. */
fun fadeOutAlpha(
  start: Float,
  end: Float,
): (Float) -> Float = { progress ->
  when {
    progress <= start -> 1f
    progress >= end -> 0f
    else -> 1f - ((progress - start) / (end - start))
  }
}

val CollapsibleToolbarTopPadding: Dp = 8.dp
val CollapsibleToolbarHeight: Dp = 48.dp
val CollapsibleToolbarBottomPadding: Dp = 8.dp
val CollapsibleToolbarBottomGradientHeight: Dp = 20.dp

/**
 * Vertical space the [CollapsibleToolbar] occupies — reserve this at the top of the scroll
 * content so the title block can scroll underneath the toolbar. Excludes the bottom gradient,
 * which is intended to overflow into the scroll content.
 */
val CollapsibleToolbarReservedHeight: Dp =
  CollapsibleToolbarTopPadding +
    CollapsibleToolbarHeight +
    CollapsibleToolbarBottomPadding

val CollapsibleToolbarHorizontalPadding: Dp = 20.dp
val CollapsibleToolbarInlineTitleStartPadding: Dp = 56.dp
val CollapsibleToolbarInlineTitleEndPadding: Dp = 56.dp

/** Scroll distance over which the title collapses from fully expanded to fully collapsed. */
val CollapsibleToolbarTitleCollapseRange: Dp = 120.dp
