import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.libs.resultPublic)
        api(libs.kmp.settings)
        api(libs.kmp.settings.coroutines)
      }
    }
  }
}
