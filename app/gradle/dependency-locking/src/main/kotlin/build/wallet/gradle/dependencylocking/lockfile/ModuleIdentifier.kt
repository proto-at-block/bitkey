package build.wallet.gradle.dependencylocking.lockfile

import org.gradle.api.artifacts.ModuleIdentifier as GradleModuleIdentifier

internal data class ModuleIdentifier(val group: String, val name: String) {
  val fullIdentifier: String = "$group:$name"

  init {
    require(group.isNotBlank()) { "Group cannot be blank!" }
    require(name.isNotBlank()) { "Module name cannot be blank!" }
  }

  constructor(moduleIdentifier: GradleModuleIdentifier) : this(
    moduleIdentifier.group,
    moduleIdentifier.name
  )

  override fun toString(): String = fullIdentifier

  companion object {
    operator fun invoke(fullIdentifier: String): ModuleIdentifier {
      val split = fullIdentifier.split(":")

      require(split.size == 2) {
        "'$fullIdentifier' does not have the expected format of 'group:name'"
      }

      return ModuleIdentifier(split[0], split[1])
    }
  }
}
