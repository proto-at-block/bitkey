import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.dbResultPublic)
        api(projects.shared.timePublic)
        api(libs.kmp.okio)
        api(projects.shared.memfaultPublic)
      }
    }
  }
}
