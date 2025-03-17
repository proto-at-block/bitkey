import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("com.google.devtools.ksp")
  alias(libs.plugins.compose.runtime)
  alias(libs.plugins.compose.compiler)
}

kotlin {
  allTargets()

  sourceSets {
    all {
      languageSettings.optIn("kotlin.experimental.ExperimentalObjCName")
    }

    commonMain {
      dependencies {
        api(compose.runtime)
      }
    }
  }
}
