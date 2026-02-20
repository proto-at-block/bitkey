package build.wallet.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals

class NoFocusedKotestTestsTest {
  private val rule = NoFocusedKotestTests(Config.empty)

  @Test
  fun `reports focused test with f prefix`() {
    val code = """
      class MyTests {
        fun setup() {
          test("f: this is a focused test") { }
        }
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(1, findings.size)
    assertEquals("NoFocusedKotestTests", findings.first().id)
  }

  @Test
  fun `reports focused test with f prefix and space`() {
    val code = """
      class MyTests {
        fun setup() {
          test("f:this is a focused test") { }
        }
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(1, findings.size)
  }

  @Test
  fun `does not report regular tests`() {
    val code = """
      class MyTests {
        fun setup() {
          test("this is a regular test") { }
          test("another test") { }
        }
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(0, findings.size)
  }

  @Test
  fun `does not report tests with f in the middle`() {
    val code = """
      class MyTests {
        fun setup() {
          test("this test uses f: in the middle") { }
          test("testing feature flag") { }
        }
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(0, findings.size)
  }

  @Test
  fun `reports focused context`() {
    val code = """
      class MyTests {
        fun setup() {
          context("f: focused context") { }
        }
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(1, findings.size)
  }

  @Test
  fun `reports focused describe`() {
    val code = """
      class MyTests {
        fun setup() {
          describe("f: focused describe") { }
        }
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(1, findings.size)
  }

  @Test
  fun `reports multiple focused tests`() {
    val code = """
      class MyTests {
        fun setup() {
          test("f: first focused") { }
          test("normal test") { }
          test("f: second focused") { }
        }
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(2, findings.size)
  }

  @Test
  fun `does not report unrelated function calls`() {
    val code = """
      class MyClass {
        fun process(s: String) = s
        fun example() {
          process("f: some string")
        }
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(0, findings.size)
  }
}
