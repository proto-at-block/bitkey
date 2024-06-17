package build.wallet.firmware

import build.wallet.memfault.MemfaultClient
import build.wallet.queueprocessor.Processor
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding

class FirmwareTelemetrySenderImpl(
  private val memfault: MemfaultClient,
) : Processor<FirmwareTelemetryEvent> {
  override suspend fun processBatch(batch: List<FirmwareTelemetryEvent>): Result<Unit, Error> {
    return coroutineBinding {
      for (event in batch) {
        memfault.uploadTelemetryEvent(event.event.toByteArray(), event.serial).bind()
      }
    }
  }
}
