package build.wallet.gradle.dependencylocking.task

import build.wallet.gradle.dependencylocking.lockfile.LockFile
import build.wallet.gradle.dependencylocking.lockfile.LockFileUnificationResult
import build.wallet.gradle.dependencylocking.lockfile.deserialize
import build.wallet.gradle.dependencylocking.lockfile.unifyWith
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

internal abstract class DebugGlobalDependencyLockingCollisionsTask : DefaultTask() {
  @get:InputFiles
  abstract val localLockFileSegments: Property<FileCollection>

  @get:Internal
  abstract val rootProjectDirectory: DirectoryProperty

  init {
    description =
      "Prints information about conflicts that prevent the creation of a unified global lock file."
    group = "dependency-lock"

    this.notCompatibleWithConfigurationCache("Uses configurations at execution time.")
    this.doNotTrackState("Not worth caching.")
  }

  @TaskAction
  fun runTask() {
    val lockFileGroups = createLockFileGroups()

    printLockFileGroups(lockFileGroups)

    printLockFileGroupsDifferences(lockFileGroups)
  }

  private fun createLockFileGroups(): List<LockFileGroup> {
    val lockFileGroups = mutableListOf<LockFileGroup>()

    localLockFileSegments.get().files
      .map {
        LockFileWithOrigin(
          lockFile = LockFile(listOf(LockFile.LockedModule.deserialize(it))),
          origin = it.toRelativeString(rootProjectDirectory.get().asFile)
        )
      }
      .forEach {
        lockFileGroups.addNextLockFile(it)
      }

    return lockFileGroups
  }

  private fun MutableList<LockFileGroup>.addNextLockFile(lockFileWithOrigin: LockFileWithOrigin) {
    forEach {
      val wasAdded = it.tryAddLockFile(lockFileWithOrigin)
      if (wasAdded) {
        return
      }
    }

    add(
      LockFileGroup(size, lockFileWithOrigin)
    )
  }

  private fun printLockFileGroups(lockFileGroups: List<LockFileGroup>) {
    lockFileGroups.forEach {
      println("Group: ${it.index}:")
      printLockFileGroup(it)
    }
  }

  private fun printLockFileGroup(lockFileGroup: LockFileGroup) {
    lockFileGroup.containedLockFilesWithOrigin
      .sortedBy { it.origin }
      .forEach {
        println("     ${it.origin}")
      }

    println()
    println()
  }

  private fun printLockFileGroupsDifferences(lockFileGroups: List<LockFileGroup>) {
    if (lockFileGroups.size <= 1) {
      println("No differences!")

      return
    }

    println("Differences:")
    println()

    lockFileGroups.forEachIndexed { lhsIndex, lhsLockFileGroup ->
      println("'${lhsLockFileGroup.index}':")

      lockFileGroups.forEachIndexed { rhsIndex, rhsLockFileGroup ->
        if (lhsIndex < rhsIndex) {
          printDifferences(lhsLockFileGroup, rhsLockFileGroup)
        }
      }

      println()
    }
  }

  private fun printDifferences(
    lhsLockFileGroup: LockFileGroup,
    rhsLockFileGroup: LockFileGroup,
  ) {
    print("    '${rhsLockFileGroup.index}': ")

    val result = lhsLockFileGroup.unifiedLockFile.unifyWith(rhsLockFileGroup.unifiedLockFile)

    when (result) {
      is LockFileUnificationResult.Success -> {
        println("Success")
      }
      is LockFileUnificationResult.Error -> {
        println(result)
      }
    }
  }

  private class LockFileGroup(
    val index: Int,
    initialLockFileWithOrigin: LockFileWithOrigin,
  ) {
    var unifiedLockFile: LockFile = LockFile()
      private set

    val containedLockFilesWithOrigin = mutableListOf<LockFileWithOrigin>()

    init {
      tryAddLockFile(initialLockFileWithOrigin)
    }

    fun tryAddLockFile(lockFileWithOrigin: LockFileWithOrigin): Boolean {
      when (val unificationResult = unifiedLockFile.unifyWith(lockFileWithOrigin.lockFile)) {
        is LockFileUnificationResult.Success -> {
          unifiedLockFile = unificationResult.value

          containedLockFilesWithOrigin.add(lockFileWithOrigin)

          return true
        }
        is LockFileUnificationResult.Error -> {
          return false
        }
      }
    }
  }

  private data class LockFileWithOrigin(
    val lockFile: LockFile,
    val origin: String,
  )
}
