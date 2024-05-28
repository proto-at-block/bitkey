import build.wallet.gradle.logic.GenerateStateMachineDiagramTask
import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.compose.runtime)
  alias(libs.plugins.kotlin.coroutines.native)
  alias(libs.plugins.kotlin.serialization)
  id("build.wallet.redacted")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        implementation(compose.runtime)
        implementation(libs.kmp.molecule.runtime)
        implementation(projects.shared.composeRuntimePublic)
        implementation(projects.shared.serializationPublic)
        implementation(projects.shared.stdlibPublic)
        implementation(libs.kmp.settings)

        api(libs.kmp.test.kotest.framework.api)

        api(projects.shared.fwupPublic)
        api(projects.shared.amountPublic)
        api(projects.shared.authPublic)
        api(projects.shared.bitcoinPublic)
        api(projects.shared.accountPublic)
        api(projects.shared.analyticsPublic)
        api(projects.shared.cloudBackupPublic)
        api(projects.shared.cloudStorePublic)
        api(projects.shared.datadogPublic)
        api(projects.shared.emergencyAccessKitPublic)
        api(projects.shared.f8eClientPublic)
        api(projects.shared.featureFlagPublic)
        api(projects.shared.fingerprintsPublic)
        api(projects.shared.homePublic)
        api(projects.shared.keyValueStoreImpl)
        api(projects.shared.ktorClientPublic)
        api(projects.shared.loggingPublic)
        api(projects.shared.nfcPublic)
        api(projects.shared.notificationsPublic)
        api(projects.shared.onboardingPublic)
        api(projects.shared.resultPublic)
        api(projects.shared.timePublic)
        api(projects.shared.phoneNumberPublic)
        api(projects.shared.platformPublic)
        api(projects.shared.recoveryPublic)
        api(projects.shared.routerPublic)
        api(projects.shared.stateMachineDataPublic)
        api(projects.shared.stateMachineFrameworkPublic)
        api(projects.shared.uiCorePublic)
        api(projects.shared.firmwarePublic)
        api(projects.shared.emailPublic)
        api(projects.shared.coroutinesPublic)
        api(projects.shared.workerPublic)
        implementation(projects.shared.supportPublic)
        implementation(projects.shared.loggingPublic)
        implementation(projects.shared.availabilityPublic)
        implementation(projects.shared.inAppSecurityPublic)
      }
    }

    val androidMain by getting {
      dependencies {
        api(projects.shared.googleSignInPublic)
        implementation(libs.android.compose.ui.activity)
        implementation(libs.android.compose.ui.core)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.accountFake)
        implementation(projects.shared.analyticsFake)
        implementation(projects.shared.authFake)
        implementation(projects.shared.availabilityFake)
        implementation(projects.shared.bdkBindingsFake)
        implementation(projects.shared.bitcoinFake)
        implementation(projects.shared.bitcoinPrimitivesFake)
        implementation(projects.shared.bitkeyPrimitivesFake)
        implementation(projects.shared.cloudBackupFake)
        implementation(projects.shared.cloudStoreFake)
        implementation(projects.shared.coroutinesTesting)
        implementation(projects.shared.datadogFake)
        implementation(projects.shared.emergencyAccessKitFake)
        implementation(projects.shared.emergencyAccessKitImpl)
        implementation(projects.shared.f8eClientFake)
        implementation(projects.shared.featureFlagFake)
        implementation(projects.shared.firmwareFake)
        implementation(projects.shared.fwupFake)
        implementation(projects.shared.homeFake)
        implementation(projects.shared.keyboxFake)
        implementation(projects.shared.keyValueStoreFake)
        implementation(projects.shared.moneyFake)
        implementation(projects.shared.notificationsFake)
        implementation(projects.shared.onboardingFake)
        implementation(projects.shared.nfcFake)
        implementation(projects.shared.emailFake)
        implementation(projects.shared.partnershipsFake)
        implementation(projects.shared.phoneNumberFake)
        implementation(projects.shared.mobilePayFake)
        implementation(projects.shared.timeFake)
        implementation(projects.shared.platformFake)
        implementation(projects.shared.recoveryFake)
        implementation(projects.shared.stateMachineDataFake)
        implementation(projects.shared.stateMachineFrameworkFake)
        implementation(projects.shared.stateMachineFrameworkTesting)
        implementation(projects.shared.stateMachineUiTesting)
        implementation(projects.shared.testingPublic)
        implementation(projects.shared.uiCoreTesting)
        implementation(projects.shared.workerFake)
        implementation(libs.kmp.okio)
        implementation(projects.shared.inAppSecurityFake)
      }
    }

    val jvmIntegrationTest by getting {
      dependencies {
        implementation(projects.shared.bitkeyPrimitivesFake)
        implementation(projects.shared.cloudBackupFake)
        implementation(projects.shared.integrationTestingPublic)
        implementation(projects.shared.moneyTesting)
        implementation(projects.shared.stateMachineFrameworkTesting)
        implementation(projects.shared.stateMachineUiTesting)
        implementation(projects.shared.encryptionFake)
        implementation(projects.shared.cloudStoreFake)
      }
    }
  }
}

tasks.register<GenerateStateMachineDiagramTask>("generateStateMachineDiagram") {
  directory = projectDir

  val permanentExcludes = setOf(
    "ProofOfPossessionNfc",
    "NfcSessionUI"
  ).takeUnless {
    project.hasProperty("overridePermanentExcludes")
  } ?: emptySet()
  val configuredExcludes = (project.findProperty("excludingMachineNames") as? String)
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotBlank() }
    ?.toSet()
    ?: emptySet()
  machineName = (project.findProperty("machineName") as? String)
    ?.trim()
    ?.takeIf { it.isNotBlank() }
  excludes = permanentExcludes + configuredExcludes
  outputFile = (project.findProperty("outputFile") as? String)
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?.let { file(it) }
}
