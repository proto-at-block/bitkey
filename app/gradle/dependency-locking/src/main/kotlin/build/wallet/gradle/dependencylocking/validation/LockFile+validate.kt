package build.wallet.gradle.dependencylocking.validation

import build.wallet.gradle.dependencylocking.configuration.DependencyLockingConfig
import build.wallet.gradle.dependencylocking.configuration.LockableConfiguration
import build.wallet.gradle.dependencylocking.configuration.LockableVariant
import build.wallet.gradle.dependencylocking.exception.DependencyContentChangedException
import build.wallet.gradle.dependencylocking.exception.DependencyNotLockedException
import build.wallet.gradle.dependencylocking.exception.DependencyVersionChangedException
import build.wallet.gradle.dependencylocking.exception.UnexpectedDependencyException
import build.wallet.gradle.dependencylocking.lockfile.ArtifactName
import build.wallet.gradle.dependencylocking.lockfile.LockFile

internal fun LockFile.validate(
  lockableConfiguration: LockableConfiguration,
  config: DependencyLockingConfig,
) {
  lockableConfiguration.lockableVariants.forEach {
    validate(lockableConfiguration, it, config)
  }
}

private fun LockFile.validate(
  lockableConfiguration: LockableConfiguration,
  lockableVariant: LockableVariant,
  config: DependencyLockingConfig,
) {
  if (config.isModuleIgnored(lockableVariant.moduleIdentifier)) {
    return
  }

  val lockedModule = lockedModulesByModuleIdentifier[lockableVariant.moduleIdentifier]

  if (lockedModule != null) {
    lockedModule.validate(lockableConfiguration, lockableVariant, config)
  } else {
    throw DependencyNotLockedException(lockableVariant, lockableConfiguration, config.projectPath)
  }
}

private fun LockFile.LockedModule.validate(
  lockableConfiguration: LockableConfiguration,
  lockableVariant: LockableVariant,
  config: DependencyLockingConfig,
) {
  val lockFileComponent = componentsByDependencyLockingGroup[lockableConfiguration.group]
    ?: throw UnexpectedDependencyException(
      moduleIdentifier = lockableVariant.moduleIdentifier,
      lockableConfiguration = lockableConfiguration,
      projectPath = config.projectPath
    )

  lockFileComponent.validate(lockableConfiguration, lockableVariant, config)
}

private fun LockFile.Component.validate(
  lockableConfiguration: LockableConfiguration,
  lockableVariant: LockableVariant,
  config: DependencyLockingConfig,
) {
  checkVersion(lockableConfiguration, lockableVariant, config)

  checkVariant(lockableConfiguration, lockableVariant, config)
}

private fun LockFile.Component.checkVersion(
  lockableConfiguration: LockableConfiguration,
  lockableVariant: LockableVariant,
  config: DependencyLockingConfig,
) {
  if (lockableVariant.version != this.coordinates.componentVersion) {
    throw DependencyVersionChangedException(
      lockableVariant = lockableVariant,
      lockFileComponent = this,
      lockableConfiguration = lockableConfiguration,
      projectPath = config.projectPath
    )
  }
}

private fun LockFile.Component.checkVariant(
  lockableConfiguration: LockableConfiguration,
  lockableVariant: LockableVariant,
  config: DependencyLockingConfig,
) {
  val hasMatchingVariant = variants.any { it.artifactsMatch(lockableVariant) }

  if (!hasMatchingVariant) {
    throw DependencyContentChangedException(
      lockableVariant = lockableVariant,
      lockFileComponent = this,
      lockableConfiguration = lockableConfiguration,
      projectPath = config.projectPath
    )
  }
}

private fun LockFile.Variant.artifactsMatch(lockableVariant: LockableVariant): Boolean =
  (this.artifactsByName.keys union lockableVariant.artifactsByName.keys).all { artifactName ->
    artifactMatches(lockableVariant, artifactName)
  }

private fun LockFile.Variant.artifactMatches(
  lockableVariant: LockableVariant,
  artifactName: ArtifactName,
): Boolean {
  val lockedArtifact = this.artifactsByName[artifactName]
  val resolvedArtifact = lockableVariant.artifactsByName[artifactName]

  return when {
    lockedArtifact != null && resolvedArtifact != null -> lockedArtifact.hash == resolvedArtifact.hash
    else -> false
  }
}
