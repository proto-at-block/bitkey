import build.wallet.gradle.logic.extensions.allTargets
import build.wallet.gradle.logic.extensions.buildLogic
import build.wallet.gradle.logic.reproducible.GenerateEmergencyExitKitInformationTask

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
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
        implementation(libs.kmp.wire.runtime)
        implementation(projects.domain.accountPublic)
        implementation(projects.domain.walletPublic)
        implementation(projects.libs.timePublic)
        implementation(projects.libs.cloudStorePublic)
        implementation(projects.libs.loggingPublic)
        implementation(projects.libs.stdlibPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.domain.emergencyExitKitFake)
        implementation(projects.domain.bitkeyPrimitivesFake)
        implementation(projects.domain.walletFake)
        implementation(projects.libs.testingPublic)
        implementation(projects.domain.cloudBackupFake)
        implementation(projects.libs.cloudStorePublic)
        implementation(projects.libs.cloudStoreFake)
        implementation(projects.libs.cloudStoreImpl)
        implementation(projects.libs.encryptionFake)
        implementation(projects.libs.platformPublic)
        implementation(projects.libs.platformFake)
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

        implementation(projects.domain.cloudBackupFake)
        implementation(projects.domain.bitkeyPrimitivesFake)
        implementation(projects.domain.emergencyExitKitFake)
        implementation(projects.libs.platformImpl)
        implementation(projects.libs.timeImpl)
      }
    }

    val jvmTest by getting {
      dependencies {
        implementation(projects.libs.platformFake)
        implementation(projects.libs.platformImpl)
      }
    }
  }
}

val generateEmergencyExitKitInformation by tasks.registering(GenerateEmergencyExitKitInformationTask::class) {
  val variables = reproducibleBuildVariables.variables
  buildType = variables.map { it.buildType }
  apkVersion = variables.map { it.emergencyApkVersion }
  apkHash = variables.map { it.emergencyApkHash }
  apkUrl = variables.map { it.emergencyApkUrl }
  outputFile = layout.buildDirectory.file(
    "generated/source/commonMain/build/wallet/emergencyexitkit/EmergencyExitKitAppInformation.kt"
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
      kotlin.srcDir(generateEmergencyExitKitInformation.map { it.outputDirectories })
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
