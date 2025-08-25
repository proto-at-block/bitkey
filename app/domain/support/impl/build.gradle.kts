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
        implementation(projects.libs.encryptionPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.domain.walletFake)
        implementation(projects.libs.contactMethodFake)
        implementation(projects.domain.f8eClientFake)
        implementation(projects.domain.accountFake)
        implementation(libs.kmp.test.kotest.framework.engine)
      }
    }

    jvmTest {
      dependencies {
        implementation(projects.libs.encryptionFake)
        implementation(projects.libs.encryptionImpl)
      }
    }

    val jvmIntegrationTest by getting {
      dependencies {
        implementation(projects.shared.integrationTestingPublic)
      }
    }
  }
}
