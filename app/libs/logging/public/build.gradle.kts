import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(libs.kmp.kotlin.datetime)
        api(libs.kmp.kotlin.result)
        api(libs.kmp.kermit)
        implementation(libs.kmp.big.number)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.libs.loggingTesting)
      }
    }

    val iosMain by getting {
      dependencies {
        implementation(
          libs.native.nserror.kt.map {
            // TODO: Once we update to Kotlin 1.9.22, we should be able to remove this line.
            //  until then we need to make sure stdlib doesn't get pulled in
            //  as it breaks commonMain in serialization-public
            it.copy().exclude("org.jetbrains.kotlin")
          }
        )
      }
    }
  }
}
