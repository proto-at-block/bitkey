import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
}

kotlin {
  allTargets()
  sourceSets {
    commonMain {
      dependencies {
        implementation(projects.domain.privilegedActionsPublic)
        implementation(projects.domain.f8eClientPublic)
        implementation(projects.domain.walletPublic)
        implementation(projects.domain.hardwarePublic)
        implementation(projects.domain.featureFlagPublic)
        implementation(projects.domain.databasePublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.domain.f8eClientFake)
        implementation(projects.libs.testingPublic)
        implementation(projects.domain.accountFake)
        implementation(projects.domain.privilegedActionsFake)
        implementation(projects.libs.encryptionFake)
        implementation(projects.domain.hardwareFake)
        implementation(projects.domain.featureFlagFake)
        implementation(projects.libs.sqldelightFake)
        implementation(projects.libs.sqldelightTesting)
        implementation(projects.libs.grantsPublic)
        implementation(projects.libs.grantsFake)
        implementation(projects.domain.authFake)
      }
    }
  }
}
