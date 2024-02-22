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
        implementation(projects.shared.f8eClientPublic)
        implementation(projects.shared.accountPublic)
        implementation(projects.shared.firmwarePublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.emailFake)
        implementation(libs.kmp.test.kotest.framework.datatest)
      }
    }

    val jvmIntegrationTest by getting {
      dependencies {
        implementation(projects.shared.integrationTestingPublic)
      }
    }
  }
}
