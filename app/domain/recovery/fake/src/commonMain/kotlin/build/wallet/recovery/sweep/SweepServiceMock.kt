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
  var estimateSweepToActiveKeysetResult: Result<Sweep, SweepError> =
    Err(NoFundsToSweep)
  var estimateSweepWithMockDestinationResult: Result<Sweep, SweepError> =
    Err(NoFundsToSweep)
  var estimateSweepToActiveKeysetHandler: ((SweepContext) -> Result<Sweep, SweepError>)? = null

  val estimateSweepToActiveKeysetCalls = mutableListOf<SweepContext>()

  override val sweepRequired = MutableStateFlow(false)

  override suspend fun checkForSweeps() = Unit

  override fun markSweepHandled() {
    sweepRequired.value = false
  }

  override suspend fun prepareSweep(
    keybox: Keybox,
    sweepContext: SweepContext,
  ): Result<Sweep?, Error> {
    return prepareSweepResult
  }

  override suspend fun estimateSweepWithMockDestination(
    keybox: Keybox,
    sweepContext: SweepContext,
  ): Result<Sweep, SweepError> {
    return estimateSweepWithMockDestinationResult
  }

  override suspend fun estimateSweepToActiveKeyset(
    keybox: Keybox,
    sweepContext: SweepContext,
  ): Result<Sweep, SweepError> {
    estimateSweepToActiveKeysetCalls.add(sweepContext)
    return estimateSweepToActiveKeysetHandler?.invoke(sweepContext)
      ?: estimateSweepToActiveKeysetResult
  }

  fun reset() {
    prepareSweepResult = Ok(null)
    estimateSweepToActiveKeysetResult = Err(NoFundsToSweep)
    estimateSweepWithMockDestinationResult = Err(NoFundsToSweep)
    estimateSweepToActiveKeysetHandler = null
    estimateSweepToActiveKeysetCalls.clear()
    sweepRequired.value = false
  }
}
