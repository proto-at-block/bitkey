package build.wallet.firmware

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.memfault.MemfaultClient
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding

@BitkeyInject(AppScope::class)
class FirmwareCoredumpEventProcessorImpl(
  private val memfault: MemfaultClient,
) : FirmwareCoredumpEventProcessor {
  override suspend fun processBatch(batch: List<FirmwareCoredump>): Result<Unit, Error> {
    return coroutineBinding {
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
