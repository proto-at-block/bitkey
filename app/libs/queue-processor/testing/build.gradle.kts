import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonTest {
      dependencies {
        implementation(projects.libs.testingPublic)
      }
    }
  }
}
