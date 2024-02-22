plugins {
  `kotlin-dsl`
  id("build.wallet.dependency-locking")
}

/**
 * Map plugin implementations to their IDs.
 */
gradlePlugin {
  plugins {
    create("kotlinMultiplatformPlugin") {
      id = "build.wallet.kmp"
      implementationClass = "build.wallet.gradle.logic.KotlinMultiplatformPlugin"
    }
    create("androidAppPlugin") {
      id = "build.wallet.android.app"
      implementationClass = "build.wallet.gradle.logic.AndroidAppPlugin"
    }
    create("androidLibPlugin") {
      id = "build.wallet.android.lib"
      implementationClass = "build.wallet.gradle.logic.AndroidLibPlugin"
    }
    create("buildScansPlugin") {
      id = "build.wallet.scans"
      implementationClass = "build.wallet.gradle.logic.GradleBuildScansPlugin"
    }
    create("redactedPlugin") {
      id = "build.wallet.redacted"
      implementationClass = "build.wallet.gradle.logic.RedactedPlugin"
    }
    create("sqldelightPlugin") {
      id = "build.wallet.sqldelight"
      implementationClass = "build.wallet.gradle.logic.sqldelight.SqlDelightPlugin"
    }
    create("kmpRustPlugin") {
      id = "build.wallet.kmp.rust"
      implementationClass = "build.wallet.gradle.logic.rust.KotlinMultiplatformRustPlugin"
    }
    create("reproducibleBugsnagAndroidPlugin") {
      id = "build.wallet.android.bugsnag"
      implementationClass = "build.wallet.gradle.logic.reproducible.ReproducibleBugsnagAndroidPlugin"
    }
  }
}

kotlin {
  val jvmToolchain = libs.versions.jvmToolchain.get().toInt()
  jvmToolchain(jvmToolchain)
}

dependencies {
  compileOnly(gradleApi())

  compileOnly(libs.pluginClasspath.android)
  compileOnly(libs.pluginClasspath.android.paparazzi)
  compileOnly(libs.pluginClasspath.detekt)
  compileOnly(libs.pluginClasspath.gradleEnterprise)
  compileOnly(libs.pluginClasspath.kmp)
  compileOnly(libs.pluginClasspath.kmp.sqldelight)
  compileOnly(libs.pluginClasspath.kotlin)
  compileOnly(libs.pluginClasspath.wire)

  implementation(libs.pluginClasspath.redacted)
  implementation(libs.pluginClasspath.bugsnag.android)
  implementation(libs.pluginClasspath.testLogger)

  implementation(":dependency-locking")

  /**
   * A workaround to add the generated, type-safe version catalog to classpath.
   * https://github.com/gradle/gradle/issues/15383.
   */
  compileOnly(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

layout.buildDirectory = File("_build")
