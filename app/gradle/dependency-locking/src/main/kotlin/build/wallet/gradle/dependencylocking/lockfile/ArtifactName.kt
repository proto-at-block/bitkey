package build.wallet.gradle.dependencylocking.lockfile

@JvmInline
internal value class ArtifactName(val value: String) {
  init {
    require(value.isNotBlank()) {
      "Artifact name must not be blank."
    }
  }

  override fun toString(): String = value
}
