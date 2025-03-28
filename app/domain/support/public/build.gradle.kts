import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(libs.kmp.kotlin.datetime)
        api(libs.kmp.kotlin.result)
        api(projects.domain.bitkeyPrimitivesPublic)
        api(projects.libs.contactMethodPublic)
        api(projects.libs.platformPublic)
      }
    }
  }
}
