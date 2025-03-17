package build.wallet.emergencyaccesskit

import android.app.Application
import android.content.Context
import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.domain.emergency.access.kit.impl.R
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.ByteString
import okio.ByteString.Companion.toByteString

@BitkeyInject(AppScope::class)
class EmergencyAccessKitTemplateProviderImpl(
  private val application: Application,
) : EmergencyAccessKitTemplateProvider {
  override suspend fun pdfTemplateBytes(): Result<ByteString, EmergencyAccessKitTemplateUnavailableError> =
    catchingResult {
      val resourceBytes =
        rawResourceBytes(
          application,
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
