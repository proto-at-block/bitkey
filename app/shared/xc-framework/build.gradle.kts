import build.wallet.gradle.logic.extensions.targets
import build.wallet.gradle.logic.gradle.HostEnvironment
import build.wallet.gradle.logic.gradle.konanTargetsForIOS
import build.wallet.gradle.logic.gradle.optimalTargetsForIOS

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
  alias(libs.plugins.compose.runtime)
  alias(libs.plugins.compose.compiler)
}

kotlin {
  targets(ios = true)

  /**
   * Runtime dependencies needed for the XC Framework.
   */
  val runtimeDependencies =
    listOf(
      projects.shared.appComponentImpl
    )

  val testDependencies = listOf(
    projects.domain.bitkeyPrimitivesFake,
    projects.domain.cloudBackupFake,
    projects.domain.emergencyExitKitFake,
    projects.domain.emergencyExitKitImpl,
    projects.domain.emergencyExitKitPublic,
    projects.libs.contactMethodImpl,
    projects.libs.timeImpl
  )

  sourceSets {
    iosMain {
      kotlin.srcDirs(layout.buildDirectory.dir("generated/snapshots"))
      dependencies {
        runtimeDependencies.onEach { dep ->
          api(dep)
        }
        testDependencies.forEach { dep ->
          api(dep)
        }
      }
    }
  }

  /**
   * Dependencies which APIs should be exposed to iOS code.
   * These APIs will end up in the generated XCFramework's headers.
   *
   * Ideally only `public` dependencies should be added here.
   */
  val exposedDependencies =
    listOf(
      projects.libs.bugsnagPublic,
      projects.libs.datadogPublic,
      projects.domain.accountPublic,
      projects.domain.analyticsPublic,
      // ActivityComponentImpl is the top level DI component that provides the rest of the KMP
      // dependencies, so `impl` is expected here.
      projects.shared.appComponentImpl,
      projects.libs.bdkBindingsPublic,
      projects.domain.walletPublic,
      projects.libs.bitcoinPrimitivesPublic,
      projects.domain.bitkeyPrimitivesPublic,
      projects.domain.cloudBackupPublic,
      projects.libs.cloudStorePublic,
      projects.libs.encryptionPublic,
      projects.domain.hardwarePublic,
      projects.libs.frostPublic,
      projects.libs.loggingPublic,
      projects.domain.notificationsPublic,
      projects.libs.platformPublic,
      projects.libs.contactMethodPublic,
      projects.ui.routerPublic,
      projects.libs.secureEnclavePublic,
      projects.libs.grantsPublic,
      libs.native.nserror.kt
    )

  // Log host env and ios targets for debugging.
  val host = HostEnvironment()
  logger.info("Compiling host env: $host")
  val konanTargets = konanTargetsForIOS()
  logger.info("iOS targets: $konanTargets")

  optimalTargetsForIOS(project).forEach {
    it.binaries.framework {
      baseName = "Shared"
      isStatic = true

      (exposedDependencies + testDependencies).onEach { dep ->
        export(dep)
      }

      freeCompilerArgs += listOf(
        "-linker-option", "-framework", "-linker-option", "Metal",
        "-linker-option", "-framework", "-linker-option", "CoreText",
        "-linker-option", "-framework", "-linker-option", "CoreGraphics"
      )
    }
  }
}
