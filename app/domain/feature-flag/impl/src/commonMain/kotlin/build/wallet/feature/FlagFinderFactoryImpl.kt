package build.wallet.feature

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@BitkeyInject(AppScope::class)
class FlagFinderFactoryImpl : FlagFinderFactory {
  override fun index(all: List<FeatureFlag<out FeatureFlagValue>>): FlagFinder {
    val originalList = all.toImmutableList()
    val root = FlagFinderImpl(originalList)
    all.associateBy { flag -> flag.title }.forEach { (key, value) ->
      var flagFinder = root
      var jaggedFinder = root
      key.forEach { c ->
        val uppercase = c.uppercaseChar()
        flagFinder.featureFlags.add(value)
        flagFinder = flagFinder.keys[uppercase] ?: FlagFinderImpl(originalList)
          .also { flagFinder.keys[uppercase] = it }
        if (c == uppercase && c != ' ') {
          jaggedFinder.featureFlags.add(value)
          jaggedFinder = jaggedFinder.keys[uppercase] ?: FlagFinderImpl(originalList)
            .also { jaggedFinder.keys[uppercase] = it }
        }
      }
    }
    return root
  }
}

class FlagFinderImpl(
  val originalList: ImmutableList<FeatureFlag<out FeatureFlagValue>>,
  val keys: MutableMap<Char, FlagFinderImpl> = mutableMapOf(),
  val featureFlags: MutableList<FeatureFlag<out FeatureFlagValue>> = mutableListOf(),
) : FlagFinder {
  override fun find(query: String): ImmutableList<FeatureFlag<out FeatureFlagValue>> {
    if (query.isBlank()) {
      return originalList
    }
    val findings = mutableMapOf<FeatureFlag<out FeatureFlagValue>, Int>()
    find(query, 0, this, findings)
    return findings.toList().sortedByDescending { it.second }.map { it.first }.toImmutableList()
  }

  private fun find(
    filter: String,
    index: Int,
    finder: FlagFinderImpl,
    findings: MutableMap<FeatureFlag<out FeatureFlagValue>, Int>,
  ) {
    if (index < 0 || index >= filter.length) return
    val next = finder.keys[filter[index].uppercaseChar()]
    if (next != null) {
      next.featureFlags.forEach { featureFlag ->
        findings[featureFlag] = 1 + (findings[featureFlag] ?: 0)
      }
      find(filter, index + 1, next, findings)
    }
  }
}
