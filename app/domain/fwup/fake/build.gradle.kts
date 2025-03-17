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
        api(projects.domain.firmwarePublic)
        api(libs.kmp.okio)
        implementation(projects.domain.firmwareFake)
      }
    }
  }
}
