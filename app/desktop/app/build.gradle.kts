import build.wallet.gradle.dependencylocking.util.ifMatches
import build.wallet.gradle.logic.extensions.targets
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.compose.runtime)
  alias(libs.plugins.compose.compiler)
}

kotlin {
  targets(jvm = true)

  sourceSets {
    jvmMain {
      dependencies {
        // Compose Desktop runtime
        implementation(compose.desktop.currentOs)
        implementation(compose.runtime)

        // Shared UI (App composable + AppUiStateMachine)
        implementation(projects.ui.featuresPublic)
        implementation(projects.ui.frameworkPublic)

        // App graph (JvmAppComponentImpl + JvmActivityComponent + generated `create`)
        implementation(projects.shared.appComponentImpl)

        // Implementations required to construct the JVM app component (mirrors AppTester bootstrap)
        implementation(projects.libs.platformImpl)
        implementation(projects.libs.keyValueStoreImpl)
        implementation(projects.libs.cloudStoreImpl)
        implementation(projects.libs.cloudStoreFake)
        implementation(projects.domain.cloudBackupImpl)
        implementation(projects.libs.bdkBindingsImpl)

        implementation(libs.kmp.kotlin.coroutines)
      }
    }
  }
}

compose.desktop {
  application {
    mainClass = "build.wallet.desktop.MainKt"

    // Show "Bitkey Desktop" (not "java"/main-class) in the macOS dock and menu bar when run via
    // `:desktop:app:run`. The window icon itself is set in code via Window(icon = ...).
    jvmArgs += listOf("-Xdock:name=Bitkey Desktop")

    nativeDistributions {
      // Placeholder packaging config. Per-OS installer icons (.icns/.ico/.png) are a follow-up.
      targetFormats(TargetFormat.Dmg)
      packageName = "BitkeyDesktop"
      packageVersion = "1.0.0"
      macOS {
        dockName = "Bitkey Desktop"
      }
    }
  }
}

customDependencyLocking {
  configurations.configureEach {
    // Compose Desktop's currentOs runtime resolves host-specific Skiko artifacts. Locking these
    // desktop-only classpaths makes the global lockfile depend on the host that generated it.
    ifMatches {
      nameIs(
        "jvmMainCompileClasspath",
        "jvmMainRuntimeClasspath",
        "jvmCompileClasspath",
        "jvmRuntimeClasspath",
        "jvmTestCompileClasspath",
        "jvmTestRuntimeClasspath",
        "jvmIntegrationTestCompileClasspath",
        "jvmIntegrationTestRuntimeClasspath",
      )
    } then {
      isLocked.set(false)
    }
  }
}
