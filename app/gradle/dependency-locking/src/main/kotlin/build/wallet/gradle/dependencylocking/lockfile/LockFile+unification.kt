package build.wallet.gradle.dependencylocking.lockfile

internal fun LockFile.unifyWith(other: LockFile): LockFileUnificationResult<LockFile> =
  unifyLockedModulesWith(other).map { lockedModules ->
    LockFile(
      lockedModules = lockedModules
    )
  }

private fun LockFile.unifyLockedModulesWith(
  other: LockFile,
): LockFileUnificationResult<List<LockFile.LockedModule>> =
  (this.lockedModulesByModuleIdentifier.keys union other.lockedModulesByModuleIdentifier.keys)
    .map { this.lockedModulesByModuleIdentifier[it] to other.lockedModulesByModuleIdentifier[it] }
    .mapNotNull { (thisLockedModule, otherLockedModule) ->
      thisLockedModule.unifyWithOptional(otherLockedModule, LockFile.LockedModule::unifyWith)
    }
    .mergeResults()

private fun LockFile.LockedModule.unifyWith(
  other: LockFile.LockedModule,
): LockFileUnificationResult<LockFile.LockedModule> {
  require(moduleIdentifier == other.moduleIdentifier) {
    "Cannot merge two entries with different module names: $moduleIdentifier and ${other.moduleIdentifier}"
  }

  return mergeResults(
    checkVersionConflicts(other),
    unifyComponentsWith(other)
  ).map { LockFile.LockedModule(moduleIdentifier, it) }
}

private fun LockFile.LockedModule.unifyComponentsWith(
  other: LockFile.LockedModule,
): LockFileUnificationResult<List<LockFile.Component>> =
  (this.componentsByVersion.keys union other.componentsByVersion.keys)
    .map { this.componentsByVersion[it] to other.componentsByVersion[it] }
    .mapNotNull { (thisComponent, otherComponent) ->
      thisComponent.unifyWithOptional(otherComponent, LockFile.Component::unifyWith)
    }
    .mergeResults()

private fun LockFile.Component.unifyWith(
  other: LockFile.Component,
): LockFileUnificationResult<LockFile.Component> {
  require(this.coordinates == other.coordinates) {
    "Cannot merge components with two different versions: ${this.coordinates} and ${other.coordinates}"
  }

  return (this.variantsById.keys union other.variantsById.keys)
    .map { this.variantsById[it] to other.variantsById[it] }
    .mapNotNull { (thisVariant, otherVariant) ->
      thisVariant.unifyWithOptional(otherVariant, LockFile.Variant::unifyWith)
    }
    .mergeResults()
    .map {
      LockFile.Component(
        coordinates = coordinates,
        variants = it,
        dependencyLockingGroups = (dependencyLockingGroups + other.dependencyLockingGroups).distinct()
      )
    }
}

private fun LockFile.Variant.unifyWith(
  other: LockFile.Variant,
): LockFileUnificationResult<LockFile.Variant> {
  require(this.id == other.id) {
    "Cannot merge variants with different content: ${this.artifacts} and ${other.artifacts}"
  }

  return this.asUnificationSuccess()
}

private fun <T> T?.unifyWithOptional(
  other: T?,
  unification: (T, T) -> LockFileUnificationResult<T>,
): LockFileUnificationResult<T>? =
  when {
    this != null && other != null -> unification(this, other)
    this != null && other == null -> this.asUnificationSuccess()
    this == null && other != null -> other.asUnificationSuccess()
    else -> null
  }

private fun LockFile.LockedModule.checkVersionConflicts(
  other: LockFile.LockedModule,
): LockFileUnificationResult.Error? =
  (this.componentsByDependencyLockingGroup.keys intersect other.componentsByDependencyLockingGroup.keys)
    .mapNotNull { group ->
      val thisComponent = this.componentsByDependencyLockingGroup.getValue(group)
      val otherComponent = other.componentsByDependencyLockingGroup.getValue(group)

      if (thisComponent.coordinates.componentVersion != otherComponent.coordinates.componentVersion) {
        LockFileUnificationResult.VersionConflict(
          moduleIdentifier = moduleIdentifier,
          lhsVersion = thisComponent.coordinates.componentVersion,
          rhsVersion = otherComponent.coordinates.componentVersion,
          conflictingDependencyLockingGroup = group
        ).asUnificationError()
      } else {
        null
      }
    }
    .mergeErrors()
