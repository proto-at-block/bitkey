plugins {
  id("build.wallet.android.lib")
  kotlin("android")
}

dependencies {
  api(projects.shared.platformPublic)
  implementation(projects.shared.loggingPublic)
}
