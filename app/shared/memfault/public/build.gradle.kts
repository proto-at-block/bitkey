import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(libs.kmp.okio)
        api(projects.shared.ktorClientPublic)
        api(projects.shared.loggingPublic)
        api(projects.shared.platformPublic)
      }
    }
  }
}
