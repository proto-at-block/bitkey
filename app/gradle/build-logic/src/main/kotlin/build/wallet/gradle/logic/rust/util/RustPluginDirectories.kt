package build.wallet.gradle.logic.rust.util

import build.wallet.gradle.logic.rust.extension.KotlinMultiplatformRustExtension
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider

/**
 * Directory for generated Rust UniFFI Kotlin bindings.
 */
internal val Project.rustUniffiOutputDirectory: Provider<Directory>
  get() = layout.buildDirectory
    .dir("generated/uniffi/kotlin")

internal val KotlinMultiplatformRustExtension.rustBuildDirectory: Provider<Directory>
  get() = cargoWorkspace.dir("_build/rust")

internal val KotlinMultiplatformRustExtension.rustBinOutputDirectory: Provider<Directory>
  get() = rustBuildDirectory.map { it.dir("bin") }
