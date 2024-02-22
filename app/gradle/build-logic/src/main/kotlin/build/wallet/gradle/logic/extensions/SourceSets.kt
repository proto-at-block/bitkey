package build.wallet.gradle.logic.extensions

import org.gradle.kotlin.dsl.get
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetContainer
import kotlin.properties.ReadOnlyProperty

/**
 * Extensions to access source sets (expected to be created).
 */

val KotlinMultiplatformExtension.commonMain: KotlinSourceSet by SourceSetDelegate()
val KotlinMultiplatformExtension.commonTest: KotlinSourceSet by SourceSetDelegate()
val KotlinMultiplatformExtension.commonIntegrationTest: KotlinSourceSet by SourceSetDelegate()

/**
 * commonJvm source set to used by jvm and android targets.
 */
val KotlinMultiplatformExtension.commonJvmMain: KotlinSourceSet by SourceSetDelegate()
val KotlinMultiplatformExtension.commonJvmTest: KotlinSourceSet by SourceSetDelegate()
val KotlinMultiplatformExtension.commonJvmIntegrationTest: KotlinSourceSet by SourceSetDelegate()

val KotlinMultiplatformExtension.jvmMain: KotlinSourceSet by SourceSetDelegate()
val KotlinMultiplatformExtension.jvmTest: KotlinSourceSet by SourceSetDelegate()
val KotlinMultiplatformExtension.jvmIntegrationTest: KotlinSourceSet by SourceSetDelegate()

val KotlinMultiplatformExtension.androidMain: KotlinSourceSet by SourceSetDelegate()
val KotlinMultiplatformExtension.androidUnitTest: KotlinSourceSet by SourceSetDelegate()

val KotlinMultiplatformExtension.iosMain: KotlinSourceSet by SourceSetDelegate()
val KotlinMultiplatformExtension.iosTest: KotlinSourceSet by SourceSetDelegate()

operator fun KotlinSourceSet.invoke(action: KotlinSourceSet.() -> Unit) = action()

internal typealias SourceSetDelegate = ReadOnlyProperty<KotlinSourceSetContainer, KotlinSourceSet>

internal fun SourceSetDelegate(): SourceSetDelegate =
  SourceSetDelegate { thisRef, property ->
    thisRef.sourceSets[property.name]
  }
