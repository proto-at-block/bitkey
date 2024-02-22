import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.sqldelightFake)
        api(libs.bundles.kmp.test.kotest)
        implementation(libs.kmp.picnic)
      }
    }
  }
}
