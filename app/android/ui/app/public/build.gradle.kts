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
    unitTests()
    snapshotTests()
  }
}

kotlin {
  sourceSets {
    test {
      kotlin.srcDir(layout.buildDirectory.dir("generated/snapshots"))
    }
  }
}

dependencies {
  api(projects.android.uiCorePublic)
  api(projects.ui.featuresPublic)
  api(projects.ui.frameworkPublic)
  api(projects.shared.priceChartPublic)
  implementation(projects.ui.snapshotGeneratorApiPublic)

  implementation(projects.libs.loggingPublic)
  implementation(libs.android.camera.camera2)
  implementation(libs.android.camera.lifecycle)
  implementation(libs.android.camera.view)
  implementation(libs.android.compose.ui.material3)
  implementation(libs.kmp.coil.compose)
  implementation(libs.kmp.coil.svg)
  implementation(libs.jvm.zxing)

  testImplementation(projects.domain.bitkeyPrimitivesFake)
  testImplementation(projects.domain.inheritancePublic)
}
