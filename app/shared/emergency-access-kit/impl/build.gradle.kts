import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

buildLogic {
  proto {
    wire {
      kotlin {
        sourcePath {
          srcDirs("${project.rootDir.parent}/proto/build/wallet/emergencyaccesskit/v1")
        }
      }
    }
  }
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(libs.kmp.wire.runtime)
        api(projects.shared.accountPublic)
        api(projects.shared.timePublic)
        implementation(projects.shared.cloudStorePublic)
        implementation(projects.shared.serializationPublic)
        implementation(projects.shared.resultPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(libs.kmp.test.kotest.assertions.json)
        implementation(projects.shared.emergencyAccessKitFake)
        implementation(projects.shared.bitkeyPrimitivesFake)
        implementation(projects.shared.testingPublic)
        implementation(projects.shared.cloudBackupFake)
        implementation(projects.shared.encryptionFake)
      }
    }

    val androidMain by getting {
      dependencies {
        implementation(libs.jvm.zxing)
      }
    }

    val androidInstrumentedTest by getting {
      dependencies {
        implementation(libs.jvm.test.junit)
        implementation(libs.android.test.junit)
        implementation(libs.android.test.junit.ktx)
        implementation(libs.android.test.espresso.core)

        implementation(projects.shared.cloudBackupFake)
        implementation(projects.shared.bitkeyPrimitivesFake)
        implementation(projects.shared.emergencyAccessKitFake)
        implementation(projects.shared.platformImpl)
        implementation(projects.shared.timeImpl)
      }
    }

    val jvmTest by getting {
      dependencies {
        api(projects.shared.platformFake)
        implementation(projects.shared.platformImpl)
      }
    }
  }
}
