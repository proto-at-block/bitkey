import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        implementation(libs.kmp.kotlin.serialization.json)
        implementation(libs.kmp.ktor.client.core)
      }
    }
  }
}
