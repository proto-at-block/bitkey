import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.resultPublic)
        api(libs.kmp.kotlin.datetime)
        api(libs.kmp.kermit)

        // For NetworkingError
        implementation(projects.shared.ktorResultPublic)
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
