package build.wallet.gradle.dependencylocking.service

import build.wallet.gradle.dependencylocking.lockfile.LockFile
import build.wallet.gradle.dependencylocking.lockfile.deserialize
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.kotlin.dsl.provideDelegate

internal abstract class LockFileProvider : BuildService<LockFileProvider.Parameters> {
  interface Parameters : BuildServiceParameters {
    val lockFileDirectory: DirectoryProperty
  }

  val lockFile: LockFile by lazy {
    val lockFileDirectory = parameters.lockFileDirectory.get().asFile

    if (lockFileDirectory.exists()) {
      LockFile.deserialize(lockFileDirectory)
    } else {
      LockFile()
    }
  }

  companion object {
    const val KEY: String = "lockFileProvider"
  }
}
