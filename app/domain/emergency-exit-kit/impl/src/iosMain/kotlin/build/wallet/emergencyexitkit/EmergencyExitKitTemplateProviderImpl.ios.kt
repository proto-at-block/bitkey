package build.wallet.emergencyexitkit

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ensureNotNull
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import okio.ByteString
import okio.ByteString.Companion.toByteString
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfURL

@BitkeyInject(AppScope::class)
class EmergencyExitKitTemplateProviderImpl : EmergencyExitKitTemplateProvider {
  override suspend fun pdfTemplateBytes(): Result<ByteString, EmergencyExitKitTemplateUnavailableError> =
    binding {
      val templateData = resourceData("EmergencyExitKitTemplate000", "pdf")
      ensureNotNull(templateData) {
        EmergencyExitKitTemplateUnavailableError(null)
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
