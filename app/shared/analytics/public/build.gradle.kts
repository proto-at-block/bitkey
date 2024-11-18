import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.kotlin.serialization)
}

buildLogic {
  proto {
    wire {
      kotlin {
        sourcePath {
          srcDir("${project.rootDir.parent}/proto/build/wallet/analytics/v1/")
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
        api(libs.kmp.big.number)
        api(libs.kmp.wire.runtime)
        api(projects.shared.moneyPublic)
        api(projects.shared.platformPublic)
        api(projects.shared.queueProcessorPublic)
        implementation(projects.shared.featureFlagPublic)
        implementation(libs.kmp.kotlin.serialization.core)
        implementation(libs.kmp.okio)
      }
    }
    commonTest {
      dependencies {
        api(projects.shared.featureFlagFake)
      }
    }
  }
}
