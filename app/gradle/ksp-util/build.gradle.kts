plugins {
  kotlin("jvm")
  id("java-test-fixtures")
}

dependencies {
  api(libs.jvm.kotlinpoet)
  api(libs.jvm.kotlinpoet.ksp)
  api(libs.jvm.ksp.api)

  testFixturesApi(libs.jvm.ksp.api)
  testFixturesApi(libs.jvm.compileTesting)
  testFixturesApi(libs.jvm.compileTesting.ksp)
}

layout.buildDirectory = File("_build")
