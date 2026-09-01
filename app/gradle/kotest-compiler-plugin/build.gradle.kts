plugins {
  kotlin("jvm")
}

kotlin {
  compilerOptions {
    allWarningsAsErrors.set(false)
  }
}

dependencies {
  compileOnly(kotlin("stdlib-jdk8"))
  compileOnly(kotlin("compiler-embeddable"))
}

layout.buildDirectory = File("_build")
