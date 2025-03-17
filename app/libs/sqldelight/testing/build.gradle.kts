import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.libs.sqldelightFake)
        api(libs.bundles.kmp.test.kotest)
        implementation(libs.kmp.picnic)
      }
    }
  }
}
