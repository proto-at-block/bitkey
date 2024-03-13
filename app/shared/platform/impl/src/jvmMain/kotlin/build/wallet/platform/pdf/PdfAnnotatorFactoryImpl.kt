package build.wallet.platform.pdf

import okio.ByteString

class PdfAnnotatorFactoryImpl : PdfAnnotatorFactory {
  override fun createBlocking(pdfTemplateData: ByteString): PdfAnnotationResult<PdfAnnotator> {
    return PdfAnnotationResult.Ok(PdfAnnotatorImpl())
  }
}
