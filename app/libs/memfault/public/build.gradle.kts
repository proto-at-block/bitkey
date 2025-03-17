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
        api(projects.libs.ktorClientPublic)
        api(projects.libs.loggingPublic)
        api(projects.libs.platformPublic)
      }
    }
  }
}
