import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
}

kotlin {
  allTargets()

  sourceSets {
    commonTest {
      dependencies {
        implementation(projects.libs.testingPublic)
        implementation(projects.domain.securityRecommendationsFake)
      }
    }
  }
}
