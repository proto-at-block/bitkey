import build.wallet.gradle.logic.extensions.allTargets
import build.wallet.gradle.logic.extensions.buildLogic
import build.wallet.gradle.logic.reproducible.GenerateEmergencyAccessKitInformationTask

plugins {
  id("build.wallet.kmp")
  id("build.wallet.build.reproducible")
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

val generateEmergencyAccessKitInformation by tasks.registering(GenerateEmergencyAccessKitInformationTask::class) {
  val variables = reproducibleBuildVariables.variables
  apkVersion = variables.map { it.emergencyApkVersion }
  apkHash = variables.map { it.emergencyApkHash }
  apkUrl = variables.map { it.emergencyApkUrl }
  outputFile = layout.buildDirectory.file(
    "generated/source/commonMain/build/wallet/emergencyaccesskit/EmergencyAccessKitAppInformation.kt"
  )
  // `outputDirectories` aren't used in the task, but this way the task gets run automatically by Gradle because we register it below as sources
  outputDirectories.setFrom(
    layout.buildDirectory.dir(
      "generated/source/commonMain"
    )
  )
}

kotlin {
  sourceSets {
    commonMain {
      kotlin.srcDir(generateEmergencyAccessKitInformation.map { it.outputDirectories })
    }
  }
}

buildLogic {
  android {
    buildFeatures {
      androidResources = true
    }
  }
}
