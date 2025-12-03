package build.wallet.recovery.sweep

import build.wallet.bitkey.keybox.Keybox
import build.wallet.recovery.sweep.SweepGenerator.SweepGeneratorError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class SweepGeneratorMock : SweepGenerator {
  var generateSweepResult: Result<List<SweepPsbt>, SweepGeneratorError>? = null

  override suspend fun generateSweep(
    keybox: Keybox,
    context: SweepGenerationContext,
  ): Result<List<SweepPsbt>, SweepGeneratorError> {
    return generateSweepResult ?: Ok(listOf())
  }

  fun reset() {
    generateSweepResult = null
  }
}
