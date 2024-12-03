
import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(projects.shared.recoveryPublic)
        implementation(projects.shared.serializationPublic)
        implementation(projects.shared.databasePublic)
        implementation(projects.shared.relationshipsPublic)
        implementation(projects.shared.keyValueStorePublic)
      }
    }

    val commonTest by getting {
      dependencies {
        implementation(projects.shared.accountFake)
        implementation(projects.shared.bitcoinFake)
        implementation(projects.shared.f8eClientFake)
        implementation(projects.shared.f8eClientImpl)
        implementation(projects.shared.featureFlagFake)
        implementation(projects.shared.inheritanceFake)
        implementation(projects.shared.keyboxFake)
        implementation(projects.shared.sqldelightTesting)
        implementation(projects.shared.databasePublic)
        implementation(projects.shared.platformFake)
        implementation(projects.shared.relationshipsFake)
        implementation(projects.shared.relationshipsImpl)
        implementation(projects.shared.testingPublic)
        implementation(projects.shared.keyValueStoreFake)
      }
    }
  }
}
