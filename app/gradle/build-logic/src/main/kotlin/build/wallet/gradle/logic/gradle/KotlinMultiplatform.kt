package build.wallet.gradle.logic.gradle

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

internal fun Project.kotlin(configure: KotlinMultiplatformExtension.() -> Unit) {
  extensions.configure(configure)
}

internal fun KotlinMultiplatformExtension.sourceSets(
  configure: NamedDomainObjectContainer<KotlinSourceSet>.() -> Unit,
) {
  (this as org.gradle.api.plugins.ExtensionAware).extensions.configure("sourceSets", configure)
}

/**
 * Configure KMP iOS targets based on [konanTargetsForIOS].
 */
fun KotlinMultiplatformExtension.optimalTargetsForIOS(project: Project): List<KotlinNativeTarget> {
  return mutableListOf<KotlinNativeTarget>().apply {
    val targets = project.konanTargetsForIOS()

    if (targets.iosX64) add(iosX64())
    if (targets.iosArm64) add(iosArm64())
    if (targets.iosSimulatorArm64) add(iosSimulatorArm64())
  }
}
