package build.wallet.gradle.dependencylocking.lockfile

@JvmInline
internal value class ArtifactHash(val value: String) {
  init {
    require(value.isNotBlank()) {
      "Artifact hash must not be blank."
    }
  }

  override fun toString(): String = value
}
