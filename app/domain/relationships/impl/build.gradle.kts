
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
        implementation(projects.domain.accountPublic)
        implementation(projects.domain.databasePublic)
        implementation(projects.domain.debugPublic)
        implementation(projects.domain.f8eClientPublic)
        implementation(projects.domain.walletPublic)
        implementation(projects.libs.stdlibPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.domain.accountFake)
        implementation(projects.domain.debugFake)
        implementation(projects.domain.featureFlagFake)
        implementation(projects.domain.f8eClientFake)
        implementation(projects.domain.f8eClientImpl)
        implementation(projects.libs.ktorClientFake)
        implementation(projects.libs.platformFake)
        implementation(projects.libs.testingPublic)
        implementation(projects.libs.sqldelightTesting)
        implementation(projects.domain.f8eClientImpl)
        implementation(projects.libs.encryptionFake)
        implementation(projects.domain.analyticsFake)
        implementation(projects.domain.relationshipsFake)
        implementation(projects.domain.walletFake)
      }
    }

    jvmTest {
      dependencies {
        implementation(projects.rust.coreFfi)
        implementation(projects.domain.authImpl)
        implementation(projects.libs.encryptionFake)
        implementation(projects.libs.encryptionImpl)
      }
    }

    val jvmIntegrationTest by getting {
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
