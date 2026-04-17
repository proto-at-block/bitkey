package build.wallet.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class BitkeyRuleSetProvider : RuleSetProvider {
  // Keep in sync with the rule set id in detekt config.
  override val ruleSetId: String = "bitkey"

  override fun instance(config: Config): RuleSet {
    return RuleSet(
      id = ruleSetId,
      rules = listOf(
        MissingFeatureFlagInList(config.subConfig(MissingFeatureFlagInList::class.simpleName!!)),
        NoFocusedKotestTests(config.subConfig(NoFocusedKotestTests::class.simpleName!!)),
        NoKotlinResult(config.subConfig(NoKotlinResult::class.simpleName!!)),
        NoServiceImportInDao(config.subConfig(NoServiceImportInDao::class.simpleName!!)),
        NoUnrememberedCollectAsState(
          config.subConfig(NoUnrememberedCollectAsState::class.simpleName!!)
        ),
        RedundantStartupWithForegroundEvent(
          config.subConfig(RedundantStartupWithForegroundEvent::class.simpleName!!)
        )
      )
    )
  }
}
