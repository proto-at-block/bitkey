package build.wallet.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals

class NoKotlinResultTest {
  private val rule = NoKotlinResult(Config.empty)

  @Test
  fun `reports kotlin Result import`() {
    val code = """
      import kotlin.Result

      class MyService {
        fun doWork(): Result<String> = Result.success("ok")
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(1, findings.size)
    assertEquals("NoKotlinResult", findings.first().id)
  }

  @Test
  fun `does not report michaelbull Result import`() {
    val code = """
      import com.github.michaelbull.result.Result

      class MyService {
        fun doWork(): Result<String, Error> = Ok("ok")
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(0, findings.size)
  }

  @Test
  fun `does not report other kotlin imports`() {
    val code = """
      import kotlin.collections.List
      import kotlin.String

      class MyService {
        fun doWork(): List<String> = emptyList()
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(0, findings.size)
  }
}
