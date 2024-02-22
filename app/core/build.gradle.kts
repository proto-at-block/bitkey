import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp.rust")
  id("build.wallet.android.lib")
}

kotlin {
  targets(android = true, jvm = true)
}

val hermitDir = rootDir.parentFile.resolve("bin")

rust {
  packageName = "ffi"
  libraryName = "core"
  cargoPath = hermitDir.resolve("cargo")
  rustupPath = hermitDir.resolve("rustup")
  rustProjectFiles.from(
    provider {
      val buildDirectory = layout.buildDirectory.get().asFile

      // This is only an approximation. The Rust compiler also has a cache so the performance impact of the compile task being incorrectly invalidated is not that high.
      projectDir.listFiles()!!.filter { !it.isHidden() && it != buildDirectory }
    }
  )

  jvm {
    host()
  }

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
