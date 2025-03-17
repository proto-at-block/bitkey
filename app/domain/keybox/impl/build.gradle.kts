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
        api(projects.libs.bitcoinPrimitivesPublic)
        api(projects.domain.bitkeyPrimitivesPublic)
        api(projects.domain.databasePublic)
        api(projects.libs.keyValueStorePublic)
        api(projects.libs.moneyPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.libs.bitcoinPrimitivesFake)
        implementation(projects.domain.bitkeyPrimitivesFake)
        implementation(projects.libs.sqldelightTesting)
        implementation(projects.libs.testingPublic)
      }
    }

    commonIntegrationTest {
      dependencies {
        implementation(projects.domain.bitkeyPrimitivesFake)
        implementation(projects.libs.moneyTesting)
        implementation(projects.shared.integrationTestingPublic)
      }
    }
  }
}
