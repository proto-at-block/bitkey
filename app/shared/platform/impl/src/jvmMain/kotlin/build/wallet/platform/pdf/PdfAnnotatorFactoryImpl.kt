package build.wallet.platform.pdf

import okio.ByteString

class PdfAnnotatorFactoryImpl : PdfAnnotatorFactory {
  override fun createBlocking(pdfTemplateData: ByteString): PdfAnnotationResult<PdfAnnotator> {
    TODO("Tracked by BKR-898")
  }
}
