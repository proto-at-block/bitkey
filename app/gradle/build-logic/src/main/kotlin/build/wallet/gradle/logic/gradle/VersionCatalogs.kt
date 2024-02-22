package build.wallet.gradle.logic.gradle

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Project
import org.gradle.kotlin.dsl.the

/**
 * A workaround to access generated, type-safe version catalog within this plugin.
 * The catalog is defined in [libs.versions.toml].
 *
 * https://github.com/gradle/gradle/issues/15383.
 */
internal val Project.libs get() = the<LibrariesForLibs>()
