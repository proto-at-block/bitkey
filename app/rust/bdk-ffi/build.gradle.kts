import build.wallet.gradle.logic.extensions.targets
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
  id("build.wallet.kmp")
  id("build.wallet.android.lib")
  id("org.jetbrains.kotlin.plugin.atomicfu") version "2.1.20"
}

kotlin {
  targets(android = true, ios = true, jvm = true)

  sourceSets {
    val uniffiBindingsDir = layout.buildDirectory.dir("generated/uniffi")

    val commonMain by getting {
      kotlin.srcDir(uniffiBindingsDir.map { it.dir("commonMain/kotlin") })

      dependencies {
        implementation(libs.kmp.kotlin.coroutines)
        implementation(libs.kmp.kotlin.atomicfu)
      }
    }

    val androidMain by getting {
      kotlin.srcDir(uniffiBindingsDir.map { it.dir("androidMain/kotlin") })

      dependencies {
        implementation("net.java.dev.jna:jna:5.16.0@aar")
        implementation(libs.android.annotations)
      }
    }

    val jvmMain by getting {
      kotlin.srcDir(uniffiBindingsDir.map { it.dir("jvmMain/kotlin") })

      dependencies {
        implementation(libs.jvm.jna)
      }
    }

    val iosMain by getting {
      kotlin.srcDir(uniffiBindingsDir.map { it.dir("nativeMain/kotlin") })
    }
  }
}

val uniffiBindingsDir = layout.buildDirectory.dir("generated/uniffi")
val nativeInteropDir = uniffiBindingsDir.map { it.dir("nativeInterop/cinterop") }
val bdkCinteropHeader = nativeInteropDir.map { it.file("headers/bdk/bdk.h") }
val bdkCinteropDefFile = nativeInteropDir.map { it.file("bdk.def") }

kotlin.targets.withType<KotlinNativeTarget>().configureEach {
  compilations.getByName("main") {
    cinterops.register("bdk") {
      packageName("bdk.cinterop")
      header(bdkCinteropHeader)
      defFile(bdkCinteropDefFile)

      val staticLibDir =
        when (konanTarget.name) {
          "ios_arm64" -> "libs/ios/iosArm64"
          "ios_simulator_arm64" -> "libs/ios/iosSimulatorArm64"
          else -> "libs/ios/${konanTarget.name}"
        }

      extraOpts("-libraryPath", layout.projectDirectory.dir(staticLibDir).asFile.absolutePath)
    }
  }
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
  compilerOptions {
    freeCompilerArgs.add("-Xexpect-actual-classes")
  }
}

tasks.named<Jar>("jvmJar") {
  from(layout.projectDirectory.dir("libs/macos/arm64")) {
    include("libbdk.dylib")
    into("darwin-aarch64")
  }
  from(layout.projectDirectory.dir("libs/macos/arm64")) {
    include("libbdk.dylib")
    into("darwin-arm64")
  }
  from(layout.projectDirectory.dir("libs/linux/x86_64")) {
    include("libbdk.so")
    into("linux-x86-64")
  }
}

android {
  ndkVersion = libs.versions.android.ndk.get()

  sourceSets {
    getByName("main") {
      jniLibs.srcDir(layout.projectDirectory.dir("libs/android"))
    }
  }
}
