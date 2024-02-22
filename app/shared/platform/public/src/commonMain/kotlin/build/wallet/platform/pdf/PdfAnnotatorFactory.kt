package build.wallet.platform.pdf

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.ByteString

interface PdfAnnotatorFactory {
  fun createBlocking(pdfTemplateData: ByteString): PdfAnnotationResult<PdfAnnotator>
}

suspend fun PdfAnnotatorFactory.create(
  pdfTemplateData: ByteString,
): PdfAnnotationResult<PdfAnnotator> {
  return withContext(Dispatchers.Default) {
    createBlocking(pdfTemplateData)
  }
}
