package build.wallet.gradle.dependencylocking.lockfile

@JvmInline
internal value class ComponentVersion(val value: String) {
  override fun toString(): String = value
}
