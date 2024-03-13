package build.wallet.gradle.logic.reproducible

import org.gradle.api.provider.Property

abstract class ReproducibleBuildVariablesExtension {
  abstract val variables: Property<ReproducibleBuildVariables>
}
