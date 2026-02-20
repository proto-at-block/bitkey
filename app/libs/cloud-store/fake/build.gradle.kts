import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        implementation(libs.kmp.okio)
        implementation(projects.libs.testingPublic)
      }
    }

    val iosMain by getting {
      dependencies {
        implementation(projects.libs.cloudStorePublic)
      }
    }
  }
}
