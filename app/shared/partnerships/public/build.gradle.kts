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
        implementation(libs.kmp.kotlin.serialization.core)
        api(libs.kmp.kotlin.datetime)
        api(projects.shared.moneyPublic)
      }
    }
  }
}
