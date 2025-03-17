plugins {
  id("build.wallet.android.lib")
  kotlin("android")
}

buildLogic {
  compose {
    composeUi()
  }
}

dependencies {
  api(libs.android.test.paparazzi)
  api(libs.kmp.test.kotest.framework.engine)
  api(projects.android.uiCorePublic)
}
