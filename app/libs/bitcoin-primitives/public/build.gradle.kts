import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.redacted")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      /**
       * Note! This is a primitives module,
       * should contain absolute *minimum* amount of dependencies!
       */
      dependencies {
        api(libs.kmp.kotlin.datetime)
      }
    }
  }
}
