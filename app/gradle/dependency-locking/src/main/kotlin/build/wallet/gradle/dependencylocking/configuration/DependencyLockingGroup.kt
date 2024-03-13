package build.wallet.gradle.dependencylocking.configuration

internal sealed class DependencyLockingGroup {
  abstract val name: String

  override fun toString(): String = name

  object Unknown : DependencyLockingGroup() {
    override val name: String = "<unknown>"
  }

  data class Known(
    override val name: String,
  ) : DependencyLockingGroup() {
    override fun toString(): String = super.toString()
  }
}
