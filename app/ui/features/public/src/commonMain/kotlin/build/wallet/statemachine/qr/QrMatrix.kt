package build.wallet.statemachine.qr

import com.github.michaelbull.result.Result

/**
 * Represents a QR code matrix as a flattened array.
 * @param columnWidth The width (number of columns) of the matrix
 * @param data The flattened matrix data where index = row * columnWidth + column
 */
data class QRMatrix(
  val columnWidth: Int,
  val data: BooleanArray,
) {
  val rowCount: Int get() = data.size / columnWidth

  operator fun get(
    row: Int,
    column: Int,
  ): Boolean {
    return data[row * columnWidth + column]
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || this::class != other::class) return false

    other as QRMatrix

    if (columnWidth != other.columnWidth) return false
    if (!data.contentEquals(other.data)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = columnWidth
    result = 31 * result + data.contentHashCode()
    return result
  }
}

/**
 * Converts a string to a QR code matrix.
 * @returns an [Error] wrapping a [QrCodeGenerationException] if QR code generation fails.
 */
expect suspend fun String.toQrMatrix(): Result<QRMatrix, Error>

/**
 * Exception thrown when QR code generation fails
 */
class QrCodeGenerationException(
  message: String,
  cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Represents the state of QR code generation
 */
sealed interface QrCodeState {
  /**
   * QR code is currently being generated
   */
  data object Loading : QrCodeState

  /**
   * QR code generation succeeded
   */
  data class Success(val matrix: QRMatrix) : QrCodeState

  /**
   * QR code generation failed
   */
  data object Error : QrCodeState
}
