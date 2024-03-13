package build.wallet.gradle.dependencylocking.extension

import build.wallet.gradle.dependencylocking.configuration.DependencyLockingGroup
import org.gradle.api.Named
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyConstraint
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject

abstract class DependencyLockingGroupConfig : Named {
  // For some reason it's impossible to use constructor injection
  @get:Inject
  protected abstract val providerFactory: ProviderFactory

  internal val group: DependencyLockingGroup
    get() = DependencyLockingGroup.Known(name)

  private val pinnedDependencies = mutableListOf<Provider<DependencyConstraint>>()

  fun pin(vararg dependencies: Provider<out ExternalModuleDependency>) {
    dependencies.forEach { dependency ->
      pinnedDependencies.add(
        dependency.map {
          // Uses internal Gradle API but there seems to be no better way to configure constraints without touching `Project` which sometimes breaks configuration cache.
          DefaultDependencyConstraint.strictly(it.group, it.name, it.version ?: "")
        }
      )
    }
  }

  fun pin(vararg dependencyNotations: String) {
    dependencyNotations.forEach { dependency ->
      val components = dependency.split(":")
      require(components.size == 3) {
        "Dependency notation requires format 'group:name:version'. Was: $dependency"
      }

      pinnedDependencies.add(
        providerFactory.provider {
          DefaultDependencyConstraint.strictly(components[0], components[1], components[2])
        }
      )
    }
  }

  internal fun pinDependenciesIn(configuration: Configuration) {
    pinnedDependencies.forEach {
      configuration.dependencyConstraints.addLater(it)
    }
  }
}
