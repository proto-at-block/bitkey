import build.wallet.gradle.logic.extensions.allTargets
import kotlinx.benchmark.gradle.benchmark

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
  id("build.wallet.redacted")
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.kotlinx.benchmark)
}

kotlin {
  allTargets()

  jvm {
    compilations.create("benchmark") {
      associateWith(this@jvm.compilations.named("test").get())
    }
  }

  sourceSets {
    commonMain {
      dependencies {
        api(projects.libs.datadogPublic)
        api(projects.domain.analyticsPublic)
        api(projects.domain.authPublic)
        api(projects.domain.databasePublic)
        api(projects.domain.notificationsPublic)
        api(projects.libs.platformPublic)
        implementation(libs.kmp.ktor.client.content.negotiation)
        implementation(libs.kmp.ktor.client.auth)
        implementation(libs.kmp.ktor.client.core)
        implementation(libs.kmp.ktor.client.json)
        implementation(libs.kmp.kotlin.serialization.json)
        implementation(projects.libs.stdlibPublic)
        // For SocialRecoveryServiceFake
        implementation(projects.libs.ktorClientFake)
        implementation(projects.domain.relationshipsPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.domain.analyticsFake)
        implementation(projects.domain.availabilityFake)
        implementation(projects.domain.authFake)
        implementation(projects.libs.encryptionFake)
        implementation(projects.domain.f8eClientFake)
        implementation(projects.domain.firmwareFake)
        implementation(projects.domain.keyboxFake)
        implementation(projects.libs.platformFake)
        implementation(projects.libs.testingPublic)
        implementation(libs.kmp.test.ktor.client.mock)
        implementation(projects.domain.accountFake)
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

    val jvmIntegrationTest by getting {
      dependencies {
        implementation(projects.libs.testingPublic)
        implementation(projects.shared.integrationTestingPublic)
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
