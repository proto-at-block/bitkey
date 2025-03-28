import build.wallet.gradle.dependencylocking.util.ifMatches
import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.di")
  id("build.wallet.sqldelight")
}

sqldelight {
  linkSqlite = false
  databases {
    create("BitkeyDatabase") {
      packageName.set("build.wallet.database.sqldelight")
      schemaOutputDirectory.set(File("src/commonMain/sqldelight/databases"))
      verifyMigrations.set(true)
      dialect(libs.kmp.sqldelight.sqlite.dialect)
    }
    create("BitkeyDebugDatabase") {
      packageName.set("build.wallet.database.sqldelight")
      schemaOutputDirectory.set(File("src/commonMain/sqldelightDebug/databases"))
      srcDirs.setFrom(File("src/commonMain/sqldelightDebug/"))
      verifyMigrations.set(true)
      dialect(libs.kmp.sqldelight.sqlite.dialect)
    }
  }
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.domain.availabilityPublic)
        api(projects.libs.bitcoinPrimitivesPublic)
        api(projects.domain.bitkeyPrimitivesPublic)
        api(projects.domain.cloudBackupPublic)
        api(projects.libs.platformPublic)
        api(projects.domain.analyticsPublic)
        api(projects.libs.sqldelightPublic)
        api(projects.libs.moneyPublic)
        api(projects.domain.onboardingPublic)
        api(projects.domain.partnershipsPublic)
        api(projects.libs.contactMethodPublic)
        api(projects.domain.recoveryPublic)
        api(projects.domain.coachmarkPublic)
        api(projects.domain.walletPublic)
        api(projects.shared.priceChartPublic)
        api(libs.kmp.kotlin.datetime)
        implementation(projects.libs.loggingPublic)
        implementation(projects.domain.hardwarePublic)
        implementation(projects.libs.stdlibPublic)
        implementation(projects.shared.priceChartPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.domain.walletFake)
        implementation(projects.domain.bitkeyPrimitivesFake)
        implementation(projects.libs.bitcoinPrimitivesFake)
      }
    }

    val commonJvmTest by getting {
      resources.srcDir("${project.projectDir}/src/commonMain/sqldelight")
        .include("databases/*", "fixtures/*")

      dependencies {
        implementation(projects.libs.sqldelightTesting)
        implementation(libs.jvm.sqldelight.driver)
      }
    }
  }
}

customDependencyLocking {
  sqldelight.databases.configureEach {
    val databaseName = name

    configurations.configureEach {
      ifMatches {
        nameIs("${databaseName}DialectClasspath")
      } then {
        dependencyLockingGroup = dependencyLockingGroups.maybeCreate("SQLDelight-DialectClasspath")
      }

      ifMatches {
        nameIs("${databaseName}IntellijEnv", "${databaseName}MigrationEnv")
      } then {
        dependencyLockingGroup = dependencyLockingGroups.maybeCreate("SQLDelight-InternalClasspath")
      }
    }
  }
}
