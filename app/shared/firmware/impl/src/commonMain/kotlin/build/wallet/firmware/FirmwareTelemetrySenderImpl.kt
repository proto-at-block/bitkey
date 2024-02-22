package build.wallet.firmware

import build.wallet.memfault.MemfaultService
import build.wallet.queueprocessor.Processor
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding

class FirmwareTelemetrySenderImpl(
  private val memfault: MemfaultService,
) : Processor<FirmwareTelemetryEvent> {
  override suspend fun processBatch(batch: List<FirmwareTelemetryEvent>): Result<Unit, Error> {
    return binding {
      for (event in batch) {
        memfault.uploadTelemetryEvent(event.event.toByteArray(), event.serial).bind()
      }
    }
  }
}
