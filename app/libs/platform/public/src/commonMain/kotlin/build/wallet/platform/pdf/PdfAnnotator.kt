package build.wallet.platform.pdf

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.ByteString

/**
 * A type that allows PDF markup and annotations.
 * Allows iOS and Android to implement platform-specific functionality.
 *
 * Caveat: Due to how the iOS implementation handles adding content, URLs
 * must be passed when adding text and images. The final serialize step places
 * the actual URLs in the PDF. This means that on iOS the serialize step
 * can fail because of link placement. Calling serialize multiple times will
 * remove previously added links.
 */
interface PdfAnnotator : AutoCloseable {
  /**
   * Serialize the annotated PDF to a byte array for storage.
   *
   * @return A byte array containing the new PDF data or errors:
   * - SerializeFailed: The annotated PDF could not be serialized.
   */
  fun serializeBlocking(): PdfAnnotationResult<ByteString>

  /**
   * Add a text annotation to the PDF template with an optional URL.
   *
   * @param text The text to write.
   * - iOS: The text will wrap in `Frame` but will be truncated if there is not enough space.
   * - Android: The text will overflow the `Frame` and will not wrap to new lines.
   * @param pageNum Page number, starting from zero.
   * @param frame Frame to place the text. The origin in a PDF is the bottom left corner.
   * @param url Optional URL for the text. If present, a link annotation will be added over the text.
   * @return Potential errors:
   * - Invalid page number
   * - Invalid font
   * - Invalid URL
   */
  fun addTextBlocking(
    text: TextAnnotation,
    pageNum: Int,
    frame: PdfFrame,
    url: String?,
  ): PdfAnnotationResult<Unit>

  /**
   * Add an image annotation to the PDF template with an optional URL.
   *
   * @param data The raw image data to place. Expected to be a png/jpeg.
   * @param pageNum Page number, starting from zero.
   * @param frame Frame to place the text. The origin in a PDF is the bottom left corner.
   * The image will be sized to fit the passed frame.
   * @param url Optional URL for the image. If present, a link annotation will be added over the image.
   * @return Potential errors:
   * - Invalid page number
   * - Invalid image data
   * - Invalid URL
   */
  fun addImageDataBlocking(
    data: ByteString,
    pageNum: Int,
    frame: PdfFrame,
    url: String?,
  ): PdfAnnotationResult<Unit>
}

/**
 * Frame for PDF annotation placement.
 * NOTE: PDFs coordinates are from the *bottom left* corner. Values are in
 * points, so exact placement will be dependent on the size and DPI of the
 * template PDF.
 */
data class PdfFrame(val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * RGB Color definition for PDF Annotations. Values must be [0.0..1.0].
 */
data class PdfColor(val r: Float, val g: Float, val b: Float)

/**
 * Annotation definition for free text in a PDF. The font listed must be a valid PDF type 1 font
 * that is available on iOS and Android (eg: "Helvetica", "Times-Roman", and "Courier").
 */
data class TextAnnotation(
  val contents: String,
  val font: String,
  val fontSize: Float,
  val color: PdfColor,
)

suspend fun PdfAnnotator.serialize(): PdfAnnotationResult<ByteString> {
  return withContext(Dispatchers.Default) {
    serializeBlocking()
  }
}

suspend fun PdfAnnotator.addText(
  text: TextAnnotation,
  pageNum: Int,
  frame: PdfFrame,
  url: String?,
): PdfAnnotationResult<Unit> {
  return withContext(Dispatchers.Default) {
    addTextBlocking(text, pageNum, frame, url)
  }
}

suspend fun PdfAnnotator.addImageData(
  data: ByteString,
  pageNum: Int,
  frame: PdfFrame,
  url: String?,
): PdfAnnotationResult<Unit> {
  return withContext(Dispatchers.Default) {
    addImageDataBlocking(data, pageNum, frame, url)
  }
}
