package build.wallet.ui.components.qr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.min
import build.wallet.ui.components.loading.LoadingIndicator
import build.wallet.ui.components.qr.CellShape.*
import build.wallet.ui.theme.WalletTheme
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.ByteMatrix
import com.google.zxing.qrcode.encoder.Encoder

@Composable
actual fun QrCode(
  modifier: Modifier,
  data: String?,
  cellShape: CellShape,
) {
  val matrix by remember(data) {
    mutableStateOf(
      when (data) {
        null -> null
        else ->
          Encoder.encode(
            data,
            ErrorCorrectionLevel.H,
            mapOf(
              EncodeHintType.CHARACTER_SET to "UTF-8",
              EncodeHintType.MARGIN to 16,
              EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H
            )
          ).matrix
      }
    )
  }

  // Use BoxWithConstraints so that we can derive size constraints from
  // parent layout node. We need those constraints to set appropriate
  // size of our QR code in pixels (while accounting for density).
  BoxWithConstraints {
    // Use the most narrow constraint available.
    val qrCodeSizeDp = min(maxWidth, maxHeight)

    when (val m = matrix) {
      null -> {
        Box(modifier = modifier.size(qrCodeSizeDp)) {
          // Show loading spinner while we are waiting for data
          LoadingIndicator(
            modifier =
              Modifier.size(qrCodeSizeDp / 4)
                .align(Alignment.Center)
          )
        }
      }

      else -> {
        val cellColor = WalletTheme.colors.foreground
        val backgroundColor = WalletTheme.colors.background
        Canvas(
          modifier = Modifier.size(qrCodeSizeDp),
          contentDescription = ""
        ) {
          val cellSize = size.width / m.width
          // draw the individual cells of the qr code, excluding the finder cells
          drawCells(
            matrix = m,
            cellShape = cellShape,
            color = cellColor
          )
          // top-left finder cell
          drawFinderCell(
            cellShape = cellShape,
            cellSize = cellSize,
            topLeft = Offset(0f, 0f),
            color = cellColor,
            backgroundColor = backgroundColor
          )
          // top-right finder cell
          drawFinderCell(
            cellShape = cellShape,
            cellSize = cellSize,
            topLeft = Offset(size.width - FINDER_CELL_SIZE * cellSize, 0f),
            color = cellColor,
            backgroundColor = backgroundColor
          )
          // bottom-left finder cell
          drawFinderCell(
            cellShape = cellShape,
            cellSize = cellSize,
            topLeft = Offset(0f, size.width - FINDER_CELL_SIZE * cellSize),
            color = cellColor,
            backgroundColor = backgroundColor
          )
        }
      }
    }
  }
}

private fun DrawScope.drawCells(
  matrix: ByteMatrix,
  cellShape: CellShape,
  color: Color,
) {
  val cellSize = size.width / matrix.width

  for (cellRow in 0 until matrix.width) {
    for (cellColumn in 0 until matrix.height) {
      if (matrix[cellRow, cellColumn].isColoredCell() &&
        !isFinderCell(cellRow, cellColumn, matrix.width)
      ) {
        drawCell(
          color = color,
          topLeftOffset =
            Offset(
              x = cellRow * cellSize,
              y = cellColumn * cellSize
            ),
          cellSize = cellSize,
          cellShape = cellShape
        )
      }
    }
  }
}

private fun Byte.isColoredCell() = this == 1.toByte()

private const val FINDER_CELL_SIZE = 7

private fun isFinderCell(
  cellRow: Int,
  cellColumn: Int,
  gridSize: Int,
) = (cellRow < FINDER_CELL_SIZE && cellColumn < FINDER_CELL_SIZE) ||
  (cellRow < FINDER_CELL_SIZE && cellColumn > gridSize - 1 - FINDER_CELL_SIZE) ||
  (cellRow > gridSize - 1 - FINDER_CELL_SIZE && cellColumn < FINDER_CELL_SIZE)

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

private fun DrawScope.drawFinderCell(
  cellShape: CellShape,
  cellSize: Float,
  topLeft: Offset,
  color: Color,
  backgroundColor: Color,
) {
  when (cellShape) {
    Square -> drawSquareFinderCell(cellSize, topLeft, color, backgroundColor)
    Circle -> drawCircleFinderCell(cellSize, topLeft, color, backgroundColor)
    RoundedSquare -> drawRoundedSquareFinderCell(cellSize, topLeft, color, backgroundColor)
  }
}

private fun DrawScope.drawCircleFinderCell(
  cellSize: Float,
  topLeftOffset: Offset,
  color: Color,
  backgroundColor: Color,
) {
  drawCircle(
    color = color,
    center =
      Offset(
        topLeftOffset.x + cellSize * FINDER_CELL_SIZE / 2,
        topLeftOffset.y + cellSize * FINDER_CELL_SIZE / 2
      ),
    radius = cellSize * FINDER_CELL_SIZE / 2
  )
  drawCircle(
    color = backgroundColor,
    center =
      Offset(
        topLeftOffset.x + cellSize * FINDER_CELL_SIZE / 2,
        topLeftOffset.y + cellSize * FINDER_CELL_SIZE / 2
      ),
    radius = cellSize * (FINDER_CELL_SIZE - 2) / 2
  )

  drawCircle(
    color = color,
    center =
      Offset(
        topLeftOffset.x + cellSize * FINDER_CELL_SIZE / 2,
        topLeftOffset.y + cellSize * FINDER_CELL_SIZE / 2
      ),
    radius = cellSize * (FINDER_CELL_SIZE - 4) / 2
  )
}

private fun DrawScope.drawRoundedSquareFinderCell(
  cellSize: Float,
  topLeft: Offset,
  color: Color,
  backgroundColor: Color,
) {
  drawRoundRect(
    color = color,
    topLeft = topLeft,
    size = Size(cellSize * FINDER_CELL_SIZE, cellSize * FINDER_CELL_SIZE),
    cornerRadius = CornerRadius(cellSize * 2)
  )
  drawRoundRect(
    color = backgroundColor,
    topLeft = topLeft + Offset(cellSize, cellSize),
    size =
      Size(
        cellSize * (FINDER_CELL_SIZE - 2),
        cellSize * (FINDER_CELL_SIZE - 2)
      ),
    cornerRadius = CornerRadius(cellSize * 2)
  )

  drawRoundRect(
    color = color,
    topLeft = topLeft + Offset(cellSize * 2, cellSize * 2),
    size =
      Size(
        cellSize * (FINDER_CELL_SIZE - 4),
        cellSize * (FINDER_CELL_SIZE - 4)
      ),
    cornerRadius = CornerRadius(cellSize * 2)
  )
}

private fun DrawScope.drawSquareFinderCell(
  cellSize: Float,
  topLeft: Offset,
  color: Color,
  backgroundColor: Color,
) {
  drawRect(
    color = color,
    topLeft = topLeft,
    size = Size(cellSize * FINDER_CELL_SIZE, cellSize * FINDER_CELL_SIZE)
  )
  drawRect(
    color = backgroundColor,
    topLeft = topLeft + Offset(cellSize, cellSize),
    size =
      Size(
        cellSize * (FINDER_CELL_SIZE - 2),
        cellSize * (FINDER_CELL_SIZE - 2)
      )
  )

  drawRect(
    color = color,
    topLeft = topLeft + Offset(cellSize * 2, cellSize * 2),
    size =
      Size(
        cellSize * (FINDER_CELL_SIZE - 4),
        cellSize * (FINDER_CELL_SIZE - 4)
      )
  )
}
