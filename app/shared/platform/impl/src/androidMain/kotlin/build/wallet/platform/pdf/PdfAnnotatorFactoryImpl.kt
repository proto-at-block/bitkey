package build.wallet.platform.pdf

import android.content.Context
import build.wallet.catching
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import okio.ByteString

class PdfAnnotatorFactoryImpl(private val applicationContext: Context) : PdfAnnotatorFactory {
  override fun createBlocking(pdfTemplateData: ByteString): PdfAnnotationResult<PdfAnnotator> {
    PDFBoxResourceLoader.init(applicationContext)

    return Result.catching {
      val document = PDDocument.load(pdfTemplateData.toByteArray())
      PdfAnnotatorImpl(document)
    }.mapError { PdfAnnotationError.InvalidData }
      .toPdfAnnotationResult()
  }
}
