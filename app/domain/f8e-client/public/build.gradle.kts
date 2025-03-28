import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.redacted")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(libs.kmp.kotlin.result)
        api(libs.kmp.ktor.client.core)
        api(projects.domain.accountPublic)
        api(projects.domain.analyticsPublic)
        api(projects.domain.availabilityPublic)
        api(projects.domain.bitkeyPrimitivesPublic)
        api(projects.libs.frostPublic)
        api(projects.libs.ktorClientPublic)
        api(projects.libs.loggingPublic)
        api(projects.domain.mobilePayPublic)
        api(projects.domain.partnershipsPublic)
        api(projects.libs.timePublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.domain.bitkeyPrimitivesFake)
        implementation(projects.libs.testingPublic)
      }
    }
  }
}
