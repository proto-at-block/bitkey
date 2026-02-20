package build.wallet.detekt.rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Detects Kotest focus tests that start with "f:" prefix.
 *
 * When a test name starts with "f:", Kotest runs only that test and ignores all others
 * in the test class. This is useful during development but should never be committed
 * to the codebase as it will cause other tests to be silently skipped in CI.
 *
 * See: https://kotest.io/docs/next/framework/conditional/conditional-tests-with-focus-and-bang.html
 *
 * <noncompliant>
 * class MyTests : FunSpec({
 *   test("f: this test will run exclusively") { }  // Bad: focuses this test
 *   test("this test will be skipped") { }
 * })
 * </noncompliant>
 *
 * <compliant>
 * class MyTests : FunSpec({
 *   test("this test will run") { }
 *   test("this test will also run") { }
 * })
 * </compliant>
 */
class NoFocusedKotestTests(config: Config = Config.empty) : Rule(config) {
  override val issue = Issue(
    id = javaClass.simpleName,
    severity = Severity.Defect,
    description = "Kotest focus tests (f: prefix) should not be committed. " +
      "They cause other tests in the class to be silently skipped.",
    debt = Debt.FIVE_MINS
  )

  // Kotest test function names that support focus/bang prefixes
  private val kotestTestFunctions = setOf(
    "test",
    "context",
    "describe",
    "given",
    "xtest",
    "xcontext",
    "xdescribe",
    "xgiven",
    "it",
    "xit",
    "should",
    "xshould",
    "feature",
    "xfeature",
    "scenario",
    "xscenario",
    "expect",
    "xexpect",
    "then",
    "xthen",
    "When",
    "xWhen",
    "And",
    "xAnd"
  )

  override fun visitCallExpression(expression: KtCallExpression) {
    super.visitCallExpression(expression)

    val calleeName = expression.calleeExpression?.text ?: return
    if (calleeName !in kotestTestFunctions) return

    // Get the first argument (test name)
    val arguments = expression.valueArguments
    if (arguments.isEmpty()) return

    val firstArg = arguments.first().getArgumentExpression()
    if (firstArg !is KtStringTemplateExpression) return

    val testName = firstArg.entries.joinToString("") { it.text }

    // Check for focus prefix (f: at the start of the test name)
    if (testName.startsWith("f:")) {
      report(
        CodeSmell(
          issue = issue,
          entity = Entity.from(expression),
          message = "Test '$testName' uses the focus prefix 'f:' which will cause " +
            "other tests to be skipped. Remove the 'f:' prefix before committing."
        )
      )
    }
  }
}
