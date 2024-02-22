package build.wallet.gradle.logic.gradle

import org.gradle.api.Project

data class KonanTargets(
  val iosArm64: Boolean,
  val iosSimulatorArm64: Boolean,
  val iosX64: Boolean,
)

/**
 * Return configuration of native targets for iOS, optimized for compiling host machine:
 *
 * - If running on apple silicon, only compile for arm64 targets (for simulator or real device).
 * We only compile for modern iOS apps, so there is no need for us to compile and package
 * XC Framework containing x86 binaries in that case.
 *
 * - If running on non-apple silicon (eg intel that doesn't support arm64), compile for arm64
 * (real device) and x64 (simulator) targets.
 */
fun Project.konanTargetsForIOS(): KonanTargets {
  val host = HostEnvironment()
  val skipRealDevice = project.iosSkipRealDevice()
  val skipSimulator = project.iosSkipSimulator()
  require(!skipSimulator || !skipRealDevice) {
    "Can't skip compiling binaries for both iOS real device AND simulator. Update your gradle.properties."
  }

  var iosArm64 = false
  var iosSimulatorArm64 = false
  var iosX64 = false

  // Real device target
  if (!skipRealDevice) {
    iosArm64 = true
  }

  // Simulator target
  if (!skipSimulator) {
    if (host.isArm64Compatible) {
      // Host machine is running on apple silicon (e.g. m1)
      iosSimulatorArm64 = true
    } else {
      // Host machine is running an Intel processor
      iosX64 = true
    }
  }

  require(iosArm64 || iosSimulatorArm64 || iosX64) {
    "Expected to have at least one iOS target enabled: iosArm64=$iosArm64, iosSimulatorArm64=$iosSimulatorArm64, iosX64=$iosX64." +
      "\n" +
      "Host environment: $host"
  }

  return KonanTargets(
    iosArm64 = iosArm64,
    iosSimulatorArm64 = iosSimulatorArm64,
    iosX64 = iosX64
  )
}

/**
 * Determine if shared XC Framework should skip building binaries for real iOS device.
 */
private fun Project.iosSkipRealDevice(): Boolean {
  return project.property("build.wallet.kmp.iosSkipRealDevice") == "true"
}

/**
 * Determine if shared XC Framework should skip building binaries for simulator.
 */
private fun Project.iosSkipSimulator(): Boolean {
  return project.property("build.wallet.kmp.iosSkipSimulator") == "true"
}
