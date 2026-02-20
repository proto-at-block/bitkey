import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  kotlin("jvm") version libs.versions.kotlin.get()
}

group = "build.wallet"

val jvmTargetVersion = libs.versions.jvmTarget.get()
java {
  val javaVersion = JavaVersion.toVersion(jvmTargetVersion)
  sourceCompatibility = javaVersion
  targetCompatibility = javaVersion
}

kotlin {
  val jvmToolchain = libs.versions.jvmToolchain.get().toInt()
  jvmToolchain(jvmToolchain)
  compilerOptions {
    jvmTarget.set(JvmTarget.fromTarget(jvmTargetVersion))
  }
}

dependencies {
  compileOnly(libs.detekt.api)

  testImplementation(kotlin("test"))
  testImplementation(libs.detekt.test)
}

layout.buildDirectory = File("_build")
