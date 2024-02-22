package build.wallet.platform.pdf

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.Err as ResultErr
import com.github.michaelbull.result.Ok as ResultOk

/**
 * Wrapper for [Result] type that only uses [PdfAnnotationError] for errors.
 *
 * Primarily used for Swift interop. With Kotlin/Native, most of the generics get erased
 * in generated ObjC code, which makes consuming code with generics in Swift hard and tedious.
 */
sealed class PdfAnnotationResult<out V : Any> {
  abstract val result: Result<V, PdfAnnotationError>

  /**
   * Wraps [Result.Ok] with [PdfAnnotationError] as an error type.
   */
  data class Ok<V : Any>(val value: V) : PdfAnnotationResult<V>() {
    override val result: Result<V, PdfAnnotationError> = ResultOk(value)
  }

  /**
   * Wraps [Result.Err] with [PdfAnnotationError] as an error type.
   */
  data class Err<V : Any>(val error: PdfAnnotationError) : PdfAnnotationResult<V>() {
    override val result: Result<V, PdfAnnotationError> = ResultErr(error)
  }
}

sealed class PdfAnnotationError : Error() {
  /** The input data could not be parsed into a PDF. */
  data object InvalidData : PdfAnnotationError()

  /** The page number does not exist in the PDF. */
  data object InvalidPage : PdfAnnotationError()

  /** The font requested is not available. */
  data object InvalidFont : PdfAnnotationError()

  /** The image data passed could not be parsed into a valid image. */
  data object InvalidImage : PdfAnnotationError()

  /** The serialize step failed. Would likely be caused by an internal error in the platform library. */
  data object SerializeFailed : PdfAnnotationError()

  /** The URL string passed is not a valid URL. */
  data object InvalidURL : PdfAnnotationError()
}

fun <V : Any> Result<V, PdfAnnotationError>.toPdfAnnotationResult(): PdfAnnotationResult<V> {
  return when (this) {
    is Ok -> PdfAnnotationResult.Ok(this.value)
    is Err -> PdfAnnotationResult.Err(this.error)
  }
}
