import build.wallet.gradle.logic.extensions.targets
import kotlinx.benchmark.gradle.benchmark

plugins {
  id("build.wallet.kmp")
  id("build.wallet.redacted")
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.kotlinx.benchmark)
}

kotlin {
  targets(ios = true, jvm = true)

  jvm {
    compilations.create("benchmark") {
      associateWith(this@jvm.compilations.named("test").get())
    }
  }

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.accountPublic)
        api(projects.shared.analyticsPublic)
        api(projects.shared.authPublic)
        api(projects.shared.databasePublic)
        api(projects.shared.notificationsPublic)
        api(projects.shared.platformPublic)
        implementation(libs.kmp.ktor.client.content.negotiation)
        implementation(libs.kmp.ktor.client.auth)
        implementation(libs.kmp.ktor.client.core)
        implementation(libs.kmp.ktor.client.json)
        implementation(libs.kmp.kotlin.serialization.json)
        implementation(projects.shared.datadogPublic)
        implementation(projects.shared.serializationPublic)
        // For SocialRecoveryServiceFake
        implementation(projects.shared.ktorClientFake)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.analyticsFake)
        implementation(projects.shared.availabilityFake)
        implementation(projects.shared.authFake)
        implementation(projects.shared.encryptionFake)
        implementation(projects.shared.coroutinesTesting)
        implementation(projects.shared.f8eClientFake)
        implementation(projects.shared.keyboxFake)
        implementation(projects.shared.platformFake)
        implementation(projects.shared.testingPublic)
        implementation(libs.kmp.test.ktor.client.mock)
        implementation(projects.shared.accountFake)
      }
    }

    val jvmBenchmark by getting {
      dependencies {
        implementation(libs.kmp.benchmark)
      }
    }

    val commonJvmMain by getting {
      dependencies {
        api(libs.jvm.ktor.client.okhttp)
      }
    }
    val iosMain by getting {
      dependencies {
        api(libs.native.ktor.client.darwin)
      }
    }
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

benchmark {
  targets {
    register("jvmBenchmark")
  }
  configurations {
    named("main") {
      mode = "AverageTime"
      iterationTime = 5
      iterationTimeUnit = "s"
      outputTimeUnit = "ms"
    }
  }
}
