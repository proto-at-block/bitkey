package build.wallet.statemachine.qr

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.ktor.utils.io.core.toByteArray
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceGray
import platform.CoreGraphics.CGColorSpaceRef
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectGetHeight
import platform.CoreGraphics.CGRectGetWidth
import platform.CoreGraphics.CGRectMake
import platform.CoreImage.CIContext
import platform.CoreImage.CIFilter
import platform.CoreImage.QRCodeGenerator
import platform.CoreImage.createCGImage
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.setValue

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual suspend fun String.toQrMatrix(): Result<QRMatrix, Error> {
  return try {
    generateQrMatrix()
  } catch (e: IllegalArgumentException) {
    return Err(
      Error(
        QrCodeGenerationException(
          message = "Invalid input for QR code generation: ${e.message}",
          cause = e
        )
      )
    )
  }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun String.generateQrMatrix(): Result<QRMatrix, Error> {
  val sharedColorSpace = CGColorSpaceCreateDeviceGray()
  val sharedCIContext = CIContext()

  val dataAsNSData = createNSDataFromString()
  val qrCodeFilter = createQrFilter(dataAsNSData)

  val outputImage = qrCodeFilter.outputImage
    ?: return Err(
      Error(
        cause = QrCodeGenerationException(
          message = "Failed to generate QR code filter output image"
        )
      )
    )
  val qrImage = sharedCIContext.createCGImage(outputImage, outputImage.extent)
    ?: return Err(
      Error(
        cause = QrCodeGenerationException(
          message = "Failed to create CGImage from QR code filter output"
        )
      )
    )

  val width = CGRectGetWidth(outputImage.extent).toInt()
  val height = CGRectGetHeight(outputImage.extent).toInt()

  return try {
    processImageToMatrix(qrImage, sharedColorSpace, width, height)
  } finally {
    CGImageRelease(qrImage)
    CGColorSpaceRelease(sharedColorSpace)
  }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun String.createNSDataFromString(): NSData {
  val dataAsByteArray = toByteArray()
  return dataAsByteArray.usePinned { pin ->
    NSData.create(
      bytes = pin.addressOf(0),
      length = dataAsByteArray.size.toULong()
    )
  }
}

private fun createQrFilter(dataAsNSData: NSData): CIFilter {
  return CIFilter.QRCodeGenerator()
    .apply { setValue(value = dataAsNSData, forKey = "inputMessage") }
    .apply { setValue(value = "H", forKey = "inputCorrectionLevel") }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun processImageToMatrix(
  qrImage: CGImageRef,
  sharedColorSpace: CGColorSpaceRef?,
  width: Int,
  height: Int,
): Result<QRMatrix, Error> {
  val rawData = UByteArray(width * height)
  val fullData = BooleanArray(width * height)

  extractRawImageData(
    rawData = rawData,
    qrImage = qrImage,
    sharedColorSpace = sharedColorSpace,
    width = width,
    height = height
  )
  val bounds = convertToMatrixWithBounds(
    rawData = rawData,
    fullData = fullData,
    width = width,
    height = height
  )

  return createMatrixFromBounds(
    fullData = fullData,
    width = width,
    bounds = bounds
  )
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun extractRawImageData(
  rawData: UByteArray,
  qrImage: CGImageRef,
  sharedColorSpace: CGColorSpaceRef?,
  width: Int,
  height: Int,
) {
  rawData.usePinned { pinnedData ->
    val rawPtr = pinnedData.addressOf(0)
    val context = CGBitmapContextCreate(
      data = rawPtr,
      width = width.toULong(),
      height = height.toULong(),
      bitsPerComponent = 8u,
      bytesPerRow = width.toULong(),
      space = sharedColorSpace,
      bitmapInfo = 0u
    )

    val rect = CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble())
    CGContextDrawImage(context, rect, qrImage)
    CGContextRelease(context)
  }
}

private fun convertToMatrixWithBounds(
  rawData: UByteArray,
  fullData: BooleanArray,
  width: Int,
  height: Int,
): MatrixBounds {
  var minRow = height
  var maxRow = -1
  var minCol = width
  var maxCol = -1

  for (index in rawData.indices) {
    val shouldDrawDot = rawData[index] == 0.toUByte()
    fullData[index] = shouldDrawDot

    if (shouldDrawDot) {
      val row = index / width
      val col = index % width
      minRow = minOf(minRow, row)
      maxRow = maxOf(maxRow, row)
      minCol = minOf(minCol, col)
      maxCol = maxOf(maxCol, col)
    }
  }

  return MatrixBounds(
    minRow = minRow,
    maxRow = maxRow,
    minCol = minCol,
    maxCol = maxCol
  )
}

private fun createMatrixFromBounds(
  fullData: BooleanArray,
  width: Int,
  bounds: MatrixBounds,
): Result<QRMatrix, Error> {
  return if (bounds.minRow <= bounds.maxRow && bounds.minCol <= bounds.maxCol) {
    createCroppedMatrix(
      fullData = fullData,
      originalWidth = width,
      minRow = bounds.minRow,
      maxRow = bounds.maxRow,
      minCol = bounds.minCol,
      maxCol = bounds.maxCol
    )
  } else {
    Err(
      Error(
        cause = QrCodeGenerationException("Generated QR code is empty")
      )
    )
  }
}

/**
 * Crops any "quiet zone" surrounding the QR code, i.e., removes any totally blank edges from the
 * QR code matrix.
 */
private fun createCroppedMatrix(
  fullData: BooleanArray,
  originalWidth: Int,
  minRow: Int,
  maxRow: Int,
  minCol: Int,
  maxCol: Int,
): Result<QRMatrix, Error> {
  val croppedWidth = maxCol - minCol + 1
  val croppedHeight = maxRow - minRow + 1

  val croppedData = BooleanArray(croppedWidth * croppedHeight) { index ->
    val row = index / croppedWidth
    val col = index % croppedWidth
    val originalRow = minRow + row
    val originalCol = minCol + col
    val originalIndex = originalRow * originalWidth + originalCol
    fullData[originalIndex]
  }

  return Ok(QRMatrix(croppedWidth, croppedData))
}

/**
 * Data class containing the min and max column and row bounds for a given QRMatrix.
 */
data class MatrixBounds(
  val minRow: Int,
  val maxRow: Int,
  val minCol: Int,
  val maxCol: Int,
)
