package build.wallet.ui.components.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.StepperIndicator.StepStyle.*
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.loading.LoadingIndicatorPainter
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tokens.painter

@Composable
fun StepperIndicator(model: FormMainContentModel.StepperIndicator) {
  val stepData = model.steps.map { step ->
    val circleColor = when (step.style) {
      PENDING -> WalletTheme.colors.bitkeyPrimary
      COMPLETED -> WalletTheme.colors.bitkeyPrimary
      UPCOMING -> WalletTheme.colors.stepperIncomplete
    }

    val painter = when (step.icon) {
      IconImage.Loader -> LoadingIndicatorPainter(circleColor)
      is IconImage.LocalImage -> step.icon.icon.painter()
      is IconImage.UrlImage -> TODO("UrlImage is not currently supported")
      null -> null
    }

    StepData(
      painter = painter,
      circleColor = circleColor
    )
  }

  Column {
    Canvas(
      modifier = Modifier
        .fillMaxWidth()
        .height(40.dp)
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
      val lineY = center.y
      val canvasWidth = size.width

      val lineHeight = 8.dp.toPx()
      val circleRadius = 12.dp.toPx()
      val circleStrokeWidth = 2.dp.toPx()
      val iconSize = 16.dp.toPx()

      // Calculate spacing to distribute circles evenly within the inset area
      val insetWidth =
        canvasWidth - (2 * circleRadius) - circleStrokeWidth // Leave space for circle radius on each end
      val stepSpacing = insetWidth / (stepData.size - 1) // Distance between circle centers

      stepData.forEachIndexed { index, step ->
        // Calculate the center position of each circle
        val circleCenter = Offset(x = circleRadius + circleStrokeWidth / 2 + index * stepSpacing, y = lineY)

        // Draw the circle outline
        drawCircle(
          color = step.circleColor,
          radius = circleRadius,
          center = circleCenter,
          style = Stroke(width = circleStrokeWidth)
        )

        drawIntoCanvas { canvas ->
          val iconOffset = Offset(
            x = circleCenter.x - iconSize / 2,
            y = circleCenter.y - iconSize / 2
          )

          canvas.save()
          canvas.translate(iconOffset.x, iconOffset.y)
          step.painter?.apply {
            draw(
              size = Size(iconSize, iconSize),
              alpha = 1f,
              colorFilter = ColorFilter.tint(step.circleColor)
            )
          }
          canvas.restore()
        }

        // If this isn't the first circle, draw a rectangle from the current circle to the previous one
        if (index != 0) {
          // Calculate the position of the previous circle to determine the rectangle length
          val previousCircleCenter = circleRadius + circleStrokeWidth / 2 + (index - 1) * stepSpacing
          val rectangleLength = circleCenter.x - previousCircleCenter - (circleRadius * 2)

          // Draw the rectangle segment between the current and previous circle
          drawRect(
            color = step.circleColor,
            topLeft = Offset(previousCircleCenter + circleRadius, circleCenter.y - lineHeight / 2),
            size = Size(rectangleLength, lineHeight),
            blendMode = BlendMode.DstAtop
          )
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth()
        .padding(top = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      model.steps.forEach { step ->
        Label(
          text = step.label,
          type = LabelType.Label3,
          treatment = LabelTreatment.Unspecified,
          color = when (step.style) {
            PENDING -> WalletTheme.colors.bitkeyPrimary
            COMPLETED -> WalletTheme.colors.bitkeyPrimary
            UPCOMING -> WalletTheme.colors.stepperIncompleteLabel
          }
        )
      }
    }
  }
}

/**
 * Internal data model representing what is needed to draw a step.
 */
private data class StepData(
  val painter: Painter?,
  val circleColor: Color,
)
