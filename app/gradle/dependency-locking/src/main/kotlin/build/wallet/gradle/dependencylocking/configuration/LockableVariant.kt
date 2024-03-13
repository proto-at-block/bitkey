package build.wallet.gradle.dependencylocking.configuration

import build.wallet.gradle.dependencylocking.lockfile.ArtifactName
import build.wallet.gradle.dependencylocking.lockfile.ComponentVersion
import build.wallet.gradle.dependencylocking.lockfile.LockFile
import build.wallet.gradle.dependencylocking.lockfile.ModuleIdentifier

internal data class LockableVariant(
  val moduleIdentifier: ModuleIdentifier,
  val version: ComponentVersion,
  val artifacts: List<LockFile.Artifact>,
) {
  val coordinates: Coordinates = Coordinates(moduleIdentifier, version)

  val artifactsByName: Map<ArtifactName, LockFile.Artifact> by lazy {
    artifacts.associateBy { it.name }
  }

  init {
    require(artifacts.size == artifactsByName.size) {
      "Artifacts must have unique names. Was: $artifacts"
    }
  }

  data class Coordinates(
    val moduleIdentifier: ModuleIdentifier,
    val componentVersion: ComponentVersion,
  ) {
    override fun toString(): String = "$moduleIdentifier:$componentVersion"
  }
}
