package build.wallet.emergencyaccesskit

import build.wallet.platform.PlatformContext
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import okio.ByteString
import okio.ByteString.Companion.toByteString
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfURL

actual class EmergencyAccessKitTemplateProviderImpl actual constructor(
  platformContext: PlatformContext,
) : EmergencyAccessKitTemplateProvider {
  override suspend fun pdfTemplateBytes(): Result<ByteString, EmergencyAccessKitTemplateUnavailableError> {
    val templateData = resourceData("EmergencyAccessKitTemplate000", "pdf")
    if (templateData == null) {
      return Err(EmergencyAccessKitTemplateUnavailableError(null))
    } else {
      return Ok(templateData.toByteString())
    }
  }

  private fun resourceData(
    fileName: String,
    fileExtension: String,
  ): NSData? {
    val resourceURL = NSBundle.mainBundle.URLForResource(fileName, fileExtension) ?: return null
    return NSData.dataWithContentsOfURL(resourceURL)
  }
}
