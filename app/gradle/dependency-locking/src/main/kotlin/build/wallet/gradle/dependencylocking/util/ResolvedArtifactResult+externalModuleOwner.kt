package build.wallet.gradle.dependencylocking.util

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult

internal val ResolvedArtifactResult.externalModuleOwner: ModuleComponentIdentifier
  get() = variant.owner as? ModuleComponentIdentifier
    ?: error("Resolved artifact '$this' is not an external module.")

internal val ResolvedArtifactResult.isFromExternalModule: Boolean
  get() = variant.owner is ModuleComponentIdentifier
