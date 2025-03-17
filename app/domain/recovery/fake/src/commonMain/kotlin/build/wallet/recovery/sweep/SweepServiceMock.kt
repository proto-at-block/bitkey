package build.wallet.recovery.sweep

import build.wallet.bitkey.keybox.Keybox
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

class SweepServiceMock : SweepService {
  var prepareSweepResult: Result<Sweep?, Error> = Ok(null)

  override val sweepRequired = MutableStateFlow(false)

  override suspend fun checkForSweeps() = Unit

  override suspend fun prepareSweep(keybox: Keybox): Result<Sweep?, Error> {
    return prepareSweepResult
  }

  fun reset() {
    prepareSweepResult = Ok(null)
    sweepRequired.value = false
  }
}
