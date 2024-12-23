package build.wallet.emergencyaccesskit

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.*
import kotlinx.cinterop.ExperimentalForeignApi
import okio.ByteString
import okio.ByteString.Companion.toByteString
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreImage.CIColor
import platform.CoreImage.CIFilter
import platform.CoreImage.CIImage
import platform.CoreImage.QRCodeGenerator
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataUsingEncoding
import platform.Foundation.setValue
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation

@BitkeyInject(AppScope::class)
class EmergencyAccessKitQrCodeGeneratorImpl : EmergencyAccessKitQrCodeGenerator {
  override suspend fun imageBytes(
    width: Float,
    height: Float,
    contents: String,
  ): Result<ByteString, EmergencyAccessKitQrCodeError> =
    generateQRCode(contents)
      .map { it.toByteString() }
      .toErrorIfNull { EmergencyAccessKitQrCodeError(null) }

  @OptIn(ExperimentalForeignApi::class)
  private fun generateQRCode(contents: String): Result<CIImage, EmergencyAccessKitQrCodeError> {
    // Set up QR code filter with input data.
    @Suppress("CAST_NEVER_SUCCEEDS")
    val contentsData =
      (contents as NSString).dataUsingEncoding(NSUTF8StringEncoding)
        ?: return Err(EmergencyAccessKitQrCodeError(null))

    val qrFilter = CIFilter.QRCodeGenerator()
    qrFilter.setValue(contentsData, forKey = "inputMessage")

    // Scale QR code up to not be blurry.
    val scale = 10.0
    val scaleTransform = CGAffineTransformMakeScale(sx = scale, sy = scale)
    val scaledOutput =
      qrFilter.outputImage?.imageByApplyingTransform(scaleTransform)
        ?: return Err(EmergencyAccessKitQrCodeError(null))

    // Colorize QR code to have clear background.
    val coloredQRCode =
      scaledOutput.imageByApplyingFilter(
        "CIFalseColor",
        mapOf(
          "inputColor0" to CIColor.blackColor(),
          "inputColor1" to CIColor.clearColor()
        )
      )

    return Ok(coloredQRCode)
  }

  private fun CIImage.toByteString(): ByteString? {
    val uiImage = UIImage.imageWithCIImage(this)
    return UIImagePNGRepresentation(uiImage)?.toByteString()
  }
}
