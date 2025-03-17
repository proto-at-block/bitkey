package build.wallet.platform.pdf

import android.app.Application
import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.mapError
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import okio.ByteString

@BitkeyInject(AppScope::class)
class PdfAnnotatorFactoryImpl(private val application: Application) : PdfAnnotatorFactory {
  override fun createBlocking(pdfTemplateData: ByteString): PdfAnnotationResult<PdfAnnotator> {
    PDFBoxResourceLoader.init(application)

    return catchingResult {
      val document = PDDocument.load(pdfTemplateData.toByteArray())
      PdfAnnotatorImpl(document)
    }.mapError { PdfAnnotationError.InvalidData }
      .toPdfAnnotationResult()
  }
}
