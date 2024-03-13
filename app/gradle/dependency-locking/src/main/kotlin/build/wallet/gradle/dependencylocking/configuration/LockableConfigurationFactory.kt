package build.wallet.gradle.dependencylocking.configuration

import build.wallet.gradle.dependencylocking.lockfile.ModuleIdentifier
import build.wallet.gradle.dependencylocking.util.externalModuleOwner
import build.wallet.gradle.dependencylocking.util.getId
import build.wallet.gradle.dependencylocking.util.isFromExternalModule
import org.gradle.api.artifacts.Configuration

internal class LockableConfigurationFactory(
  private val lockableVariantFactory: LockableVariantFactory,
  private val dependencyLockingConfig: DependencyLockingConfig,
) {
  fun create(configurationWithOrigin: ConfigurationWithOrigin): LockableConfiguration {
    val id = configurationWithOrigin.configuration.getId(configurationWithOrigin.origin)

    val config = dependencyLockingConfig[id]

    require(config.isLocked) {
      "Cannot create LockableConfiguration for configuration '$id' which does not have locking enabled."
    }

    return LockableConfiguration(
      id = id,
      group = config.group,
      lockableVariants = configurationWithOrigin.configuration.lockableVariants
    )
  }

  fun createOrNull(configurationWithOrigin: ConfigurationWithOrigin): LockableConfiguration? =
    try {
      create(configurationWithOrigin)
    } catch (_: Throwable) {
      null
    }

  private val Configuration.lockableVariants: List<LockableVariant>
    get() = incoming.artifactView { isLenient = true }.artifacts
      .filter { it.isFromExternalModule }
      .groupBy { ModuleIdentifier(it.externalModuleOwner.moduleIdentifier) }
      .values
      .map { lockableVariantFactory.create(it) }
      .filter {
          variant ->
        dependencyLockingConfig.ignoredModules.none { it.matches(variant.moduleIdentifier) }
      }
}
