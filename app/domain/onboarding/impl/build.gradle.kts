import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.libs.keyValueStorePublic)
        api(projects.domain.databasePublic)
        api(projects.domain.debugPublic)
        api(projects.domain.notificationsPublic)
        api(projects.domain.walletPublic)
        implementation(projects.libs.frostPublic)
        implementation(projects.libs.loggingPublic)
        implementation(projects.libs.stdlibPublic)
        implementation(libs.kmp.settings)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.domain.accountFake)
        implementation(projects.domain.authFake)
        implementation(projects.domain.bitkeyPrimitivesFake)
        implementation(projects.domain.f8eClientFake)
        implementation(projects.domain.walletFake)
        implementation(projects.libs.platformFake)
        implementation(projects.libs.sqldelightTesting)
        implementation(projects.libs.testingPublic)
      }
    }

    val jvmIntegrationTest by getting {
      dependencies {
        implementation(projects.shared.integrationTestingPublic)
      }
    }
  }
}
