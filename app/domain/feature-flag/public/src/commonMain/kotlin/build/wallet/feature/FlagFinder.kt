package build.wallet.feature

import kotlinx.collections.immutable.ImmutableList

interface FlagFinderFactory {
  fun index(all: List<FeatureFlag<out FeatureFlagValue>>): FlagFinder
}

interface FlagFinder {
  fun find(query: String): ImmutableList<FeatureFlag<out FeatureFlagValue>>
}
