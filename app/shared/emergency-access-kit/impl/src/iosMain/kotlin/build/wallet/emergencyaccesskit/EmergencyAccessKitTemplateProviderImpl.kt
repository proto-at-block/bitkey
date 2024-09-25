package build.wallet.emergencyaccesskit

import build.wallet.ensureNotNull
import build.wallet.platform.PlatformContext
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import okio.ByteString
import okio.ByteString.Companion.toByteString
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfURL

actual class EmergencyAccessKitTemplateProviderImpl actual constructor(
  platformContext: PlatformContext,
) : EmergencyAccessKitTemplateProvider {
  override suspend fun pdfTemplateBytes(): Result<ByteString, EmergencyAccessKitTemplateUnavailableError> =
    binding {
      val templateData = resourceData("EmergencyAccessKitTemplate000", "pdf")
      ensureNotNull(templateData) {
        EmergencyAccessKitTemplateUnavailableError(null)
      }
      templateData.toByteString()
    }

  private fun resourceData(
    fileName: String,
    fileExtension: String,
  ): NSData? {
    val resourceURL = NSBundle.mainBundle.URLForResource(fileName, fileExtension) ?: return null
    return NSData.dataWithContentsOfURL(resourceURL)
  }
}
