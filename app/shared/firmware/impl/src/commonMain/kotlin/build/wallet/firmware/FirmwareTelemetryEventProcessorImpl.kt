package build.wallet.firmware

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.memfault.MemfaultClient
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding

@BitkeyInject(AppScope::class)
class FirmwareTelemetryEventProcessorImpl(
  private val memfault: MemfaultClient,
) : FirmwareTelemetryEventProcessor {
  override suspend fun processBatch(batch: List<FirmwareTelemetryEvent>): Result<Unit, Error> {
    return coroutineBinding {
      for (event in batch) {
        memfault.uploadTelemetryEvent(event.event.toByteArray(), event.serial).bind()
      }
    }
  }
}
