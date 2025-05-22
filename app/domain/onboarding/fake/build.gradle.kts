import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.domain.bitkeyPrimitivesFake)
        api(projects.libs.testingPublic)
      }
    }
  }
}
