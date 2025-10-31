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
        implementation(libs.kmp.kotlin.datetime)
        implementation(projects.libs.datadogPublic)
        implementation(projects.domain.availabilityPublic)
        implementation(projects.domain.authPublic)
        implementation(projects.domain.featureFlagPublic)
        implementation(projects.domain.recoveryPublic)
        implementation(projects.libs.encryptionPublic)
        implementation(projects.domain.accountPublic)
        implementation(projects.libs.bdkBindingsPublic)
        implementation(projects.domain.cloudBackupPublic)
        implementation(projects.domain.debugPublic)
        implementation(projects.libs.keyValueStorePublic)
        implementation(projects.libs.ktorClientPublic)
        implementation(projects.libs.moneyPublic)
        implementation(projects.domain.notificationsPublic)
        implementation(projects.libs.platformPublic)
        implementation(projects.libs.queueProcessorPublic)
        implementation(projects.domain.databasePublic)
        implementation(projects.shared.balanceUtilsImpl)
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
        implementation(projects.domain.recoveryFake)
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

    androidMain {
      dependencies {
        implementation(projects.rust.coreFfi)
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
