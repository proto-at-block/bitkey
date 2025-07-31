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
        api(projects.domain.privilegedActionsPublic)
        api(projects.domain.f8eClientPublic)
        api(projects.domain.walletPublic)
        api(projects.domain.hardwarePublic)
        api(projects.domain.featureFlagPublic)
        api(projects.domain.databasePublic)
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
      }
    }
  }
}
