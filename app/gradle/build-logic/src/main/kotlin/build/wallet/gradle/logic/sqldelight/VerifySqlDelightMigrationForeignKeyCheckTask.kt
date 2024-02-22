package build.wallet.gradle.logic.sqldelight

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

open class VerifySqlDelightMigrationForeignKeyCheckTask : DefaultTask() {
  init {
    group = "Sqldelight"
    description =
      "Verifies that all .sqm migration files run foreign key check at the end of the migration."
  }

  private val projectDir = project.projectDir

  @TaskAction
  fun verifyAction() {
    val migrationFiles = projectDir.walkTopDown().filter { it.extension == "sqm" }

    migrationFiles.forEach { file ->
      val lines = file.readLines()
      if (lines.isEmpty() || lines.last() != "PRAGMA foreign_key_check;") {
        error(
          "${file.absoluteFile} migration is missing foreign key check: 'PRAGMA foreign_key_check;'"
        )
      }
    }
  }
}
