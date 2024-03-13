import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.accountPublic)
        api(projects.shared.analyticsPublic)
        api(projects.shared.authPublic)
        api(projects.shared.databasePublic)
        api(projects.shared.notificationsPublic)
        api(projects.shared.platformPublic)
        implementation(libs.kmp.ktor.client.content.negotiation)
        implementation(libs.kmp.ktor.client.auth)
        implementation(libs.kmp.ktor.client.core)
        implementation(libs.kmp.ktor.client.json)
        implementation(projects.shared.datadogPublic)
        implementation(projects.shared.serializationPublic)
        // For SocialRecoveryServiceFake
        implementation(projects.shared.ktorTestFake)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.analyticsFake)
        implementation(projects.shared.availabilityFake)
        implementation(projects.shared.authFake)
        implementation(projects.shared.encryptionFake)
        implementation(projects.shared.coroutinesTesting)
        implementation(projects.shared.f8eClientFake)
        implementation(projects.shared.keyboxFake)
        implementation(projects.shared.platformFake)
        implementation(projects.shared.testingPublic)
        implementation(libs.kmp.test.ktor.client.mock)
      }
    }
    val commonJvmMain by getting {
      dependencies {
        api(libs.jvm.ktor.client.okhttp)
      }
    }
    val iosMain by getting {
      dependencies {
        api(libs.native.ktor.client.darwin)
      }
    }
  }
}
