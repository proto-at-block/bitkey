package build.wallet.gradle.dependencylocking.configuration

import build.wallet.gradle.dependencylocking.lockfile.ModuleIdentifier
import build.wallet.gradle.dependencylocking.lockfile.ModuleIdentifierPattern

internal data class DependencyLockingConfig(
  val isEnabled: Boolean,
  val projectPath: String,
  val ignoredModules: Collection<ModuleIdentifierPattern>,
  val disabledForTaskPaths: Collection<String>,
  val requiredForTaskPaths: Collection<String>,
  private val configurationsConfig: Map<LockableConfiguration.Id, ConfigurationConfig>,
) {
  operator fun get(id: LockableConfiguration.Id): ConfigurationConfig =
    configurationsConfig[id] ?: ConfigurationConfig()

  fun isModuleIgnored(moduleIdentifier: ModuleIdentifier): Boolean =
    ignoredModules.any { it.matches(moduleIdentifier) }

  data class ConfigurationConfig(
    val isLocked: Boolean = true,
    val group: DependencyLockingGroup = DependencyLockingGroup.Unknown,
  )
}
