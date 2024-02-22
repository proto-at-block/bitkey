package build.wallet.platform.pdf

import build.wallet.catching
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toResultOr
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import okio.ByteString
import okio.ByteString.Companion.toByteString
import okio.use
import java.io.ByteArrayOutputStream

class PdfAnnotatorImpl(private val document: PDDocument) : PdfAnnotator {
  private val fontMap: Map<String, PDType1Font> =
    mapOf(
      "Helvetica" to PDType1Font.HELVETICA,
      "Times-Roman" to PDType1Font.TIMES_ROMAN,
      "Courier" to PDType1Font.COURIER
    )

  override fun close() {
    document.close()
  }

  override fun serializeBlocking(): PdfAnnotationResult<ByteString> {
    return binding {
      val outputBuffer = ByteArrayOutputStream()
      Result
        .catching {
          document.save(outputBuffer)
        }.mapError { PdfAnnotationError.SerializeFailed }
        .bind()

      outputBuffer.toByteArray().toByteString()
    }.toPdfAnnotationResult()
  }

  override fun addTextBlocking(
    text: TextAnnotation,
    pageNum: Int,
    frame: PdfFrame,
    url: String?,
  ): PdfAnnotationResult<Unit> {
    return binding {
      val page = getPage(pageNum).bind()
      val font = text.getFont().toResultOr { PdfAnnotationError.InvalidFont }.bind()
      val fontSize = text.fontSize
      val adjustedFrame = adjustedFrame(frame, font, fontSize)
      val lineSpacing = lineSpacing(font, fontSize)
      val textLines = wrappedTextLines(text.contents, font, fontSize, frame.width)

      getContentStream(page).use {
        it.beginText()
        it.setNonStrokingColor(text.color.r, text.color.g, text.color.b)
        it.setFont(font, text.fontSize)
        it.newLineAtOffset(adjustedFrame.x, adjustedFrame.y)

        for ((textLineIndex, textLine) in textLines.withIndex()) {
          if (textLineIndex > 0) {
            it.newLineAtOffset(0f, -lineSpacing)
          }
          it.showText(textLine)
        }

        it.endText()
      }

      if (url != null) {
        url.addAsUrl(adjustedFrame, page)
      }

      Unit
    }.toPdfAnnotationResult()
  }

  private fun lineSpacing(
    font: PDType1Font,
    fontSize: Float,
  ): Float {
    val fontHeight = font.fontDescriptor.capHeight / 1000 * fontSize
    val lineSpacingMultiple = 0.75f
    val lineSpacing = fontHeight + (fontHeight * lineSpacingMultiple)
    return lineSpacing
  }

  private fun wrappedTextLines(
    text: String,
    font: PDType1Font,
    fontSize: Float,
    maxWidth: Float,
  ): Array<String> {
    var textLines = arrayOf<String>()
    var currentLine = ""

    for (character in text) {
      val currentLineNextCharacterWidth = font.getStringWidth(currentLine + character) / 1000 * fontSize
      if (currentLineNextCharacterWidth > maxWidth) {
        // If adding the next character exceeds max width, append current line to [textLines].
        textLines += currentLine
        currentLine = character.toString()
      } else {
        currentLine += character
      }
    }

    // Add any remaining text.
    if (currentLine.isNotEmpty()) {
      textLines += currentLine
    }

    return textLines
  }

  override fun addImageDataBlocking(
    data: ByteString,
    pageNum: Int,
    frame: PdfFrame,
    url: String?,
  ): PdfAnnotationResult<Unit> {
    return binding {
      val page = getPage(pageNum).bind()

      val pdImage =
        Result.catching {
          PDImageXObject.createFromByteArray(document, data.toByteArray(), null)
        }.mapError { PdfAnnotationError.InvalidImage }.bind()

      getContentStream(page).use {
        it.drawImage(pdImage, frame.x, frame.y, frame.width, frame.height)
      }

      url.addAsUrl(frame, page)

      Unit
    }.toPdfAnnotationResult()
  }

  private fun getPage(pageNum: Int): Result<PDPage, PdfAnnotationError> {
    return Result.catching {
      document.getPage(pageNum)
    }.mapError { PdfAnnotationError.InvalidPage }
  }

  private fun getContentStream(page: PDPage): PDPageContentStream {
    return PDPageContentStream(
      document,
      page,
      PDPageContentStream.AppendMode.APPEND,
      true,
      true
    )
  }

  private fun String?.addAsUrl(
    frame: PdfFrame,
    page: PDPage,
  ) = this?.let {
    val linkAnnotation = PDAnnotationLink()
    linkAnnotation.rectangle = PDRectangle(frame.x, frame.y, frame.width, frame.height)
    val action = PDActionURI()
    action.uri = this
    linkAnnotation.action = action
    page.annotations.add(linkAnnotation)
  }

  private fun TextAnnotation.getFont(): PDType1Font? {
    return fontMap[font]
  }

  /**
   * PdfBox-Android has a different native text y position when compared to iOS PDFKit. This
   * adjustment logic reconciles the two implementations by offsetting the PdfBox frame Y by the
   * frame height subtracted by the font's ascender.
   */
  private fun adjustedFrame(
    originalFrame: PdfFrame,
    font: PDFont,
    fontSize: Float,
  ): PdfFrame {
    val ascenderHeight = font.fontDescriptor.ascent / 1000 * fontSize
    val adjustedFrameY = originalFrame.y + originalFrame.height - ascenderHeight
    return PdfFrame(originalFrame.x, adjustedFrameY, originalFrame.width, originalFrame.height)
  }
}
