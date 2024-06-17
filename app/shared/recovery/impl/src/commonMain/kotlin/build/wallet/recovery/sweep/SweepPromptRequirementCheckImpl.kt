package build.wallet.recovery.sweep

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SweepPromptRequirementCheckImpl(
  promptSweepFeatureFlag: PromptSweepFeatureFlag,
) : SweepPromptRequirementCheck {
  override val sweepRequired: Flow<Boolean> = promptSweepFeatureFlag.flagValue().map { it.value }
}
