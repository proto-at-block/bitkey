import build.wallet.gradle.logic.extensions.allTargets
import build.wallet.gradle.logic.extensions.commonIntegrationTest
import build.wallet.gradle.logic.extensions.invoke

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.bitcoinPrimitivesPublic)
        api(projects.shared.bitkeyPrimitivesPublic)
        api(projects.shared.databasePublic)
        api(projects.shared.keyValueStorePublic)
        api(projects.shared.moneyPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.bitcoinPrimitivesFake)
        implementation(projects.shared.bitkeyPrimitivesFake)
        implementation(projects.shared.sqldelightTesting)
        implementation(projects.shared.testingPublic)
      }
    }

    commonIntegrationTest {
      dependencies {
        implementation(projects.shared.bitkeyPrimitivesFake)
        implementation(projects.shared.moneyTesting)
        implementation(projects.shared.integrationTestingPublic)
      }
    }
  }
}
