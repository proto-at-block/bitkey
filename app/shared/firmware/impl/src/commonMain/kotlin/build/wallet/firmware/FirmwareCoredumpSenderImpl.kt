package build.wallet.firmware

import build.wallet.memfault.MemfaultService
import build.wallet.queueprocessor.Processor
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding

class FirmwareCoredumpSenderImpl(
  private val memfault: MemfaultService,
) : Processor<FirmwareCoredump> {
  override suspend fun processBatch(batch: List<FirmwareCoredump>): Result<Unit, Error> {
    return binding {
      for (coredump in batch) {
        memfault.uploadCoredump(
          coredump.coredump,
          coredump.identifiers.serial,
          coredump.identifiers.hwRevision,
          coredump.identifiers.swType,
          coredump.identifiers.version
        ).bind()
      }
    }
  }
}
