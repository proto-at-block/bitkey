
import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
}

kotlin {
  allTargets()

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(projects.domain.recoveryPublic)
        implementation(projects.libs.stdlibPublic)
        implementation(projects.domain.databasePublic)
        implementation(projects.domain.relationshipsPublic)
        implementation(projects.domain.walletPublic)
        implementation(projects.libs.keyValueStorePublic)
      }
    }

    val commonTest by getting {
      dependencies {
        implementation(projects.domain.accountFake)
        implementation(projects.domain.databasePublic)
        implementation(projects.domain.f8eClientFake)
        implementation(projects.domain.f8eClientImpl)
        implementation(projects.domain.featureFlagFake)
        implementation(projects.domain.inheritanceFake)
        implementation(projects.libs.sqldelightTesting)
        implementation(projects.domain.databasePublic)
        implementation(projects.libs.platformFake)
        implementation(projects.domain.relationshipsFake)
        implementation(projects.domain.relationshipsImpl)
        implementation(projects.domain.walletFake)
        implementation(projects.libs.testingPublic)
        implementation(projects.libs.keyValueStoreFake)
        implementation(projects.domain.onboardingImpl)
      }
    }
  }
}
