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
        implementation(projects.domain.f8eClientPublic)
        implementation(projects.domain.accountPublic)
        implementation(projects.domain.hardwarePublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.libs.contactMethodFake)
        implementation(libs.kmp.test.kotest.framework.engine)
      }
    }

    val jvmIntegrationTest by getting {
      dependencies {
        implementation(projects.shared.integrationTestingPublic)
      }
    }
  }
}
