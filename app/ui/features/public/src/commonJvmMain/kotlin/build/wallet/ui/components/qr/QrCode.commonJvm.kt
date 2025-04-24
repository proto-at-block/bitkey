package build.wallet.ui.components.qr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import build.wallet.ui.tooling.LocalIsPreviewTheme
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.ByteMatrix
import com.google.zxing.qrcode.encoder.Encoder
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor

@Composable
actual fun QrCode(
  modifier: Modifier,
  data: String?,
  cellShape: CellShape,
) {
  val isPreviewTheme = LocalIsPreviewTheme.current
  val previewByteMatrix = remember(isPreviewTheme, data) {
    if (isPreviewTheme) data?.toByteMatrix() else null
  }
  val matrix by produceState(previewByteMatrix, data) {
    value = data?.let {
      withContext(Default) {
        data.toByteMatrix()
      }
    }
  }

  // Use BoxWithConstraints so that we can derive size constraints from
  // parent layout node. We need those constraints to set appropriate
  // size of our QR code in pixels (while accounting for density).
  BoxWithConstraints {
    // Use the most narrow constraint available.
    val qrCodeSizeDp = remember(constraints) {
      min(maxWidth, maxHeight)
    }

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
          val cellSize = floor(size.width / m.width)
          val qrCodeRealSize = cellSize * m.width
          val baseOffsetPx = ceil((size.width - qrCodeRealSize) / 2)
          val baseOffset = Offset(baseOffsetPx, baseOffsetPx)
          // draw the individual cells of the qr code, excluding the finder cells
          drawCells(
            matrix = m,
            baseOffset = baseOffset,
            cellShape = cellShape,
            cellSize = cellSize,
            color = cellColor
          )
          // top-left finder cell
          drawFinderCell(
            cellShape = cellShape,
            cellSize = cellSize,
            topLeft = baseOffset,
            color = cellColor,
            backgroundColor = backgroundColor
          )
          // top-right finder cell
          drawFinderCell(
            cellShape = cellShape,
            cellSize = cellSize,
            topLeft = baseOffset + Offset((m.width - FINDER_CELL_SIZE) * cellSize, 0f),
            color = cellColor,
            backgroundColor = backgroundColor
          )
          // bottom-left finder cell
          drawFinderCell(
            cellShape = cellShape,
            cellSize = cellSize,
            topLeft = baseOffset + Offset(0f, (m.height - FINDER_CELL_SIZE) * cellSize),
            color = cellColor,
            backgroundColor = backgroundColor
          )
        }
      }
    }
  }
}

private fun String.toByteMatrix(): ByteMatrix {
  return Encoder.encode(
    this,
    ErrorCorrectionLevel.H,
    mapOf(
      EncodeHintType.CHARACTER_SET to "UTF-8",
      EncodeHintType.MARGIN to 16,
      EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H
    )
  ).matrix
}

private fun DrawScope.drawCells(
  matrix: ByteMatrix,
  cellShape: CellShape,
  cellSize: Float,
  color: Color,
  baseOffset: Offset,
) {
  for (cellRow in 0 until matrix.width) {
    for (cellColumn in 0 until matrix.height) {
      if (matrix[cellRow, cellColumn].isColoredCell() &&
        !isFinderCell(cellRow, cellColumn, matrix.width)
      ) {
        drawCell(
          color = color,
          topLeftOffset =
            baseOffset + Offset(
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
