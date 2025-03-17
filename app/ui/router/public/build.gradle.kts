import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

buildLogic {
  proto {
    wire {
      kotlin {
        sourcePath {
          srcDir("${project.rootDir.parent}/proto/build/wallet/navigation/v1/")
        }
      }
    }
  }
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(libs.kmp.ktor.client.core)
      }
    }

    commonTest {}
  }
}
