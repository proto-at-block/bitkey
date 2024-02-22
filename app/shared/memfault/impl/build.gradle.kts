import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.availabilityPublic)
        api(projects.shared.ktorClientPublic)
        implementation(projects.shared.loggingPublic)
      }
    }
  }
}
