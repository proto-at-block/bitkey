package build.wallet.gradle.logic.sqldelight

import build.wallet.gradle.logic.gradle.apply
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

/**
 * Custom plugin to apply SqlDelight plugin and register custom tasks.
 */
internal class SqlDelightPlugin : Plugin<Project> {
  override fun apply(target: Project): Unit =
    target.run {
      pluginManager.apply<app.cash.sqldelight.gradle.SqlDelightPlugin>()

      tasks.register<VerifySqlDelightMigrationForeignKeyCheckTask>(
        "verifySqlDelightMigrationForeignKeyCheck"
      )
    }
}
