import build.wallet.gradle.dependencylocking.util.ifMatches
import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  id("build.wallet.sqldelight")
}

sqldelight {
  linkSqlite = false
  databases {
    create("BitkeyDatabase") {
      packageName.set("build.wallet.database.sqldelight")
      schemaOutputDirectory.set(File("src/commonMain/sqldelight/databases"))
      verifyMigrations.set(true)
    }
    create("BitkeyDebugDatabase") {
      packageName.set("build.wallet.database.sqldelight")
      schemaOutputDirectory.set(File("src/commonMain/sqldelightDebug/databases"))
      srcDirs.setFrom(File("src/commonMain/sqldelightDebug/"))
      verifyMigrations.set(true)
    }
  }
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.availabilityPublic)
        api(projects.shared.bitcoinPrimitivesPublic)
        api(projects.shared.bitkeyPrimitivesPublic)
        api(projects.shared.cloudBackupPublic)
        api(projects.shared.emailPublic)
        api(projects.shared.platformPublic)
        api(projects.shared.analyticsPublic)
        api(projects.shared.homePublic)
        api(projects.shared.sqldelightPublic)
        api(projects.shared.moneyPublic)
        api(projects.shared.onboardingPublic)
        api(projects.shared.phoneNumberPublic)
        api(projects.shared.fwupPublic)
        api(projects.shared.recoveryPublic)
        api(libs.kmp.kotlin.datetime)
        implementation(projects.shared.loggingPublic)
        implementation(projects.shared.firmwarePublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.bitcoinFake)
        implementation(projects.shared.bitkeyPrimitivesFake)
        implementation(projects.shared.bitcoinPrimitivesFake)
      }
    }

    jvmTest {}
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
