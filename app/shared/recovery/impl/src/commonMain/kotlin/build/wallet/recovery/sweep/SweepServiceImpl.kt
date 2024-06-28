package build.wallet.recovery.sweep

import build.wallet.bitkey.keybox.Keybox
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding

class SweepServiceImpl(
  private val sweepGenerator: SweepGenerator,
) : SweepService {
  override suspend fun prepareSweep(keybox: Keybox): Result<Sweep?, Error> =
    coroutineBinding {
      val sweepPsbts = sweepGenerator.generateSweep(keybox).bind()

      when {
        sweepPsbts.isEmpty() -> null
        else -> Sweep(unsignedPsbts = sweepPsbts.toSet())
      }
    }
}
