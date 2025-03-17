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
        implementation(projects.libs.composeRuntimePublic)
        implementation(projects.libs.stateMachineTesting)
        implementation(projects.ui.featuresPublic)
        implementation(projects.libs.testingPublic)
      }
    }
  }
}
