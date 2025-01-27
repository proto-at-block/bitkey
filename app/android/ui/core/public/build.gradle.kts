plugins {
  id("build.wallet.android.lib")
  kotlin("android")
}

buildLogic {
  android {
    buildFeatures {
      androidResources = true
    }
  }

  compose {
    composeUi()
  }

  test {
    snapshotTests()
  }
}

dependencies {
  api(projects.shared.amountPublic)
  api(projects.shared.uiCorePublic)
  api(projects.shared.priceChartPublic)
  api(libs.android.compose.ui.core)

  implementation(libs.android.accompanist.system.ui.controller)
  implementation(libs.android.compose.ui.material)
  implementation(libs.android.compose.ui.material3)
  implementation(libs.kmp.kotlin.datetime)
  implementation(libs.jvm.zxing)
  implementation(libs.android.voyager.navigator)
  implementation(libs.android.voyager.transitions)
  implementation(projects.shared.stateMachineUiPublic)
}
