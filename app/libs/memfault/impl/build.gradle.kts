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
        implementation(projects.domain.availabilityPublic)
        implementation(projects.libs.ktorClientPublic)
        implementation(projects.domain.f8eClientPublic)
        implementation(projects.libs.loggingPublic)
      }
    }
  }
}
