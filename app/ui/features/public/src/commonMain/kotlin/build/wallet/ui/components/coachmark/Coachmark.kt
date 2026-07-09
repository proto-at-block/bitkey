package build.wallet.ui.components.coachmark

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.icon.IconButton
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.coachmark.CoachmarkLabelTreatment
import build.wallet.ui.model.coachmark.CoachmarkModel
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.list.CoachmarkLabelModel
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tokens.darkStyleDictionaryColors
import build.wallet.statemachine.core.Icon
import build.wallet.ui.tokens.painter

/**
 * Popover-style coachmark
 * @param model The model to use to display the coachmark.
 * @param offset The offset to apply to the coachmark.
 */
@Composable
fun Coachmark(
  modifier: Modifier = Modifier,
  model: CoachmarkModel,
  offset: Offset,
) {
  val density = LocalDensity.current
  val coachmarkShape = RoundedCornerShape(8.dp)
  // Coachmarks always use the dark palette regardless of the active theme.
  val backgroundColor = darkStyleDictionaryColors.subtleBackground
  val titleColor = darkStyleDictionaryColors.foreground
  val descriptionColor = darkStyleDictionaryColors.foreground60
  val closeIconColor = darkStyleDictionaryColors.foreground30
  val arrowAlignment = when (model.arrowPosition.horizontal) {
    CoachmarkModel.ArrowPosition.Horizontal.Leading -> Alignment.Start
    CoachmarkModel.ArrowPosition.Horizontal.Centered -> Alignment.CenterHorizontally
    CoachmarkModel.ArrowPosition.Horizontal.Trailing -> Alignment.End
  }

  Column(
    modifier = modifier
      .padding(horizontal = 16.dp)
      .fillMaxWidth()
      .offset(with(density) { offset.x.toDp() }, with(density) { offset.y.toDp() })
  ) {
    // Top arrow
    if (model.arrowPosition.vertical == CoachmarkModel.ArrowPosition.Vertical.Top) {
      CoachmarkArrow(
        modifier = Modifier.align(arrowAlignment).offset(y = 1.dp),
        color = backgroundColor
      )
    }

    // Coachmark body
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .shadow(
          elevation = 8.dp,
          shape = coachmarkShape,
          spotColor = Color(0x0A000000),
          ambientColor = Color(0x0A000000)
        )
        .background(color = backgroundColor, shape = coachmarkShape)
        .padding(16.dp)
    ) {
      model.image?.let {
        Image(
          painter = it.painter(),
          contentDescription = null
        )
        Spacer(modifier = Modifier.height(8.dp))
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        CoachmarkLabel(
          model = CoachmarkLabelModel.New.copy(treatment = CoachmarkLabelTreatment.Dark)
        )
        IconButton(
          iconModel = IconModel(
            iconImage = IconImage.LocalImage(Icon.XCircleFill),
            iconSize = IconSize.Small
          ),
          color = closeIconColor,
          onClick = model.dismiss
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      Label(
        text = model.title,
        type = LabelType.Title3,
        treatment = LabelTreatment.Unspecified,
        color = titleColor
      )
      Label(
        text = model.description,
        type = LabelType.Body3Regular,
        treatment = LabelTreatment.Unspecified,
        color = descriptionColor
      )
      model.button?.let {
        Spacer(modifier = Modifier.height(8.dp))
        CompositionLocalProvider(LocalTheme provides Theme.DARK) {
          WalletTheme {
            Button(
              model = it.copy(treatment = ButtonModel.Treatment.Secondary)
            )
          }
        }
      }
    }

    // Bottom arrow
    if (model.arrowPosition.vertical == CoachmarkModel.ArrowPosition.Vertical.Bottom) {
      CoachmarkArrow(
        modifier = Modifier.align(arrowAlignment).offset(y = (-1).dp),
        color = backgroundColor,
        rotated = true
      )
    }
  }
}

@Composable
private fun CoachmarkArrow(
  modifier: Modifier = Modifier,
  color: Color,
  rotated: Boolean = false,
) {
  // The arrow is drawn as a triangle pointing up with a rounded apex, inside a box sized
  // arrowWidthDp x arrowHeightDp. The apex is shaped by a cubic bezier (rather than a single
  // quadratic) so the tip is a slightly flatter, softer join than a perfectly circular
  // round-over.
  val arrowWidthDp = 25.dp
  val arrowHeightDp = 11.dp
  val density = LocalDensity.current
  val path = remember(density) {
    with(density) {
      val widthPx = arrowWidthDp.toPx()
      val heightPx = arrowHeightDp.toPx()

      // Helpers to convert design-space coordinates (in the 25x11 viewport) to pixels.
      fun x(designX: Float) = widthPx * (designX / 25f)
      fun y(designY: Float) = heightPx * (designY / 11f)

      // Small horizontal inset from the box edges, preserved from the original design viewport
      // so the filled shape sits flush with the tooltip body without anti-aliased edges
      // bleeding past the canvas bounds.
      val baselineInset = 0.5f
      val baselineY = 11f

      // Where the straight edges of the triangle stop and the rounded apex begins.
      val apexStartX = 9.672f
      val apexEndX = 15.328f
      val apexStartY = 1.828f

      // Cubic bezier control points that shape the rounded tip.
      val apexControlLeftX = 11.234f
      val apexControlRightX = 13.766f
      val apexControlY = 0.266f

      Path().apply {
        // Bottom-left corner of the triangle base.
        moveTo(x(baselineInset), y(baselineY))
        // Bottom-right corner of the triangle base.
        lineTo(x(25f - baselineInset), y(baselineY))
        // Up the right edge to where the rounded apex starts.
        lineTo(x(apexEndX), y(apexStartY))
        // Rounded apex: curve across the top from right to left.
        cubicTo(
          x(apexControlRightX), y(apexControlY),
          x(apexControlLeftX), y(apexControlY),
          x(apexStartX), y(apexStartY)
        )
        // Back down the left edge to the starting point.
        close()
      }
    }
  }
  Canvas(
    modifier = modifier
      .padding(horizontal = 16.dp)
      .size(width = arrowWidthDp, height = arrowHeightDp)
      .then(if (rotated) Modifier.rotate(180f) else Modifier)
  ) {
    drawPath(path = path, color = color)
  }
}

