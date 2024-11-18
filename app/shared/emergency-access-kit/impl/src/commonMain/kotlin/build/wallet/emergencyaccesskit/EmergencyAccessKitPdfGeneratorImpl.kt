package build.wallet.emergencyaccesskit

import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.platform.pdf.*
import build.wallet.time.DateTimeFormatter
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onSuccess
import kotlin.coroutines.cancellation.CancellationException

class EmergencyAccessKitPdfGeneratorImpl(
  private val apkParametersProvider: EmergencyAccessKitApkParametersProvider,
  private val mobileKeyParametersProvider: EmergencyAccessKitMobileKeyParametersProvider,
  private val pdfAnnotatorFactory: PdfAnnotatorFactory,
  private val templateProvider: EmergencyAccessKitTemplateProvider,
  private val backupDateProvider: EmergencyAccessKitBackupDateProvider,
  private val dateTimeFormatter: DateTimeFormatter,
  private val qrCodeGenerator: EmergencyAccessKitQrCodeGenerator,
) : EmergencyAccessKitPdfGenerator {
  override suspend fun generate(
    keybox: Keybox,
    sealedCsek: SealedCsek,
  ): Result<EmergencyAccessKitData, Error> =
    coroutineBinding {
      val templateBytes = templateProvider.pdfTemplateBytes().bind()
      val pdfAnnotator = pdfAnnotatorFactory.create(templateBytes).result.bind()

      pdfAnnotator.use { pdfAnnotator ->
        populateCreationDate(pdfAnnotator)

        val apkParameters = apkParametersProvider.parameters()
        populateAPKFields(pdfAnnotator, apkParameters)

        val mobileKeyParameters = mobileKeyParametersProvider.parameters(
          keybox,
          sealedCsek
        ).bind()
        populateMobileKeyFields(pdfAnnotator, mobileKeyParameters)

        pdfAnnotator
          .serialize()
          .result
          .map { EmergencyAccessKitData(it) }
          .bind()
      }
    }

  @Suppress("unused") // Used in iOS
  @Throws(Error::class, CancellationException::class)
  suspend fun generateOrThrow(
    keybox: Keybox,
    sealedCsek: SealedCsek,
  ): EmergencyAccessKitData {
    return generate(keybox, sealedCsek).getOrThrow()
  }

  private suspend fun populateCreationDate(pdfAnnotator: PdfAnnotator) {
    val backupDate = backupDateProvider.backupDate()
    val creationDate = dateTimeFormatter.longLocalDate(backupDate)

    pdfAnnotator.addBlackHelveticaText(
      text = creationDate,
      fontSize = FONT_SIZE_LARGE,
      pageNum = 0,
      frame = PdfFrame(135.0f, 3014.0f, 516.0f, 116.0f)
    )
  }

  private suspend fun populateAPKFields(
    pdfAnnotator: PdfAnnotator,
    apkParameters: ApkParameters,
  ) {
    pdfAnnotator.addBlackHelveticaText(
      text = "Version: " + apkParameters.apkVersion,
      fontSize = FONT_SIZE_MEDIUM,
      pageNum = 0,
      frame = PdfFrame(TEXT_BOX_X, 2190.0f, 1653.0f, 116.0f)
    )

    // Populate APK link text.
    pdfAnnotator.addBlackHelveticaText(
      text = apkParameters.apkLinkText,
      fontSize = FONT_SIZE_MEDIUM,
      pageNum = 0,
      frame = PdfFrame(TEXT_BOX_X, 2031.0f, 1653.0f, 116.0f),
      url = apkParameters.apkLinkUrl
    )

    // Populate APK QR code.
    if (apkParameters.apkLinkQRCodeText.isNotEmpty()) {
      pdfAnnotator.addQrCodeImage(
        contents = apkParameters.apkLinkQRCodeText,
        pageNum = 0,
        frameX = QR_CODE_FRAME_X,
        frameY = 1940.0f
      )
    }

    // Populate APK hash.
    pdfAnnotator.addBlackHelveticaText(
      text = apkParameters.apkHash,
      fontSize = FONT_SIZE_MEDIUM,
      pageNum = 0,
      frame = PdfFrame(TEXT_BOX_X, 790.0f, 1490.0f, 142.0f)
    )
  }

  private suspend fun populateMobileKeyFields(
    pdfAnnotator: PdfAnnotator,
    mobileKeyParameters: MobileKeyParameters,
  ) {
    // Populate Mobile Key characters.
    pdfAnnotator.addBlackHelveticaText(
      text = mobileKeyParameters.mobileKeyCharacters,
      fontSize = FONT_SIZE_MEDIUM,
      pageNum = 1,
      frame = PdfFrame(TEXT_BOX_X, 1198.0f, 1490.0f, 850.0f)
    )

    // Populate Mobile Key QR code.
    if (mobileKeyParameters.mobileKeyQRCodeText.isNotEmpty()) {
      pdfAnnotator.addQrCodeImage(
        contents = mobileKeyParameters.mobileKeyQRCodeText,
        pageNum = 1,
        frameX = QR_CODE_FRAME_X,
        frameY = 2176.0f
      )
    }
  }

  private companion object {
    const val QR_CODE_FRAME_X = 108.0f
    const val QR_CODE_LENGTH = 610.0f
    const val TEXT_BOX_X = 860.0f
    const val FONT_SIZE_LARGE = 52.0f
    const val FONT_SIZE_MEDIUM = 42.0f
  }

  private suspend fun PdfAnnotator.addBlackHelveticaText(
    text: String,
    fontSize: Float,
    pageNum: Int,
    frame: PdfFrame,
    url: String? = null,
  ) {
    addText(
      text = TextAnnotation(
        contents = text,
        font = "Helvetica",
        fontSize = fontSize,
        color = PdfColor(0.0f, 0.0f, 0.0f)
      ),
      pageNum = pageNum,
      frame = frame,
      url = url
    )
  }

  private suspend fun PdfAnnotator.addQrCodeImage(
    contents: String,
    pageNum: Int,
    frameX: Float,
    frameY: Float,
  ) {
    val qrCodeLength = QR_CODE_LENGTH
    qrCodeGenerator.imageBytes(qrCodeLength, qrCodeLength, contents)
      .onSuccess { qrCodeData ->
        addImageData(
          data = qrCodeData,
          pageNum = pageNum,
          frame = PdfFrame(frameX, frameY, qrCodeLength, qrCodeLength),
          url = null
        )
      }
  }
}
