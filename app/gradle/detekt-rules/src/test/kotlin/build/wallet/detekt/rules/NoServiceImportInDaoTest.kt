package build.wallet.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals

class NoServiceImportInDaoTest {
  private val rule = NoServiceImportInDao(Config.empty)

  @Test
  fun `reports service import in Dao class`() {
    val code = """
      package build.wallet.transactions.dao

      import build.wallet.transactions.TransactionService

      class TransactionDaoImpl {
        fun save() {}
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(1, findings.size)
    assertEquals("NoServiceImportInDao", findings.first().id)
  }

  @Test
  fun `reports service import when class name contains Dao`() {
    val code = """
      package build.wallet.transactions

      import build.wallet.auth.AuthenticationService

      class TransactionDaoImpl {
        fun save() {}
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(1, findings.size)
  }

  @Test
  fun `reports multiple service imports in Dao file`() {
    val code = """
      package build.wallet.transactions.dao

      import build.wallet.transactions.TransactionService
      import build.wallet.auth.AuthenticationServiceImpl

      class TransactionDaoImpl {
        fun save() {}
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(2, findings.size)
  }

  @Test
  fun `does not report non-service imports in Dao class`() {
    val code = """
      package build.wallet.transactions.dao

      import build.wallet.database.SqlDriver
      import build.wallet.transactions.Transaction

      class TransactionDaoImpl {
        fun save() {}
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(0, findings.size)
  }

  @Test
  fun `does not report service import in non-Dao class`() {
    val code = """
      package build.wallet.transactions

      import build.wallet.auth.AuthenticationService

      class TransactionRepository {
        fun get() {}
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(0, findings.size)
  }

  @Test
  fun `does not report in unrelated file`() {
    val code = """
      package build.wallet.ui

      import build.wallet.transactions.TransactionService

      class MyScreen {
        fun render() {}
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(0, findings.size)
  }
}
