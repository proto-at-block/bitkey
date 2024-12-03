import build.wallet.gradle.dependencylocking.extension.commonDependencyLockingGroups

plugins {
  `kotlin-dsl`
  id("build.wallet.dependency-locking")
  id("build.wallet.dependency-locking.common-group-configuration")
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
    create("kspPlugin") {
      id = "build.wallet.ksp"
      implementationClass = "build.wallet.gradle.logic.ksp.KspPlugin"
    }
    create("reproducibleBugsnagAndroidPlugin") {
      id = "build.wallet.android.bugsnag"
      implementationClass = "build.wallet.gradle.logic.reproducible.ReproducibleBugsnagAndroidPlugin"
    }
    create("reproducibleBuildVariablesPlugin") {
      id = "build.wallet.build.reproducible"
      implementationClass = "build.wallet.gradle.logic.reproducible.ReproducibleBuildVariablesPlugin"
    }
    create("dependencyLockingDependencyConfigurationPlugin") {
      id = "build.wallet.dependency-locking.dependency-configuration"
      implementationClass = "build.wallet.gradle.logic.DependencyLockingDependencyConfigurationPlugin"
    }
    create("automaticKotlinOptInPlugin") {
      id = "build.wallet.kotlin.opt-in"
      implementationClass = "build.wallet.gradle.logic.AutomaticKotlinOptInPlugin"
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
  compileOnly(libs.pluginClasspath.android.paparazzi) {
    exclude("org.jetbrains.kotlin", "kotlin-stdlib")
  }
  compileOnly(libs.pluginClasspath.detekt)
  compileOnly(libs.pluginClasspath.gradleEnterprise)
  compileOnly(libs.pluginClasspath.kmp)
  compileOnly(libs.pluginClasspath.kmp.sqldelight) {
    exclude("org.jetbrains.kotlin", "kotlin-stdlib")
  }
  compileOnly(libs.pluginClasspath.kotlin)
  compileOnly(libs.pluginClasspath.wire)

  implementation(libs.pluginClasspath.redacted)
  implementation(libs.pluginClasspath.bugsnag.android) {
    exclude("org.jetbrains.kotlin")
  }

  implementation(":dependency-locking")

  /**
   * A workaround to add the generated, type-safe version catalog to classpath.
   * https://github.com/gradle/gradle/issues/15383.
   */
  compileOnly(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

layout.buildDirectory = File("_build")

customDependencyLocking {
  configurations.named("embeddedKotlin").configure {
    dependencyLockingGroup = commonDependencyLockingGroups.buildToolchain
  }

  commonDependencyLockingGroups.buildClasspath.pin(libs.jvm.asm)
}
