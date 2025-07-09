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
        api(libs.kmp.kotlin.datetime)
        api(projects.libs.datadogPublic)
        api(projects.domain.availabilityPublic)
        api(projects.domain.authPublic)
        api(projects.libs.encryptionPublic)
        api(projects.domain.accountPublic)
        api(projects.libs.bdkBindingsPublic)
        api(projects.domain.cloudBackupPublic)
        api(projects.domain.debugPublic)
        api(projects.libs.keyValueStorePublic)
        api(projects.libs.ktorClientPublic)
        api(projects.libs.moneyPublic)
        api(projects.domain.notificationsPublic)
        api(projects.libs.platformPublic)
        api(projects.libs.queueProcessorPublic)
        api(projects.domain.databasePublic)
        api(projects.shared.balanceUtilsImpl)
        implementation(projects.libs.loggingPublic)
        implementation(libs.kmp.okio)
        implementation(libs.kmp.settings)
      }
    }
    commonTest {
      dependencies {
        implementation(projects.libs.datadogFake)
        implementation(projects.domain.accountFake)
        implementation(projects.domain.analyticsFake)
        implementation(projects.domain.availabilityFake)
        implementation(projects.libs.bdkBindingsFake)
        implementation(projects.libs.bitcoinPrimitivesFake)
        implementation(projects.domain.bitkeyPrimitivesFake)
        implementation(projects.domain.debugFake)
        implementation(projects.domain.f8eClientFake)
        implementation(projects.domain.featureFlagFake)
        implementation(projects.domain.walletFake)
        implementation(projects.domain.walletTesting)
        implementation(projects.libs.keyValueStoreFake)
        implementation(projects.libs.moneyFake)
        implementation(projects.domain.partnershipsFake)
        implementation(projects.libs.platformFake)
        implementation(projects.libs.queueProcessorFake)
        implementation(projects.libs.sqldelightTesting)
        implementation(projects.libs.testingPublic)
        implementation(projects.libs.timeFake)
        implementation(libs.kmp.test.ktor.client.mock)
      }
    }

    val commonJvmMain by getting {
      dependencies {
        implementation(projects.rust.coreFfi)
      }
    }

    val jvmIntegrationTest by getting {
      dependencies {
        implementation(libs.kmp.aws.secretsmanager)
        implementation(projects.shared.integrationTestingPublic)
        implementation(projects.domain.bitkeyPrimitivesFake)
        implementation(projects.libs.moneyTesting)
        implementation(projects.domain.walletFake)
      }
    }
  }
}
