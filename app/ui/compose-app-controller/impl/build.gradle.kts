import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
  alias(libs.plugins.compose.runtime)
  alias(libs.plugins.compose.compiler)
}

kotlin {
  allTargets()

  sourceSets {
    iosMain {
      dependencies {
        api(projects.ui.featuresPublic)
        api(projects.ui.frameworkPublic)
      }
    }
  }
}
