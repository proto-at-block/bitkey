plugins {
  id("build.wallet.android.lib")
  kotlin("android")
}

buildLogic {
  compose {
    composeUi()
  }

  test {
    unitTests()
    snapshotTests()
  }
}

dependencies {
  api(projects.android.nfcPublic)
  api(projects.android.uiCorePublic)
  api(projects.shared.stateMachineUiPublic)
  api(projects.shared.uiCorePublic)

  implementation(projects.shared.loggingPublic)
  implementation(libs.android.camera.camera2)
  implementation(libs.android.camera.lifecycle)
  implementation(libs.android.camera.view)
  implementation(libs.android.compose.ui.material3)
  implementation(libs.android.lottie.compose)
  implementation(libs.jvm.zxing)
}
