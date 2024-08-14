import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        implementation(libs.kmp.kotlin.datetime)
        implementation(projects.shared.emailFake)
        implementation(projects.shared.phoneNumberFake)
      }
    }
  }
}
