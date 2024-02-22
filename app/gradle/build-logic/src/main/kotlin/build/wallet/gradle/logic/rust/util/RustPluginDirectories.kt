package build.wallet.gradle.logic.rust.util

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider

internal val Project.rustBuildDirectory: Provider<Directory>
  get() = layout.buildDirectory.dir("rust")

internal val Project.rustUniffiOutputDirectory: Provider<Directory>
  get() = rustBuildDirectory.map { it.dir("uniffi").dir("kotlin") }

internal val Project.rustBinOutputDirectory: Provider<Directory>
  get() = rustBuildDirectory.map { it.dir("bin") }
