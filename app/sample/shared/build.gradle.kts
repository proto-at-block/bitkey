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
        api(projects.libs.stateMachinePublic)
        api(projects.ui.frameworkPublic)

        implementation(projects.libs.platformPublic)
        implementation(projects.libs.timePublic)
        implementation(projects.ui.frameworkImpl)
        implementation(projects.libs.platformImpl)
      }
    }
  }
}
