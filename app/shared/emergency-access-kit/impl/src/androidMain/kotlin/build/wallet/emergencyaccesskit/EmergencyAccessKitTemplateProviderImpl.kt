package build.wallet.emergencyaccesskit

import android.content.Context
import build.wallet.catchingResult
import build.wallet.platform.PlatformContext
import build.wallet.shared.emergency.access.kit.impl.R
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.ByteString
import okio.ByteString.Companion.toByteString

@Suppress("unused")
actual class EmergencyAccessKitTemplateProviderImpl actual constructor(
  private val platformContext: PlatformContext,
) : EmergencyAccessKitTemplateProvider {
  override suspend fun pdfTemplateBytes(): Result<ByteString, EmergencyAccessKitTemplateUnavailableError> =
    catchingResult {
      val resourceBytes =
        rawResourceBytes(
          platformContext.appContext,
          R.raw.emergency_access_kit_template_000
        )
      return Ok(resourceBytes)
    }
      .mapError { EmergencyAccessKitTemplateUnavailableError(it) }

  private suspend fun rawResourceBytes(
    context: Context,
    resourceId: Int,
  ): ByteString {
    return withContext(Dispatchers.IO) {
      val inputStream = context.resources.openRawResource(resourceId)
      val bytes = inputStream.readBytes()
      bytes.toByteString()
    }
  }
}
