package build.wallet.gradle.logic.rust

import build.wallet.gradle.logic.KotlinMultiplatformPlugin
import build.wallet.gradle.logic.gradle.apply
import build.wallet.gradle.logic.rust.extension.KotlinMultiplatformRustExtension
import build.wallet.gradle.logic.rust.util.RustToolchainProvider
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Apply this plugin using `build.wallet.kmp.rust` ID on a library project that needs to
 * compile as a Kotlin multiplatform module and includes Rust
 */
internal class KotlinMultiplatformRustPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.run {
      pluginManager.apply<KotlinMultiplatformPlugin>()

      val extension = extensions.create("rust", KotlinMultiplatformRustExtension::class.java)

      gradle.sharedServices.registerIfAbsent(
        RustToolchainProvider.SERVICE,
        RustToolchainProvider::class.java
      ) {
        parameters.rustupPath.set(extension.rustupPath)
        parameters.cargoPath.set(extension.cargoPath)
      }
    }
  }
}
