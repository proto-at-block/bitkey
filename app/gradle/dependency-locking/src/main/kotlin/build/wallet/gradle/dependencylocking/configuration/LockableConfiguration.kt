package build.wallet.gradle.dependencylocking.configuration

import build.wallet.gradle.dependencylocking.lockfile.LockFile

internal data class LockableConfiguration(
  val id: Id,
  val group: DependencyLockingGroup,
  val lockableVariants: List<LockableVariant>,
) {
  fun createLockFile(): LockFile =
    LockFile(
      lockedModules = lockableVariants.map { it.toLockedModule() }
    )

  private fun LockableVariant.toLockedModule(): LockFile.LockedModule =
    LockFile.LockedModule(
      moduleIdentifier = moduleIdentifier,
      components = listOf(
        LockFile.Component(
          coordinates = coordinates,
          variants = listOf(
            LockFile.Variant(artifacts = artifacts)
          ),
          dependencyLockingGroups = listOf(group)
        )
      )
    )

  enum class Origin {
    Build,
    BuildScript,
  }

  data class Id(val origin: Origin, val name: String) {
    override fun toString(): String = origin.name + ":" + name
  }
}
