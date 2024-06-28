package build.wallet.recovery.sweep

import build.wallet.bitkey.keybox.Keybox
import build.wallet.feature.flags.PromptSweepFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.logging.logFailure
import com.github.michaelbull.result.getOr
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SweepPromptRequirementCheckImpl(
  private val promptSweepFeatureFlag: PromptSweepFeatureFlag,
  private val sweepGenerator: SweepGenerator,
) : SweepPromptRequirementCheck {
  private val lastKnownState = MutableStateFlow(false)
  override val sweepRequired: StateFlow<Boolean> = lastKnownState

  override suspend fun checkForSweeps(keybox: Keybox): Boolean {
    if (!promptSweepFeatureFlag.isEnabled()) {
      return false
    }

    return sweepGenerator.generateSweep(keybox)
      .map { it.isNotEmpty() }
      .logFailure { "Failure Generating Sweep used to check for funds on old wallets" }
      .getOr(false)
      .also {
        lastKnownState.value = it
      }
  }
}
