import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.accountPublic)
        api(projects.shared.debugPublic)
        api(projects.shared.databasePublic)
        api(projects.shared.memfaultPublic)
        api(projects.shared.platformPublic)
        api(projects.shared.loggingPublic)
        api(projects.shared.serializationPublic)
        implementation(libs.kmp.kotlin.serialization.core)
        implementation(libs.kmp.kotlin.serialization.json)
        implementation(projects.shared.firmwarePublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.debugFake)
        implementation(projects.shared.firmwareFake)
        implementation(projects.shared.fwupFake)
        implementation(projects.shared.keyboxFake)
        implementation(projects.shared.platformFake)
        implementation(projects.shared.sqldelightTesting)
        implementation(projects.shared.testingPublic)
      }
    }
  }
}
