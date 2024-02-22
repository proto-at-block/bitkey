plugins {
  `kotlin-dsl`
}

dependencies {
  compileOnly(gradleApi())
  implementation(libs.pluginClasspath.android)
  implementation(libs.pluginClasspath.kotlin)
}
