package build.wallet.platform.pdf

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import okio.ByteString

@BitkeyInject(AppScope::class)
class PdfAnnotatorFactoryImpl : PdfAnnotatorFactory {
  override fun createBlocking(pdfTemplateData: ByteString): PdfAnnotationResult<PdfAnnotator> {
    return PdfAnnotationResult.Ok(PdfAnnotatorImpl())
  }
}
