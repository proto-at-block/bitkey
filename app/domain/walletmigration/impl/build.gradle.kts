import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
  id("build.wallet.redacted")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        implementation(projects.domain.walletmigrationPublic)
        implementation(projects.domain.accountPublic)
        implementation(projects.domain.recoveryPublic)
        implementation(projects.domain.f8eClientPublic)
        implementation(projects.libs.loggingPublic)
        implementation(projects.libs.stdlibPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.domain.accountFake)
        implementation(projects.domain.walletmigrationFake)
        implementation(projects.libs.testingPublic)
        implementation(projects.libs.timeFake)
        implementation(projects.libs.platformFake)
        implementation(projects.domain.f8eClientFake)
        implementation(libs.kmp.test.kotest.assertions)
        implementation(projects.domain.walletFake)
      }
    }
  }
}
