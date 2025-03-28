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
        api(projects.libs.composeRuntimePublic)
        api(libs.kmp.test.turbine)

        implementation(projects.libs.stdlibPublic)
        implementation(libs.kmp.molecule.runtime)
        implementation(libs.kmp.test.kotest.assertions)
      }
    }
  }
}
