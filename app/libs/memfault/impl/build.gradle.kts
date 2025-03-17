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
        api(projects.domain.availabilityPublic)
        api(projects.libs.ktorClientPublic)
        implementation(projects.domain.f8eClientPublic)
        implementation(projects.libs.loggingPublic)
      }
    }
  }
}
