package build.wallet.gradle.logic.ksp

import org.gradle.api.artifacts.Dependency

/**
 * Unique KSP configuration:
 * @param deps what KSP processors should be added.
 * @param android if KSP processor should be configured for Android target.
 * @param jvm if KSP processor should be configured for JVM target.
 * @param ios if KSP processor should be configured for iOS target.
 */
data class KspProcessorConfig(
  val deps: List<Dependency>,
  val android: Boolean,
  val jvm: Boolean,
  val ios: Boolean,
) {
  init {
    require(deps.any()) { "Expected some KSP processors." }
  }

  internal val targets = KspProcessorTargets(android = android, jvm = jvm, ios = ios)
}

/**
 * Specifies what KMP targets the KSP processor should be configured for.
 * Expects a KMP target to be available.
 */
internal data class KspProcessorTargets(
  val android: Boolean,
  val ios: Boolean,
  val jvm: Boolean,
) {
  init {
    require(android || ios || jvm) { "At least one KSP target must be specified" }
  }
}
