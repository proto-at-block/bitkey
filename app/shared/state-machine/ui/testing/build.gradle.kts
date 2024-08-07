import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.compose.runtime)
  alias(libs.plugins.compose.compiler)
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(compose.runtime)
        implementation(projects.shared.composeRuntimePublic)
        implementation(projects.shared.stateMachineFrameworkTesting)
        implementation(projects.shared.stateMachineUiPublic)
      }
    }
  }
}
