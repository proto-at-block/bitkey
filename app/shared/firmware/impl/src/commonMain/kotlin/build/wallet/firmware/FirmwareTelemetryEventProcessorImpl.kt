package build.wallet.firmware

import build.wallet.memfault.MemfaultClient
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding

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
