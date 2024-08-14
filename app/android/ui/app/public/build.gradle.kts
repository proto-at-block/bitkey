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
  api(projects.shared.priceChartPublic)

  implementation(projects.shared.loggingPublic)
  implementation(libs.android.camera.camera2)
  implementation(libs.android.camera.lifecycle)
  implementation(libs.android.camera.view)
  implementation(libs.android.compose.ui.material3)
  implementation(libs.android.io.coil.compose)
  implementation(libs.android.io.coil.svg)
  implementation(libs.android.lottie.compose)
  implementation(libs.jvm.zxing)

  testImplementation(projects.shared.bitkeyPrimitivesFake)
}
