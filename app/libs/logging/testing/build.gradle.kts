import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.libs.loggingPublic)
      }
    }

    val commonJvmMain by getting {
      dependencies {
        implementation(libs.jvm.ktor.client.okhttp)
        // To suppress 'SLF4J: No SLF4J providers were found.' warning in logs.
        implementation(libs.jvm.slf4j.simple)
      }
    }
  }
}
