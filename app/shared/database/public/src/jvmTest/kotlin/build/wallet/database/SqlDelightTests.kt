
package build.wallet.database

import build.wallet.database.sqldelight.BitkeyDatabase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File

class SqlDelightTests : FunSpec({

  // if this test fails, you probably forgot to commit the .db file
  test("verify correct number of databases") {
    // check number of databases
    val directory = File("src/commonMain/sqldelight/databases")
    // This should actually be BitkeyDatabase.Schema.version, but we somehow skipped "20.db" so we have to subtract 1 🙃
    directory.listFiles {
        _,
        name,
      ->
      name.endsWith(".db")
    }?.size.shouldBe(BitkeyDatabase.Schema.version - 1)
  }

  // if this test fails, you're trying to update the db schema without adding a migration
  test("verify correct number of migrations") {
    // check number of migrations
    val directory = File("src/commonMain/sqldelight/migrations")
    directory.listFiles {
        _,
        name,
      ->
      name.endsWith(".sqm")
    }?.size.shouldBe(BitkeyDatabase.Schema.version - 1)
  }
})
