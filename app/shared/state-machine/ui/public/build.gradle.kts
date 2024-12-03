import build.wallet.gradle.logic.GenerateStateMachineDiagramTask
import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.ksp")
  alias(libs.plugins.compose.runtime)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.coroutines.native)
  alias(libs.plugins.kotlin.serialization)
  id("build.wallet.redacted")
}

kotlin {
  allTargets()

  sourceSets {
    all {
      languageSettings {
        optIn("androidx.compose.material.ExperimentalMaterialApi")
        optIn("androidx.compose.material3.ExperimentalMaterial3Api")
        optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
      }
    }
    commonMain {
      dependencies {
        implementation(compose.runtime)
        implementation(compose.components.resources)
        implementation(libs.kmp.molecule.runtime)
        implementation(projects.shared.composeRuntimePublic)
        implementation(projects.shared.serializationPublic)
        implementation(projects.shared.stdlibPublic)
        implementation(libs.kmp.settings)
        implementation(libs.android.voyager.navigator)
        implementation(libs.android.voyager.transitions)
        implementation(libs.kmp.compottie)
        implementation(libs.kmp.compottie.resources)

        api(libs.kmp.test.kotest.framework.api)

        api(projects.shared.fwupPublic)
        api(projects.shared.amountPublic)
        // TODO: remove dependency on :impl.
        implementation(projects.shared.amountImpl) {
          because("Depends on AmountCalculatorImpl, DecimalNumberCalculatorImpl, and WholeNumberCalculatorImpl.")
        }
        api(projects.shared.authPublic)
        api(projects.shared.bitcoinPublic)
        api(projects.shared.bootstrapPublic)
        api(projects.shared.accountPublic)
        api(projects.shared.analyticsPublic)
        api(projects.shared.cloudBackupPublic)
        api(projects.shared.cloudStorePublic)
        api(projects.shared.coachmarkPublic)
        api(projects.shared.datadogPublic)
        api(projects.shared.emergencyAccessKitPublic)
        api(projects.shared.f8eClientPublic)
        api(projects.shared.featureFlagPublic)
        api(projects.shared.fwupPublic)
        api(projects.shared.homePublic)
        api(projects.shared.keyValueStoreImpl)
        api(projects.shared.ktorClientPublic)
        api(projects.shared.loggingPublic)
        api(projects.shared.nfcPublic)
        api(projects.shared.notificationsPublic)
        api(projects.shared.onboardingPublic)
        api(projects.shared.resultPublic)
        api(projects.shared.timePublic)
        api(projects.shared.contactMethodPublic)
        api(projects.shared.platformPublic)
        api(projects.shared.recoveryPublic)
        api(projects.shared.routerPublic)
        api(projects.shared.stateMachineDataPublic)
        api(projects.shared.stateMachineFrameworkPublic)
        api(projects.shared.uiCorePublic)
        api(projects.shared.firmwarePublic)
        api(projects.shared.workerPublic)
        api(projects.shared.priceChartPublic)
        implementation(projects.shared.supportPublic)
        implementation(projects.shared.loggingPublic)
        implementation(projects.shared.availabilityPublic)
        implementation(projects.shared.inAppSecurityPublic)
        implementation(projects.shared.inheritancePublic)
        implementation(projects.shared.relationshipsPublic)
      }
    }

    val androidMain by getting {
      dependencies {
        api(projects.shared.googleSignInPublic)
        implementation(libs.android.camera.view)
        implementation(libs.android.camera.lifecycle)
        implementation(libs.android.camera.camera2)
        implementation(libs.android.compose.ui.activity)
        implementation(libs.android.compose.ui.core)
        implementation(libs.android.accompanist.system.ui.controller)
        implementation(libs.android.compose.ui.tooling)
        implementation(libs.android.compose.ui.tooling.preview)
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
        implementation(projects.shared.bootstrapFake)
        implementation(projects.shared.cloudBackupFake)
        implementation(projects.shared.cloudStoreFake)
        implementation(projects.shared.datadogFake)
        implementation(projects.shared.debugFake)
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
        implementation(projects.shared.partnershipsFake)
        implementation(projects.shared.contactMethodFake)
        implementation(projects.shared.mobilePayFake)
        implementation(projects.shared.timeFake)
        implementation(projects.shared.platformFake)
        implementation(projects.shared.priceChartFake)
        implementation(projects.shared.recoveryFake)
        implementation(projects.shared.relationshipsFake)
        implementation(projects.shared.stateMachineDataFake)
        implementation(projects.shared.stateMachineFrameworkFake)
        implementation(projects.shared.stateMachineFrameworkTesting)
        implementation(projects.shared.stateMachineUiTesting)
        implementation(projects.shared.supportFake)
        implementation(projects.shared.testingPublic)
        implementation(projects.shared.uiCoreTesting)
        implementation(projects.shared.workerFake)
        implementation(libs.kmp.okio)
        implementation(projects.shared.inAppSecurityFake)
        implementation(projects.shared.platformFake)
        implementation(projects.shared.coachmarkFake)
        implementation(projects.shared.inheritanceFake)
      }
    }

    val commonJvmMain by getting {
      dependencies {
        implementation(libs.jvm.zxing)
      }
    }

    val jvmIntegrationTest by getting {
      dependencies {
        implementation(projects.shared.bitkeyPrimitivesFake)
        implementation(projects.shared.bootstrapFake)
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

buildLogic {
  ksp {
    processors(projects.gradle.formbodymodelGenerator)
    targets(ios = true) // FormBodyModel snapshot generation is only used for iOS builds.
  }
}
