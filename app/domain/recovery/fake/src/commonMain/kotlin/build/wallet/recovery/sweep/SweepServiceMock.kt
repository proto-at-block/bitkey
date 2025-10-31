package build.wallet.recovery.sweep

import build.wallet.bitkey.keybox.Keybox
import build.wallet.recovery.sweep.SweepService.SweepError
import build.wallet.recovery.sweep.SweepService.SweepError.NoFundsToSweep
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

class SweepServiceMock : SweepService {
  var prepareSweepResult: Result<Sweep?, Error> = Ok(null)
  var estimateSweepWithMockDestinationResult: Result<Sweep, SweepError> =
    Err(NoFundsToSweep)

  override val sweepRequired = MutableStateFlow(false)

  override suspend fun checkForSweeps() = Unit

  override fun markSweepHandled() {
    sweepRequired.value = false
  }

  override suspend fun prepareSweep(keybox: Keybox): Result<Sweep?, Error> {
    return prepareSweepResult
  }

  override suspend fun estimateSweepWithMockDestination(
    keybox: Keybox,
  ): Result<Sweep, SweepError> {
    return estimateSweepWithMockDestinationResult
  }

  fun reset() {
    prepareSweepResult = Ok(null)
    estimateSweepWithMockDestinationResult = Err(NoFundsToSweep)
    sweepRequired.value = false
  }
}
