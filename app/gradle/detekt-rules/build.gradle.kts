plugins {
  kotlin("jvm")
}

kotlin {
  val jvmToolchain = libs.versions.jvmToolchain.get().toInt()
  jvmToolchain(jvmToolchain)
}

dependencies {
  compileOnly(libs.jvm.detekt.api)

  testImplementation(kotlin("test"))
  testImplementation(libs.jvm.detekt.test)
}

layout.buildDirectory = File("_build")
