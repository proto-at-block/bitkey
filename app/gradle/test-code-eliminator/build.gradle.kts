plugins {
  kotlin("jvm")
}

dependencies {
  compileOnly(kotlin("stdlib-jdk8"))
  compileOnly(kotlin("compiler-embeddable"))

  testImplementation(kotlin("test"))
  testImplementation(kotlin("test-junit"))
  testImplementation(libs.jvm.compileTesting)
}

layout.buildDirectory = File("_build")
