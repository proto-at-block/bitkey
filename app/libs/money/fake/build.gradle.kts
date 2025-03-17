import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(libs.kmp.big.number)
        api(libs.kmp.kotlin.datetime)
        api(projects.libs.amountPublic)
        api(projects.libs.platformFake)
        // TODO: remove dependency on :impl.
        implementation(projects.libs.amountImpl) {
          because("Depends on DoubleFormatterImpl.")
        }
        // TODO: remove dependency on :impl.
        implementation(projects.libs.moneyImpl) {
          because("Depends on MoneyDisplayFormatterImpl and MoneyFormatterDefinitionsImpl.")
        }
      }
    }

    commonTest {
      dependencies {
        implementation(projects.libs.testingPublic)
      }
    }
  }
}
