package build.wallet.gradle.dependencylocking.lockfile

import build.wallet.gradle.dependencylocking.configuration.DependencyLockingGroup

internal sealed interface LockFileUnificationResult<out T> {
  fun <R> map(mapSuccess: (T) -> R): LockFileUnificationResult<R>

  data class Success<T>(val value: T) : LockFileUnificationResult<T> {
    override fun <R> map(mapSuccess: (T) -> R): LockFileUnificationResult<R> =
      Success(mapSuccess(value))
  }

  data class Error(val reasons: List<Reason>) : LockFileUnificationResult<Nothing> {
    override fun <R> map(mapSuccess: (Nothing) -> R): LockFileUnificationResult<R> = this

    sealed interface Reason
  }

  data class VersionConflict(
    val moduleIdentifier: ModuleIdentifier,
    val lhsVersion: ComponentVersion,
    val rhsVersion: ComponentVersion,
    val conflictingDependencyLockingGroup: DependencyLockingGroup,
  ) : Error.Reason
}

internal fun <T> mergeResults(
  vararg result: LockFileUnificationResult<List<T>>?,
): LockFileUnificationResult<List<T>> = result.toList().mergeResults().map { it.flatten() }

internal fun <T> Collection<LockFileUnificationResult<T>?>.mergeResults(): LockFileUnificationResult<List<T>> {
  val successes = filterIsInstance<LockFileUnificationResult.Success<T>>()
  val errors = filterIsInstance<LockFileUnificationResult.Error>()

  return if (errors.isNotEmpty()) {
    errors.flatten()
  } else {
    successes.map { it.value }.asUnificationSuccess()
  }
}

internal fun Collection<LockFileUnificationResult.Error?>.mergeErrors(): LockFileUnificationResult.Error? =
  filterNotNull().takeIf { it.isNotEmpty() }?.flatten()

private fun List<LockFileUnificationResult.Error>.flatten(): LockFileUnificationResult.Error =
  LockFileUnificationResult.Error(
    reasons = flatMap { it.reasons }
  )

internal fun <T> T.asUnificationSuccess(): LockFileUnificationResult<T> =
  LockFileUnificationResult.Success(this)

internal fun <T : LockFileUnificationResult.Error.Reason> T.asUnificationError(): LockFileUnificationResult.Error =
  LockFileUnificationResult.Error(listOf(this))
