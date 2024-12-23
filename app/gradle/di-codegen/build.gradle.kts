plugins {
  kotlin("jvm")
  id("com.google.devtools.ksp")
}

dependencies {
  implementation(libs.jvm.kotlinpoet)
  implementation(libs.jvm.kotlinpoet.ksp)
  implementation(libs.jvm.ksp.api)
  implementation(libs.kmp.kotlin.inject.anvil.runtime)
  implementation(libs.kmp.kotlin.inject.runtime)
  implementation(projects.gradle.kspUtil)
  implementation(projects.shared.diScopesPublic)

  testImplementation(kotlin("test"))
  testImplementation(kotlin("test-junit"))
  testImplementation(testFixtures(projects.gradle.kspUtil))
}

layout.buildDirectory = File("_build")
