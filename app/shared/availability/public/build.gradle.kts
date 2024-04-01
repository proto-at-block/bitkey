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
        api(libs.kmp.ktor.client.core)
        api(projects.shared.dbResultPublic)
        api(projects.shared.f8ePublic)
        api(projects.shared.ktorClientPublic)
      }
    }
  }
}
