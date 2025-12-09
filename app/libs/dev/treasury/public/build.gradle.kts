import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(android = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.libs.moneyPublic)
        api(projects.libs.testingPublic)
        api(projects.domain.walletPublic)
        implementation(libs.kmp.test.kotest.assertions)
        implementation(projects.libs.bdkBindingsPublic)
        implementation(projects.libs.moneyTesting)
        implementation(projects.libs.stdlibPublic)
        implementation(projects.libs.testingPublic)
      }
    }

    val commonJvmMain by getting {
      dependencies {
        implementation(libs.kmp.aws.secretsmanager)
      }
    }

    val jvmMain by getting {
      dependencies {
        implementation(libs.jvm.bdk.legacy)
        implementation(libs.jvm.bitcoin.rpc.client)
      }
    }
  }
}
