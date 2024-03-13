package build.wallet.gradle.dependencylocking.configuration

import build.wallet.gradle.dependencylocking.lockfile.ArtifactName
import build.wallet.gradle.dependencylocking.lockfile.ComponentVersion
import build.wallet.gradle.dependencylocking.lockfile.LockFile
import build.wallet.gradle.dependencylocking.lockfile.ModuleIdentifier
import build.wallet.gradle.dependencylocking.service.ArtifactHashProvider
import build.wallet.gradle.dependencylocking.util.externalModuleOwner
import org.gradle.api.artifacts.result.ResolvedArtifactResult

internal class LockableVariantFactory(
  private val artifactHashProvider: ArtifactHashProvider,
) {
  fun create(resolvedArtifacts: List<ResolvedArtifactResult>): LockableVariant =
    LockableVariant(
      moduleIdentifier = resolvedArtifacts.moduleIdentifier,
      version = resolvedArtifacts.version,
      artifacts = resolvedArtifacts.artifacts
    )

  private val List<ResolvedArtifactResult>.moduleIdentifier: ModuleIdentifier
    get() =
      map { it.externalModuleOwner.moduleIdentifier }
        .distinct()
        .singleOrNull()
        ?.let(::ModuleIdentifier)
        ?: error("All resolved components must have the same module identifier. Was: $this")

  private val List<ResolvedArtifactResult>.version: ComponentVersion
    get() =
      map { it.externalModuleOwner.version }
        .distinct()
        .singleOrNull()
        ?.let(::ComponentVersion)
        ?: error("All resolved artifacts must have the same component version. Was: $this")

  private val List<ResolvedArtifactResult>.artifacts: List<LockFile.Artifact>
    get() =
      map {
        LockFile.Artifact(
          name = ArtifactName(it.file.name),
          hash = artifactHashProvider.hash(it.file)
        )
        // Needed because configurations can fetch multiple identical artifacts by depending on different variants that all provide the same artifact
      }.distinct()
}
