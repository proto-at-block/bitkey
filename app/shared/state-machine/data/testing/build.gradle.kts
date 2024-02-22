import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.compose.runtime)
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(compose.runtime)
        api(projects.shared.stateMachineDataPublic)
        api(projects.shared.stateMachineUiPublic)
        implementation(projects.shared.composeRuntimePublic)
        implementation(projects.shared.stateMachineFrameworkTesting)
      }
    }
  }
}
