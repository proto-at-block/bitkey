package build.wallet.gradle.dependencylocking.extension

import build.wallet.gradle.dependencylocking.configuration.DependencyLockingConfig
import build.wallet.gradle.dependencylocking.configuration.DependencyLockingGroup
import org.gradle.api.artifacts.Configuration
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

internal abstract class DependencyLockingConfigurationConfig
  @Inject
  constructor(
    objects: ObjectFactory,
  ) {
    val isLocked: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

    val dependencyLockingGroup: Property<DependencyLockingGroupConfig> =
      objects.property(DependencyLockingGroupConfig::class.java)

    // Track whether this config was initialized with an unresolved configuration
    private var configurationForPinning: Configuration? = null

    /**
     * Initialize with a configuration that can have dependencies pinned.
     * Must be called before the configuration is resolved.
     * In Gradle 9+, configurations become immutable earlier, so we only
     * register callbacks for configurations that aren't yet resolved.
     */
    internal fun initWithConfiguration(configuration: Configuration) {
      configurationForPinning = configuration
      configuration.withDependencies {
        dependencyLockingGroup.orNull?.pinDependenciesIn(configuration)
      }
    }

    internal fun createConfig(): DependencyLockingConfig.ConfigurationConfig =
      DependencyLockingConfig.ConfigurationConfig(
        isLocked = isLocked.get(),
        group = dependencyLockingGroup.orNull?.group ?: DependencyLockingGroup.Unknown
      )
  }
