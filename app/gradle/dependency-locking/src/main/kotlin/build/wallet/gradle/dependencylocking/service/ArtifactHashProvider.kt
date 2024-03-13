package build.wallet.gradle.dependencylocking.service

import build.wallet.gradle.dependencylocking.lockfile.ArtifactHash
import build.wallet.gradle.dependencylocking.util.toReadableSha256
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal abstract class ArtifactHashProvider : BuildService<BuildServiceParameters.None> {
  private val hashCache = ConcurrentHashMap<File, ArtifactHash>()

  fun hash(artifact: File): ArtifactHash =
    hashCache.getOrPut(artifact) {
      val artifactContent = artifact.readBytes()

      val hashValue = artifactContent.toReadableSha256()

      ArtifactHash(hashValue)
    }

  companion object {
    const val KEY: String = "artifactHashProvider"
  }
}
