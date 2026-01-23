package build.wallet.ui.components.qr

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.qr.QRMatrix
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.qr.CellShape.*
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.theme.WalletTheme
import kotlin.math.ceil
import kotlin.math.floor

// QR Code Constants
private const val CELL_SPACING_RATIO = 0.2f // 20% spacing between dots
private const val FINDER_PATTERN_SIZE = 7 // Standard QR finder pattern is 7x7 cells
private const val FINDER_CORNER_RADIUS_MULTIPLIER = 2.0f // Corner radius relative to cell size
private const val FINDER_INNER_CORNER_RADIUS_MULTIPLIER = 0.4f // Inner corner radius multiplier
private const val FINDER_CENTER_CORNER_RADIUS_MULTIPLIER = 0.2f // Center corner radius multiplier
private const val CENTER_ICON_CORNER_RADIUS_DP = 20 // Corner radius for center icon background
private const val CENTER_ICON_PADDING_DP = 2 // Padding inside center icon background
private const val MAX_CENTER_OBSCURED_RATIO = 0.20f // Maximum 20% of QR dots can be obscured by center icon
private const val CENTER_ICON_PADDING_RATIO = 0.20f // Padding around center icon is 20% of the obscured area

/**
 * @param [matrix] - content that will be encoded in the QR code.
 * @param [cellShape] - shape of the QR code cells
 * @param [centerIcon] - icon to display in center, or null if no icon is desired.
 */
@Composable
fun QrCode(
  matrix: QRMatrix,
  cellShape: CellShape = Circle,
  centerIcon: Icon? = null,
) {
  // Use BoxWithConstraints so that we can derive size constraints from
  // parent layout node. We need those constraints to set appropriate
  // size of our QR code in pixels (while accounting for density).
  BoxWithConstraints {
    // Use the most narrow constraint available.
    val qrCodeSizeDp = remember(constraints) {
      min(maxWidth, maxHeight)
    }

    // Ensure QR code data is valid
    QrCodeWithData(
      matrix = matrix,
      cellShape = cellShape,
      centerIcon = centerIcon,
      qrCodeSizeDp = qrCodeSizeDp
    )
  }
}

@Composable
fun QrCodeWithData(
  matrix: QRMatrix,
  cellShape: CellShape = Circle,
  centerIcon: Icon? = null,
  qrCodeSizeDp: Dp,
) {
  val backgroundColor = WalletTheme.colors.background
  val cellColor = WalletTheme.colors.foreground

  // Track old and new matrices for animation
  var oldMatrix by remember { mutableStateOf<QRMatrix?>(null) }
  var currentMatrix by remember { mutableStateOf(matrix) }
  
  // Animation progress (0.0 = start, 1.0 = complete)
  val animationProgress = remember { Animatable(1f) }

  // When matrix changes, trigger animation
  LaunchedEffect(matrix) {
    if (currentMatrix != matrix) {
      oldMatrix = currentMatrix
      currentMatrix = matrix
      animationProgress.snapTo(0f)
      animationProgress.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 1000)
      )
      // Clear old matrix after animation completes
      oldMatrix = null
    }
  }

  // Use the container column width to lock dimensions
  val containerColumnWidth = remember(currentMatrix.columnWidth) {
    currentMatrix.columnWidth
  }

  // Calculate center icon area size once for the entire component
  val centerIconAreaSize = if (centerIcon != null) {
    calculateCenterIconAreaSize(containerColumnWidth)
  } else {
    0
  }

  Box(modifier = Modifier.size(qrCodeSizeDp)) {
    Canvas(
      modifier = Modifier.size(qrCodeSizeDp),
      contentDescription = ""
    ) {
      val cellSize = floor(size.width / containerColumnWidth)
      val qrCodeRealSize = cellSize * containerColumnWidth
      val baseOffsetPx = ceil((size.width - qrCodeRealSize) / 2)
      val baseOffset = Offset(baseOffsetPx, baseOffsetPx)

      // Draw old matrix with inverse animation (fades out as wave passes)
      oldMatrix?.let { old ->
        drawDataCells(
          matrix = old,
          baseOffset = baseOffset,
          cellShape = cellShape,
          cellSize = cellSize,
          color = cellColor,
          hasCenterIcon = centerIcon != null,
          centerIconAreaSize = centerIconAreaSize,
          animationProgress = animationProgress.value,
          isInverseAnimation = true // Old matrix fades out
        )

        drawFinderPatterns(
          matrix = old,
          baseOffset = baseOffset,
          cellSize = cellSize,
          color = cellColor,
          backgroundColor = backgroundColor
        )
      }

      // Draw new matrix with wipe-up animation (fades in as wave passes)
      drawDataCells(
        matrix = currentMatrix,
        baseOffset = baseOffset,
        cellShape = cellShape,
        cellSize = cellSize,
        color = cellColor,
        hasCenterIcon = centerIcon != null,
        centerIconAreaSize = centerIconAreaSize,
        animationProgress = oldMatrix?.let { animationProgress.value },
        isInverseAnimation = false // New matrix fades in
      )

      drawFinderPatterns(
        matrix = currentMatrix,
        baseOffset = baseOffset,
        cellSize = cellSize,
        color = cellColor,
        backgroundColor = backgroundColor
      )
    }

    centerIcon?.let {
      CenterIcon(
        icon = it,
        cellColor = cellColor,
        gridSize = containerColumnWidth,
        centerIconAreaSize = centerIconAreaSize,
        qrCodeSizeDp = qrCodeSizeDp
      )
    }
  }
}

private fun DrawScope.drawDataCells(
  matrix: QRMatrix,
  cellShape: CellShape,
  cellSize: Float,
  color: Color,
  baseOffset: Offset,
  hasCenterIcon: Boolean,
  centerIconAreaSize: Int,
  animationProgress: Float? = null,
  isInverseAnimation: Boolean = false,
) {
  val spacing = cellSize * CELL_SPACING_RATIO
  val dotSize = cellSize - spacing

  for (cellRow in 0 until matrix.rowCount) {
    for (cellColumn in 0 until matrix.columnWidth) {
      drawDataCell(
        matrix = matrix,
        cellRow = cellRow,
        cellColumn = cellColumn,
        cellShape = cellShape,
        cellSize = cellSize,
        dotSize = dotSize,
        color = color,
        baseOffset = baseOffset,
        hasCenterIcon = hasCenterIcon,
        centerIconAreaSize = centerIconAreaSize,
        animationProgress = animationProgress,
        isInverseAnimation = isInverseAnimation
      )
    }
  }
}

/**
 * Draws a single data cell if it should be visible.
 */
private fun DrawScope.drawDataCell(
  matrix: QRMatrix,
  cellRow: Int,
  cellColumn: Int,
  cellShape: CellShape,
  cellSize: Float,
  dotSize: Float,
  color: Color,
  baseOffset: Offset,
  hasCenterIcon: Boolean,
  centerIconAreaSize: Int,
  animationProgress: Float?,
  isInverseAnimation: Boolean,
) {
  val isColored = matrix[cellRow, cellColumn].isColoredCell()
  val isFinderCell = isFinderCell(
    cellRow = cellRow,
    cellColumn = cellColumn,
    gridSize = matrix.columnWidth
  )
  val isCenterArea = isCenterIconArea(
    cellRow = cellRow,
    cellColumn = cellColumn,
    gridSize = matrix.columnWidth,
    centerIconAreaSize = centerIconAreaSize,
    hasCenterIcon = hasCenterIcon
  )

  val isInVisibleZone = !isFinderCell && !isCenterArea

  if (!isColored || !isInVisibleZone) return

  // Calculate animation scale and opacity if animating
  val (scale, opacity) = calculateCellAnimation(
    cellRow = cellRow,
    rowCount = matrix.rowCount,
    animationProgress = animationProgress,
    isInverseAnimation = isInverseAnimation
  )

  // Only draw if visible
  if (scale <= 0f || opacity <= 0f) return

  val cellCenter = baseOffset + Offset(
    x = cellColumn * cellSize + cellSize / 2,
    y = cellRow * cellSize + cellSize / 2
  )
  val scaledDotSize = dotSize * scale

  drawCell(
    color = color.copy(alpha = color.alpha * opacity),
    topLeftOffset = Offset(
      x = cellCenter.x - scaledDotSize / 2,
      y = cellCenter.y - scaledDotSize / 2
    ),
    cellSize = scaledDotSize,
    cellShape = cellShape
  )
}

/**
 * Calculates the animation scale and opacity for a cell based on its position
 * and the current animation progress.
 */
private fun calculateCellAnimation(
  cellRow: Int,
  rowCount: Int,
  animationProgress: Float?,
  isInverseAnimation: Boolean,
): Pair<Float, Float> {
  if (animationProgress == null) {
    // No animation - draw at full scale and opacity
    return 1f to 1f
  }

  // Single stage wipe-up animation
  val rowProgress = cellRow.toFloat() / rowCount.toFloat()
  val waveSpread = 0.3f // How gradual the wave effect is
  
  // Wave position moves from -waveSpread to 1+waveSpread
  val wavePosition = (1f + waveSpread) * animationProgress
  
  // Distance from the wave (negative = before wave, positive = after wave)
  val distanceFromWave = (wavePosition - rowProgress) / waveSpread
  
  // Scale goes from 0.0 to 1.0 as wave passes
  val normalizedScale = distanceFromWave.coerceIn(0f, 1f)
  
  // For inverse animation (old matrix), flip the scale/opacity
  // Old dots: visible (1.0) before wave, invisible (0.0) after wave
  // New dots: invisible (0.0) before wave, visible (1.0) after wave
  return if (isInverseAnimation) {
    // Old dots: fade out with dramatic scale down
    // Scale down to 0.2 for more dramatic shrink effect
    val fadeProgress = 1f - normalizedScale
    val scale = 0.2f + (fadeProgress * 0.8f) // 1.0 → 0.2 → 0.0
    scale to fadeProgress
  } else {
    // New dots: fade in with pop-up scale effect
    // Use ease-out-back for slight overshoot effect
    val easeOutBack = if (normalizedScale < 0.5f) {
      // First half: scale up quickly with overshoot
      val t = normalizedScale * 2f
      val overshoot = 1.2f
      t * t * ((overshoot + 1f) * t - overshoot)
    } else {
      // Second half: settle back to 1.0
      val t = (normalizedScale - 0.5f) * 2f
      1.0f + (0.1f * (1f - t)) // 1.1 → 1.0
    }
    val scale = easeOutBack.coerceIn(0f, 1.2f)
    scale to normalizedScale
  }
}

/**
 * Calculates the size of the center icon area in QR code cells.
 * Returns the side length of the square area that can be obscured by the center icon.
 */
private fun calculateCenterIconAreaSize(gridSize: Int): Int {
  val totalDots = gridSize * gridSize
  val maxObscuredDots = (totalDots * MAX_CENTER_OBSCURED_RATIO).toInt()

  // Calculate the largest square area (in dots) that doesn't exceed the max obscured dots
  val maxSquareSide = kotlin.math.sqrt(maxObscuredDots.toDouble()).toInt()

  // Ensure the square side is odd for symmetric centering
  return if (maxSquareSide % 2 == 0) maxSquareSide - 1 else maxSquareSide
}

/**
 * Determines whether the cell at the given row and column would be obscured by the center
 * logo area.
 */
private fun isCenterIconArea(
  cellRow: Int,
  cellColumn: Int,
  gridSize: Int,
  centerIconAreaSize: Int,
  hasCenterIcon: Boolean,
): Boolean {
  if (!hasCenterIcon) return false

  val squareSide = centerIconAreaSize
  val halfSquareSide = squareSide / 2

  // Calculate center position
  val centerRow = gridSize / 2
  val centerColumn = gridSize / 2

  // Calculate distances from center
  val rowDistance = kotlin.math.abs(cellRow - centerRow)
  val columnDistance = kotlin.math.abs(cellColumn - centerColumn)

  return rowDistance <= halfSquareSide && columnDistance <= halfSquareSide
}

private fun DrawScope.drawFinderPatterns(
  matrix: QRMatrix,
  baseOffset: Offset,
  cellSize: Float,
  color: Color,
  backgroundColor: Color,
) {
  val finderSize = FINDER_PATTERN_SIZE * cellSize
  val cornerRadius = cellSize * FINDER_CORNER_RADIUS_MULTIPLIER

  // Calculate positions
  val topLeftPos = baseOffset
  val topRightPos = baseOffset + Offset((matrix.columnWidth - FINDER_PATTERN_SIZE) * cellSize, 0f)
  val bottomLeftPos = baseOffset + Offset(0f, (matrix.rowCount - FINDER_PATTERN_SIZE) * cellSize)

  // Top-left finder pattern
  drawFinderPattern(
    topLeft = topLeftPos,
    size = finderSize,
    cornerRadius = cornerRadius,
    color = color,
    backgroundColor = backgroundColor
  )

  // Top-right finder pattern
  drawFinderPattern(
    topLeft = topRightPos,
    size = finderSize,
    cornerRadius = cornerRadius,
    color = color,
    backgroundColor = backgroundColor
  )

  // Bottom-left finder pattern
  drawFinderPattern(
    topLeft = bottomLeftPos,
    size = finderSize,
    cornerRadius = cornerRadius,
    color = color,
    backgroundColor = backgroundColor
  )
}

private fun DrawScope.drawFinderPattern(
  topLeft: Offset,
  size: Float,
  cornerRadius: Float,
  color: Color,
  backgroundColor: Color,
) {
  // Outer rounded rectangle (7x7 cells)
  drawRoundRect(
    color = color,
    topLeft = topLeft,
    size = Size(size, size),
    cornerRadius = CornerRadius(cornerRadius)
  )

  // Inner white rounded rectangle (5x5 cells) - creates the "ring" effect
  val innerPadding = size / FINDER_PATTERN_SIZE // 1 cell padding (1/7 of total size)
  drawRoundRect(
    color = backgroundColor,
    topLeft = topLeft + Offset(innerPadding, innerPadding),
    size = Size(size - 2 * innerPadding, size - 2 * innerPadding),
    cornerRadius = CornerRadius(cornerRadius * FINDER_INNER_CORNER_RADIUS_MULTIPLIER)
  )

  // Center solid rounded rectangle (3x3 cells) - the inner square
  val centerPadding = size * 2f / FINDER_PATTERN_SIZE // 2 cell padding (2/7 of total size)
  val centerSize = size * 3f / FINDER_PATTERN_SIZE // 3/7 of total size (direct calculation)
  drawRoundRect(
    color = color,
    topLeft = topLeft + Offset(centerPadding, centerPadding),
    size = Size(centerSize, centerSize),
    cornerRadius = CornerRadius(cornerRadius * FINDER_CENTER_CORNER_RADIUS_MULTIPLIER)
  )
}

private fun Boolean.isColoredCell() = this

private fun isFinderCell(
  cellRow: Int,
  cellColumn: Int,
  gridSize: Int,
) = (cellRow < FINDER_PATTERN_SIZE && cellColumn < FINDER_PATTERN_SIZE) ||
  (cellRow < FINDER_PATTERN_SIZE && cellColumn > gridSize - 1 - FINDER_PATTERN_SIZE) ||
  (cellRow > gridSize - 1 - FINDER_PATTERN_SIZE && cellColumn < FINDER_PATTERN_SIZE)

private fun DrawScope.drawCell(
  color: Color,
  topLeftOffset: Offset,
  cellSize: Float,
  cellShape: CellShape,
) {
  when (cellShape) {
    Square ->
      drawRect(
        color = color,
        topLeft = topLeftOffset,
        size = Size(cellSize, cellSize)
      )

    Circle, RoundedSquare ->
      drawCircle(
        color = color,
        center = Offset(topLeftOffset.x + cellSize / 2, topLeftOffset.y + cellSize / 2),
        radius = cellSize / 2
      )
  }
}

@Composable
private fun BoxScope.CenterIcon(
  icon: Icon,
  cellColor: Color,
  gridSize: Int,
  centerIconAreaSize: Int,
  qrCodeSizeDp: Dp,
) {
  // Calculate the total obscured area (including padding)
  val totalObscuredSquareSide = centerIconAreaSize
  // Calculate the actual icon size (excluding padding)
  // The padding is 20% of the total obscured area, so the icon is 80% of the total
  val iconSquareSide = (totalObscuredSquareSide * (1f - CENTER_ICON_PADDING_RATIO)).toInt()
  val iconSizeRatio = iconSquareSide.toFloat() / gridSize.toFloat()

  Box(
    modifier = Modifier
      .size(qrCodeSizeDp * iconSizeRatio)
      .align(Alignment.Center),
    contentAlignment = Alignment.Center
  ) {
    CenterIconContent(
      icon = icon,
      foregroundColor = cellColor
    )
  }
}

@Composable
private fun CenterIconContent(
  modifier: Modifier = Modifier,
  icon: Icon,
  foregroundColor: Color,
) {
  val cornerRadius = CENTER_ICON_CORNER_RADIUS_DP.dp

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(foregroundColor, RoundedCornerShape(cornerRadius))
      .padding(CENTER_ICON_PADDING_DP.dp),
    contentAlignment = Alignment.Center
  ) {
    IconImage(
      model = IconModel(
        iconImage = IconImage.LocalImage(icon),
        iconSize = IconSize.XLarge,
        iconTint = IconTint.Background
      )
    )
  }
}
