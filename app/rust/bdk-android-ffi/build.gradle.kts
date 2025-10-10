import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp.rust")
  id("build.wallet.android.lib")
}

kotlin {
  targets(android = true)
}

val hermitDir = rootDir.parentFile.resolve("bin")

rust {
  packageName = "bdk-android-ffi"
  libraryName = "bdkffi"
  cargoPath = hermitDir.resolve("cargo")
  rustupPath = hermitDir.resolve("rustup")
  cargoWorkspace = projectDir.parentFile
  val bdkAndroidWorkspace = cargoWorkspace.dir("../bdk-android-ffi").get()
  rustProjectFiles.from(
    provider {
      // This is only an approximation. The Rust compiler also has a cache so the performance impact of the compile task being incorrectly invalidated is not that high.
      cargoWorkspace.asFileTree.matching { exclude("**/_build") } +
        bdkAndroidWorkspace.asFileTree.matching { exclude("build", "target") }
    }
  )

  android {
    arm32()
    arm64()
    x64()

    apiLevel = libs.versions.android.sdk.min.get().toInt()
  }
}

android {
  ndkVersion = libs.versions.android.ndk.get()
}
