import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.keyboxPublic)
        api(projects.shared.platformPublic)
        api(projects.shared.databasePublic)
        implementation(projects.shared.loggingPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.sqldelightTesting)
      }
    }

    val jvmIntegrationTest by getting {
      dependencies {
        implementation(projects.shared.integrationTestingPublic)
      }
    }
  }
}
