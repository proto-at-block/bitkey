package build.wallet.emergencyaccesskit

import build.wallet.catching
import build.wallet.platform.PlatformContext
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.File

actual class EmergencyAccessKitTemplateProviderImpl actual constructor(
  platformContext: PlatformContext,
) : EmergencyAccessKitTemplateProvider {
  override suspend fun pdfTemplateBytes(): Result<ByteString, EmergencyAccessKitTemplateUnavailableError> =
    Result
      .catching {
        withContext(Dispatchers.IO) {
          File("src/androidMain/res/raw/template000.pdf")
            .readBytes()
            .toByteString()
        }
      }
      .mapError(::EmergencyAccessKitTemplateUnavailableError)
}
