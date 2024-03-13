package build.wallet.gradle.dependencylocking.exception

import build.wallet.gradle.dependencylocking.lockfile.LockFileUnificationResult

internal class LockFileUnificationException(
  error: LockFileUnificationResult.Error,
  lhsLockFileDescription: String,
  rhsLockFileDescription: String,
) : DependencyLockingException(
    "Cannot unify lock files of '$lhsLockFileDescription' and '$rhsLockFileDescription'. Reasons:\n" +
      error.reasons.joinToString("\n") { reason ->
        when (reason) {
          is LockFileUnificationResult.VersionConflict -> {
            "Version conflict of '${reason.moduleIdentifier}' for dependency locking group '${reason.conflictingDependencyLockingGroup}': '${reason.lhsVersion}' vs. '${reason.rhsVersion}'"
          }
        }
      }
  )
