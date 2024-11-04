import build.wallet.gradle.dependencylocking.extension.commonDependencyLockingGroups
import build.wallet.gradle.dependencylocking.util.ifMatches

plugins {
  id("build.wallet.android.app")
  kotlin("android")
}

buildLogic {
  compose {
    composeUi()
  }
}

dependencies {
  implementation(projects.android.uiCorePublic)
  implementation(projects.shared.stateMachineUiPublic)
  implementation(projects.sample.shared)

  implementation(libs.android.activity.ktx)
  implementation(libs.android.compose.ui.activity)
}

android {
  namespace = "bitkey.sample"
  defaultConfig {
    applicationId = "bitkey.sample"
    versionCode = 1
    versionName = "1.0"
  }
}

customDependencyLocking {
  android.buildTypes.configureEach {
    val buildTypeName = name

    configurations.configureEach {
      ifMatches {
        nameIs("${buildTypeName}WearBundling", "${buildTypeName}ReverseMetadataValues")
      } then {
        dependencyLockingGroup = commonDependencyLockingGroups.buildClasspath
      }
    }
  }
}
