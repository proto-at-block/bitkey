import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `kotlin-dsl`
}

/**
 * Map plugin implementations to their IDs.
 */
gradlePlugin {
  plugins {
    create("dependencyLockingPlugin") {
      id = "build.wallet.dependency-locking"
      implementationClass = "build.wallet.gradle.dependencylocking.DependencyLockingPlugin"
    }
    create("dependencyLockingCommonGroupConfigurationPlugin") {
      id = "build.wallet.dependency-locking.common-group-configuration"
      implementationClass = "build.wallet.gradle.dependencylocking.DependencyLockingCommonGroupConfigurationPlugin"
    }
  }
}

val jvmTargetVersion = libs.versions.jvmTarget.get()
java {
  val javaVersion = JavaVersion.toVersion(jvmTargetVersion)
  sourceCompatibility = javaVersion
  targetCompatibility = javaVersion
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    jvmTarget = jvmTargetVersion
  }
}

kotlin {
  val jvmToolchain = libs.versions.jvmToolchain.get().toInt()
  jvmToolchain(jvmToolchain)
}

dependencies {
  compileOnly(gradleApi())
}

layout.buildDirectory = File("_build")
