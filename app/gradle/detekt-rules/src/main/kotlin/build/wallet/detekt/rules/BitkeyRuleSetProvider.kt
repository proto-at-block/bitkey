package build.wallet.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/**
 * Custom Detekt rule set for Bitkey-specific linting rules.
 */
class BitkeyRuleSetProvider : RuleSetProvider {
  override val ruleSetId: String = "bitkey"

  override fun instance(config: Config): RuleSet =
    RuleSet(
      id = ruleSetId,
      rules = listOf(
        NoFocusedKotestTests(config.subConfig(NoFocusedKotestTests::class.simpleName!!)),
        NoUnrememberedCollectAsState(config.subConfig(NoUnrememberedCollectAsState::class.simpleName!!))
      )
    )
}
