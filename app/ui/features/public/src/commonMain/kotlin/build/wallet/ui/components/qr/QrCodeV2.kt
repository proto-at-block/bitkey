package build.wallet.ui.components.qr

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
import androidx.compose.runtime.remember
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
fun QrCodeV2(
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
    QrCodeV2WithData(
      matrix = matrix,
      cellShape = cellShape,
      centerIcon = centerIcon,
      qrCodeSizeDp = qrCodeSizeDp
    )
  }
}

@Composable
fun QrCodeV2WithData(
  matrix: QRMatrix,
  cellShape: CellShape = Circle,
  centerIcon: Icon? = null,
  qrCodeSizeDp: Dp,
) {
  val backgroundColor = WalletTheme.colors.background
  val cellColor = WalletTheme.colors.foreground

  // Calculate center icon area size once for the entire component
  val centerIconAreaSize = if (centerIcon != null) {
    calculateCenterIconAreaSize(matrix.columnWidth)
  } else 0

  Box(modifier = Modifier.size(qrCodeSizeDp)) {
    Canvas(
      modifier = Modifier.size(qrCodeSizeDp),
      contentDescription = ""
    ) {
      val cellSize = floor(size.width / matrix.columnWidth)
      val qrCodeRealSize = cellSize * matrix.columnWidth
      val baseOffsetPx = ceil((size.width - qrCodeRealSize) / 2)
      val baseOffset = Offset(baseOffsetPx, baseOffsetPx)

      // Draw data cells (excluding finder patterns and center icon area) with spacing
      drawDataCells(
        matrix = matrix,
        baseOffset = baseOffset,
        cellShape = cellShape,
        cellSize = cellSize,
        color = cellColor,
        hasCenterIcon = centerIcon != null,
        centerIconAreaSize = centerIconAreaSize
      )

      drawFinderPatterns(
        matrix = matrix,
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
        gridSize = matrix.columnWidth,
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
) {
  val spacing = cellSize * CELL_SPACING_RATIO
  val dotSize = cellSize - spacing

  for (cellRow in 0 until matrix.rowCount) {
    for (cellColumn in 0 until matrix.columnWidth) {
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

      if (isColored && isInVisibleZone) {
        drawCell(
          color = color,
          topLeftOffset =
            baseOffset + Offset(
              x = cellColumn * cellSize + spacing / 2,
              y = cellRow * cellSize + spacing / 2
            ),
          cellSize = dotSize,
          cellShape = cellShape
        )
      }
    }
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
