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
        api(projects.domain.accountPublic)
        api(projects.domain.databasePublic)
        api(projects.libs.memfaultPublic)
        api(projects.libs.platformPublic)
        api(projects.libs.loggingPublic)
        api(projects.libs.stdlibPublic)
        implementation(projects.domain.firmwarePublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.domain.accountFake)
        implementation(projects.domain.firmwareFake)
        implementation(projects.domain.fwupFake)
        implementation(projects.domain.keyboxFake)
        implementation(projects.libs.platformFake)
        implementation(projects.libs.sqldelightTesting)
        implementation(projects.libs.testingPublic)
      }
    }
  }
}
