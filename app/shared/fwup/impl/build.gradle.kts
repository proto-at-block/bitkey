import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.accountPublic)
        api(projects.shared.databasePublic)
        api(projects.shared.memfaultPublic)
        api(projects.shared.platformPublic)
        api(projects.shared.loggingPublic)
        implementation(libs.kmp.kotlin.serialization.core)
        implementation(libs.kmp.kotlin.serialization.json)
        implementation(projects.shared.firmwarePublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.fwupFake)
        implementation(projects.shared.sqldelightTesting)
        implementation(projects.shared.testingPublic)
      }
    }
  }
}
