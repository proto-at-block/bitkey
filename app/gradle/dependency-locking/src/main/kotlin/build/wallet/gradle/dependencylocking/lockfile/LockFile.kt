package build.wallet.gradle.dependencylocking.lockfile

import build.wallet.gradle.dependencylocking.configuration.DependencyLockingGroup
import build.wallet.gradle.dependencylocking.configuration.LockableVariant

internal data class LockFile(
  val lockedModules: List<LockedModule> = emptyList(),
) {
  val lockedModulesByModuleIdentifier: Map<ModuleIdentifier, LockedModule> =
    lockedModules.associateBy { it.moduleIdentifier }

  init {
    require(lockedModules.size == lockedModulesByModuleIdentifier.size) {
      "Locked modules contain duplicated modules."
    }
  }

  data class LockedModule(
    val moduleIdentifier: ModuleIdentifier,
    val components: List<Component>,
  ) {
    val componentsByVersion: Map<ComponentVersion, Component> =
      components.associateBy { it.coordinates.componentVersion }

    val componentsByDependencyLockingGroup: Map<DependencyLockingGroup, Component> =
      components
        .flatMap { component ->
          component.dependencyLockingGroups.map { it to component }
        }
        .also {
          require(it.size == it.distinctBy { entry -> entry.first }.size) {
            "Components for '$moduleIdentifier' contain duplicated entries - some dependency locking groups are assigned to two or more component."
          }
        }
        .toMap()

    init {
      require(components.size == componentsByVersion.size) {
        "Components for '$moduleIdentifier' contain duplicated entries - some versions are declared more than once."
      }

      require(components.isNotEmpty()) { "Expected at least one component for '$moduleIdentifier'." }
    }

    companion object
  }

  data class Component(
    val coordinates: LockableVariant.Coordinates,
    val variants: List<Variant>,
    val dependencyLockingGroups: List<DependencyLockingGroup>,
  ) {
    val variantsById: Map<String, Variant> by lazy {
      variants.associateBy { it.id }
    }

    init {
      require(variants.isNotEmpty()) {
        "Artifact '$coordinates' must have at least one variant."
      }

      require(dependencyLockingGroups.isNotEmpty()) {
        "Artifact '$coordinates' must have at least one dependency locking group."
      }

      require(variants.size == variantsById.size) {
        "Variants for '$coordinates' contain duplicated entries."
      }
    }
  }

  data class Variant(
    val artifacts: List<Artifact>,
  ) {
    val id: String by lazy {
      artifacts.sortedBy { it.name.value }.joinToString()
    }

    val artifactsByName: Map<ArtifactName, Artifact> =
      artifacts.associateBy { it.name }

    init {
      require(artifacts.size == artifactsByName.size) {
        "Artifacts contain duplicated entries - some artifacts are declared more than once."
      }

      require(artifacts.isNotEmpty()) {
        "Each variant must have at least one artifact."
      }
    }

    override fun toString(): String = artifacts.toString()
  }

  data class Artifact(val name: ArtifactName, val hash: ArtifactHash)

  companion object
}
