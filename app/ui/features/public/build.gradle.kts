import build.wallet.gradle.logic.extensions.allTargets
import build.wallet.gradle.logic.extensions.isBuildingReleaseArtifact
import build.wallet.gradle.logic.ksp.KspProcessorConfig

plugins {
  id("build.wallet.kmp")
  id("build.wallet.ksp")
  id("build.wallet.di")
  alias(libs.plugins.compose.runtime)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  id("build.wallet.redacted")
  id("build.wallet.test-code-eliminator")
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
        implementation(projects.libs.composeRuntimePublic)
        implementation(projects.libs.stdlibPublic)
        implementation(libs.kmp.settings)
        implementation(libs.android.voyager.navigator)
        implementation(libs.android.voyager.transitions)
        implementation(libs.kmp.compottie)
        implementation(libs.kmp.compottie.resources)

        api(projects.libs.datadogPublic)
        api(projects.libs.amountPublic)
        // TODO: remove dependency on :impl.
        implementation(projects.libs.amountImpl) {
          because("Depends on AmountCalculatorImpl, DecimalNumberCalculatorImpl, and WholeNumberCalculatorImpl.")
        }
        api(projects.domain.authPublic)
        api(projects.domain.bootstrapPublic)
        api(projects.domain.accountPublic)
        api(projects.domain.analyticsPublic)
        api(projects.domain.cloudBackupPublic)
        api(projects.libs.cloudStorePublic)
        api(projects.domain.coachmarkPublic)
        api(projects.domain.emergencyExitKitPublic)
        api(projects.domain.featureFlagPublic)
        api(projects.libs.keyValueStoreImpl)
        implementation(libs.kmp.settings.coroutines)
        api(projects.libs.ktorClientPublic)
        api(projects.libs.loggingPublic)
        api(projects.domain.metricsPublic)
        api(projects.domain.notificationsPublic)
        api(projects.domain.onboardingPublic)
        api(projects.libs.timePublic)
        api(projects.libs.contactMethodPublic)
        api(projects.libs.platformPublic)
        api(projects.domain.recoveryPublic)
        api(projects.ui.routerPublic)
        api(projects.domain.dataStateMachinePublic)
        api(projects.libs.stateMachinePublic)
        api(projects.ui.frameworkPublic)
        api(projects.domain.hardwarePublic)
        api(projects.domain.walletPublic)
        api(projects.domain.workerPublic)
        api(projects.shared.priceChartPublic)
        api(projects.domain.securityCenterPublic)
        api(projects.domain.privilegedActionsPublic)
        implementation(projects.shared.priceChartFake)
        implementation(projects.shared.balanceUtilsImpl)
        implementation(projects.domain.supportPublic)
        implementation(projects.libs.loggingPublic)
        implementation(projects.domain.availabilityPublic)
        implementation(projects.domain.inAppSecurityPublic)
        implementation(projects.domain.inheritancePublic)
        implementation(projects.domain.relationshipsPublic)
        implementation(projects.ui.snapshotGeneratorApiPublic)
      }
    }

    val androidMain by getting {
      dependencies {
        api(projects.libs.googleSignInPublic)
        implementation(libs.android.camera.view)
        implementation(libs.android.camera.lifecycle)
        implementation(libs.android.camera.camera2)
        implementation(libs.android.compose.ui.activity)
        implementation(libs.android.compose.ui.core)
        implementation(libs.android.compose.ui.tooling)
        implementation(libs.android.compose.ui.tooling.preview)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.libs.datadogFake)
        implementation(projects.domain.accountFake)
        implementation(projects.domain.analyticsFake)
        implementation(projects.domain.authFake)
        implementation(projects.domain.availabilityFake)
        implementation(projects.libs.bdkBindingsFake)
        implementation(projects.libs.bitcoinPrimitivesFake)
        implementation(projects.domain.bitkeyPrimitivesFake)
        implementation(projects.domain.bootstrapFake)
        implementation(projects.domain.cloudBackupFake)
        implementation(projects.libs.cloudStoreFake)
        implementation(projects.domain.debugFake)
        implementation(projects.domain.emergencyExitKitFake)
        implementation(projects.domain.emergencyExitKitImpl)
        implementation(projects.domain.f8eClientFake)
        implementation(projects.domain.featureFlagFake)
        implementation(projects.domain.hardwareFake)
        implementation(projects.libs.keyValueStoreFake)
        implementation(projects.domain.metricsFake)
        implementation(projects.libs.moneyFake)
        implementation(projects.domain.notificationsFake)
        implementation(projects.domain.onboardingFake)
        implementation(projects.domain.partnershipsFake)
        implementation(projects.libs.contactMethodFake)
        implementation(projects.domain.mobilePayFake)
        implementation(projects.libs.timeFake)
        implementation(projects.libs.platformFake)
        implementation(projects.shared.priceChartFake)
        implementation(projects.domain.privilegedActionsImpl)
        implementation(projects.domain.privilegedActionsFake)
        implementation(projects.shared.balanceUtilsImpl)
        implementation(projects.domain.recoveryFake)
        implementation(projects.domain.relationshipsFake)
        implementation(projects.domain.dataStateMachineFake)
        implementation(projects.libs.stateMachineFake)
        implementation(projects.libs.stateMachineTesting)
        implementation(projects.ui.featuresTesting)
        implementation(projects.domain.supportFake)
        implementation(projects.libs.testingPublic)
        implementation(projects.ui.frameworkTesting)
        implementation(projects.domain.workerFake)
        implementation(libs.kmp.okio)
        implementation(projects.domain.inAppSecurityFake)
        implementation(projects.libs.platformFake)
        implementation(projects.domain.coachmarkFake)
        implementation(projects.domain.inheritanceFake)
        // TODO: remove dependency on :impl.
        implementation(projects.libs.amountImpl) {
          because("Depends on DoubleFormatterImpl")
        }
        implementation(projects.domain.walletFake)
        implementation(projects.domain.securityCenterFake)
      }
    }

    val commonJvmMain by getting {
      dependencies {
        implementation(libs.jvm.zxing)
      }
    }

    val jvmIntegrationTest by getting {
      dependencies {
        implementation(projects.domain.bitkeyPrimitivesFake)
        implementation(projects.domain.bootstrapFake)
        implementation(projects.domain.cloudBackupFake)
        implementation(projects.domain.walletFake)
        implementation(projects.domain.privilegedActionsFake)
        implementation(projects.shared.integrationTestingPublic)
        implementation(projects.libs.moneyTesting)
        implementation(projects.libs.stateMachineTesting)
        implementation(projects.libs.encryptionFake)
        implementation(projects.libs.cloudStoreFake)
        implementation(projects.ui.featuresTesting)
        implementation(projects.ui.frameworkTesting)
        implementation(projects.ui.routerPublic)
        implementation(projects.ui.featuresPublic)
      }
    }
  }
}

testCodeEliminator {
  enabled.set(isBuildingReleaseArtifact())
}

buildLogic {
  ksp {
    val androidUiLayout = projects.android.uiAppPublic.dependencyProject.layout
    val xcFrameworkProject = projects.shared.xcFramework.dependencyProject
    arg(
      "swiftTestCaseOutputDirectory",
      rootDir.resolve("ios/Wallet/Tests/SnapshotTests/generated").absolutePath
    )
    arg(
      "kotlinTestCaseOutputDirectory",
      androidUiLayout.projectDirectory.dir("_build/generated/snapshots").asFile.absolutePath
    )
    arg(
      "kotlinIosOutputDirectory",
      xcFrameworkProject.projectDir.resolve("_build/generated/snapshots").absolutePath
    )
    processors(
      KspProcessorConfig(
        deps = listOf(
          projects.gradle.snapshotGenerator
        ),
        android = false,
        jvm = false,
        ios = true
      )
    )
  }
}
