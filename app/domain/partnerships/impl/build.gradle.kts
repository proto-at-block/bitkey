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
        implementation(projects.domain.databasePublic)
        implementation(projects.libs.stdlibPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.domain.accountFake)
        implementation(projects.domain.f8eClientFake)
        implementation(projects.domain.featureFlagFake)
        implementation(projects.domain.partnershipsFake)
        implementation(projects.libs.platformFake)
        implementation(projects.libs.timeFake)
        implementation(projects.libs.testingPublic)
      }
    }

    val commonIntegrationTest by getting {
      dependencies {
        implementation(projects.domain.accountFake)
        implementation(projects.domain.bitkeyPrimitivesFake)
        implementation(projects.domain.f8eClientFake)
        implementation(projects.shared.integrationTestingPublic)
        implementation(projects.libs.platformFake)
        implementation(projects.domain.recoveryFake)
        implementation(projects.libs.sqldelightTesting)
        implementation(projects.libs.testingPublic)
      }
    }
  }
}
