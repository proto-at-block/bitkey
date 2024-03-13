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
    configuration: Configuration,
  ) {
    val isLocked: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

    val dependencyLockingGroup: Property<DependencyLockingGroupConfig> =
      objects.property(DependencyLockingGroupConfig::class.java)

    init {
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
