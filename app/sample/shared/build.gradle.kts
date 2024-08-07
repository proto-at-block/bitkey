import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.compose.runtime)
  alias(libs.plugins.compose.compiler)
}

kotlin {
  targets(jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(compose.runtime)
        api(projects.shared.stateMachineFrameworkPublic)
        api(projects.shared.uiCorePublic)

        implementation(projects.shared.platformPublic)
        implementation(projects.shared.timePublic)
        implementation(projects.shared.uiCoreImpl)
      }
    }
  }
}
