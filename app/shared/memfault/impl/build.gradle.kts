import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.availabilityPublic)
        api(projects.shared.ktorClientPublic)
        implementation(projects.shared.f8eClientPublic)
        implementation(projects.shared.loggingPublic)
      }
    }
  }
}
