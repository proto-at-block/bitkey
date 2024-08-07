package build.wallet.gradle.logic

import build.wallet.gradle.dependencylocking.extension.DependencyLockingExtension
import build.wallet.gradle.dependencylocking.extension.commonDependencyLockingGroups
import build.wallet.gradle.logic.gradle.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Plugin to "fix" version inconsistencies which are prohibited by our custom Gradle dependency locking.
 */
class DependencyLockingDependencyConfigurationPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.extensions.configure<DependencyLockingExtension> {
      registerIgnoredModules()
      target.pinDependencies(this)
    }
  }

  private fun DependencyLockingExtension.registerIgnoredModules() {
    // These libraries are used only in tests, and they are KMP which means they uses host platform dependent library coordinates which the locking doesn't support currently.
    ignoredModules.add("app.cash.paparazzi:layoutlib-*")
    ignoredModules.add("com.android.tools.layoutlib:layoutlib-runtime-*")

    // This dependency cannot be locked because different configurations from the same group can rightfully use two different versions: '1.0' and '9999.0-empty-to-avoid-conflict-with-guava'.
    // See https://mvnrepository.com/artifact/com.google.guava/listenablefuture/9999.0-empty-to-avoid-conflict-with-guava
    ignoredModules.add("com.google.guava:listenablefuture")
  }

  private fun Project.pinDependencies(dependencyLockingExtension: DependencyLockingExtension) {
    dependencyLockingExtension.commonDependencyLockingGroups.buildClasspath.pin(
      libs.android.tools.common,
      libs.android.tools.annotations,
      libs.android.lifecycle.common,
      libs.android.activity.asProvider(),
      libs.android.appcompat,
      libs.android.annotations.asProvider(),
      libs.android.annotations.experimental,
      libs.android.arch.core.runtime,
      libs.android.core.ktx,
      libs.android.compose.animation,
      libs.android.compose.ui.core,
      libs.android.compose.ui.foundation.asProvider(),
      libs.android.compose.ui.foundation.layout,
      libs.android.compose.ui.animation.core,
      libs.android.compose.ui.material.icons.core,
      libs.android.compose.ui.material.ripple,
      libs.android.compose.ui.material.asProvider(),
      libs.android.compose.ui.tooling.preview,
      libs.android.compose.ui.unit,
      libs.android.compose.ui.text,
      libs.android.compose.ui.graphics,
      libs.android.compose.ui.geometry,
      libs.android.compose.ui.util,
      libs.android.emoji2,
      libs.android.fragment,
      libs.android.gson,
      libs.android.google.errorprone.annotations,
      libs.android.google.guava,
      libs.android.google.gms.play.services.tasks,
      libs.android.google.gms.play.services.basement,
      libs.android.core.asProvider(),
      libs.android.collection,
      libs.android.layoutlib.api,
      libs.android.profileinstaller,
      libs.android.sqlite.asProvider(),
      libs.android.sqlite.framework,
      libs.android.test.findbugs,
      libs.android.exifinterface,
      libs.android.io.coil.base,
      libs.jvm.apache.httpclient,
      libs.jvm.bytebuddy.asProvider(),
      libs.jvm.bytebuddy.agent,
      libs.jvm.commons.codec,
      libs.jvm.jna.asProvider(),
      libs.jvm.jna.platform,
      libs.jvm.slf4j,
      libs.jvm.test.opentest4j,
      libs.kmp.crashkios.bugsnag.asProvider(),
      libs.kmp.kotlin.stdlib.asProvider(),
      libs.kmp.kotlin.stdlib.jdk8,
      libs.kmp.kotlin.coroutines.asProvider(),
      libs.kmp.kotlin.coroutines.debug,
      libs.kmp.kotlin.coroutines.android,
      libs.kmp.kotlin.atomicfu,
      libs.kmp.kotlin.annotations,
      libs.kmp.kotlin.serialization.core,
      libs.kmp.kotlin.serialization.json,
      libs.kmp.kotlin.reflection,
      libs.kmp.kotlin.datetime,
      libs.kmp.okhttp,
      libs.kmp.okio,
      libs.kmp.test.kotlin.coroutines
    )

    dependencyLockingExtension.commonDependencyLockingGroups.kotlinCompiler.pin(
      libs.kmp.kotlin.stdlib.jdk8
    )
  }
}
