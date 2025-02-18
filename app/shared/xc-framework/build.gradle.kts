import build.wallet.gradle.logic.extensions.targets
import build.wallet.gradle.logic.gradle.HostEnvironment
import build.wallet.gradle.logic.gradle.konanTargetsForIOS
import build.wallet.gradle.logic.gradle.optimalTargetsForIOS
import org.jetbrains.kotlin.gradle.plugin.mpp.BitcodeEmbeddingMode.DISABLE

plugins {
  id("build.wallet.kmp")
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
      projects.shared.accountImpl,
      projects.shared.accountPublic,
      projects.shared.amountImpl,
      projects.shared.amountPublic,
      projects.shared.analyticsImpl,
      projects.shared.analyticsPublic,
      projects.shared.appComponentImpl,
      projects.shared.appComponentPublic,
      projects.shared.bdkBindingsImpl,
      projects.shared.bdkBindingsPublic,
      projects.shared.bitcoinImpl,
      projects.shared.bitcoinPublic,
      projects.shared.bitcoinPrimitivesPublic,
      projects.shared.bitkeyPrimitivesFake,
      projects.shared.bitkeyPrimitivesPublic,
      projects.shared.bugsnagImpl,
      projects.shared.bugsnagPublic,
      projects.shared.cloudBackupFake,
      projects.shared.cloudBackupImpl,
      projects.shared.cloudBackupPublic,
      projects.shared.cloudStoreImpl,
      projects.shared.cloudStorePublic,
      projects.shared.datadogPublic,
      projects.shared.encryptionPublic,
      projects.shared.keyValueStoreImpl,
      projects.shared.keyValueStorePublic,
      projects.shared.keyboxPublic,
      projects.shared.loggingImpl,
      projects.shared.loggingPublic,
      projects.shared.f8eClientImpl,
      projects.shared.f8eClientPublic,
      projects.shared.firmwareImpl,
      projects.shared.firmwarePublic,
      projects.shared.frostImpl,
      projects.shared.frostPublic,
      projects.shared.fwupImpl,
      projects.shared.fwupPublic,
      projects.shared.memfaultImpl,
      projects.shared.memfaultPublic,
      projects.shared.nfcImpl,
      projects.shared.nfcPublic,
      projects.shared.contactMethodImpl,
      projects.shared.contactMethodPublic,
      projects.shared.platformImpl,
      projects.shared.platformPublic,
      projects.shared.recoveryImpl,
      projects.shared.recoveryPublic,
      projects.shared.routerPublic,
      projects.shared.sqldelightImpl,
      projects.shared.sqldelightPublic,
      projects.shared.workerPublic,
      projects.shared.secureEnclavePublic,
      projects.shared.secureEnclaveImpl
    )

  sourceSets {
    commonMain {
      dependencies {
        runtimeDependencies.onEach { dep ->
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
      projects.shared.accountPublic,
      projects.shared.analyticsPublic,
      // ActivityComponentImpl is the top level DI component that provides the rest of the KMP
      // dependencies, so `impl` is expected here.
      projects.shared.appComponentImpl,
      projects.shared.appComponentPublic,
      projects.shared.availabilityPublic,
      projects.shared.bdkBindingsPublic,
      projects.shared.bitcoinPublic,
      projects.shared.bitcoinPrimitivesPublic,
      projects.shared.bitkeyPrimitivesFake,
      projects.shared.bitkeyPrimitivesPublic,
      projects.shared.bugsnagPublic,
      projects.shared.cloudBackupFake,
      projects.shared.cloudBackupPublic,
      projects.shared.cloudStoreImpl,
      projects.shared.cloudStorePublic,
      projects.shared.datadogPublic,
      projects.shared.emergencyAccessKitFake,
      projects.shared.emergencyAccessKitImpl,
      // projects.shared.emergencyAccessKitPublic,
      projects.shared.encryptionPublic,
      projects.shared.f8eClientPublic,
      projects.shared.fwupPublic,
      projects.shared.firmwareImpl,
      projects.shared.firmwarePublic,
      projects.shared.frostPublic,
      projects.shared.keyboxPublic,
      projects.shared.loggingPublic,
      projects.shared.memfaultPublic,
      projects.shared.nfcPublic,
      projects.shared.notificationsPublic,
      projects.shared.platformImpl,
      projects.shared.platformPublic,
      projects.shared.contactMethodPublic,
      projects.shared.contactMethodImpl,
      projects.shared.routerPublic,
      projects.shared.timeImpl,
      projects.shared.secureEnclavePublic,
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

      // Disable iOS bitcode completely.
      embedBitcode(DISABLE)

      exposedDependencies.onEach { dep ->
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
