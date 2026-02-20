package build.wallet.detekt.rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPostfixExpression

/**
 * Detects `collectAsState()` or `collectAsStateWithLifecycle()` calls where the Flow receiver
 * contains a function call that is not wrapped in `remember { }`.
 *
 * When a Flow-returning function is called during recomposition without `remember`, a new Flow
 * instance is created each time. This causes:
 * - Subscription churn (previous subscription cancelled, new one started)
 * - UI flicker (briefly shows initial value on each recomposition)
 * - Wasted resources from repeated subscriptions
 *
 * <noncompliant>
 * // Function call as receiver - creates new Flow on each recomposition
 * val state by service.getFlow().collectAsState(initial = "")
 *
 * // Operator chains create new Flows
 * val state by service.flow.map { it.name }.collectAsState(initial = "")
 * </noncompliant>
 *
 * <compliant>
 * // Property access - stable reference
 * val state by service.stateFlow.collectAsState()
 *
 * // Remember wrapping the Flow source
 * val state by remember { service.getFlow() }.collectAsState(initial = "")
 *
 * // Deep property access is also safe
 * val state by viewModel.repository.cache.stateFlow.collectAsState()
 * </compliant>
 */
class NoUnrememberedCollectAsState(config: Config = Config.empty) : Rule(config) {
  override val issue = Issue(
    id = javaClass.simpleName,
    severity = Severity.Warning,
    description = "Flow-returning function called in collectAsState() without remember { } " +
      "creates a new Flow on each recomposition, causing subscription churn and UI flicker. " +
      "Wrap the Flow source in remember { } or use a stable property reference.",
    debt = Debt.TWENTY_MINS
  )

  private var hasCollectAsStateImport = false

  override fun visitKtFile(file: KtFile) {
    // Reset state for each file
    hasCollectAsStateImport = false
    super.visitKtFile(file)
  }

  override fun visitImportDirective(importDirective: KtImportDirective) {
    super.visitImportDirective(importDirective)
    val importPath = importDirective.importPath ?: return
    val pathStr = importPath.pathStr
    val isWildcard = importPath.isAllUnder
    // fqName is the package name without the *
    val fqName = importPath.fqName.asString()

    // Check for explicit imports of collectAsState variants
    if (pathStr == "androidx.compose.runtime.collectAsState" ||
      pathStr == "androidx.lifecycle.compose.collectAsStateWithLifecycle"
    ) {
      hasCollectAsStateImport = true
    }

    // Check for wildcard imports from the relevant packages
    if (isWildcard) {
      if (fqName == "androidx.compose.runtime" ||
        fqName == "androidx.lifecycle.compose"
      ) {
        hasCollectAsStateImport = true
      }
    }
  }

  override fun visitCallExpression(expression: KtCallExpression) {
    super.visitCallExpression(expression)

    // Only check if the file imports collectAsState
    if (!hasCollectAsStateImport) return

    val calleeName = expression.calleeExpression?.text ?: return
    if (calleeName != "collectAsState" && calleeName != "collectAsStateWithLifecycle") return

    // Check if it's a method call (has a receiver via dot notation)
    val parent = expression.parent
    if (parent !is KtDotQualifiedExpression) return

    // Make sure collectAsState is the selector (right side of the dot)
    if (parent.selectorExpression != expression) return

    // Get the receiver (left side of the dot)
    val receiver = parent.receiverExpression

    // Check if receiver contains unsafe function calls
    if (receiverContainsFunctionCall(receiver)) {
      report(
        CodeSmell(
          issue = issue,
          entity = Entity.from(expression),
          message = "Flow-returning function called in $calleeName() without remember { }. " +
            "This creates a new Flow on each recomposition. " +
            "Wrap the Flow source in remember { } or use a stable property reference."
        )
      )
    }
  }

  /**
   * Checks if the receiver expression contains a function call that would create
   * a new instance on each recomposition.
   *
   * Safe patterns (returns false):
   * - Property access: `service.stateFlow`
   * - Remember wrapping: `remember { service.getFlow() }`
   * - Deep property access: `viewModel.repo.cache.stateFlow`
   *
   * Unsafe patterns (returns true):
   * - Direct function call: `service.getFlow()`
   * - Operator chains: `flow.map { }.filter { }`
   * - Nested function calls: `getService().getFlow()`
   */
  private fun receiverContainsFunctionCall(receiver: KtExpression): Boolean {
    // Unwrap common wrapper expressions
    val unwrapped = unwrapExpression(receiver)

    return when (unwrapped) {
      is KtCallExpression -> {
        // Function call - check if it's a remember { } wrapper
        val callee = unwrapped.calleeExpression?.text ?: ""
        !callee.startsWith("remember")
      }
      is KtDotQualifiedExpression -> {
        // Check the selector (right side) for function calls
        val selector = unwrapExpression(unwrapped.selectorExpression)
        if (selector is KtCallExpression) {
          val callee = selector.calleeExpression?.text ?: ""
          // remember is safe, anything else is unsafe
          if (!callee.startsWith("remember")) return true
        }
        // Recurse on receiver (left side) to catch nested chains
        receiverContainsFunctionCall(unwrapped.receiverExpression)
      }
      else -> false // Simple name reference (variable/property) is safe
    }
  }

  /**
   * Unwraps common wrapper expressions to get to the underlying expression.
   * Handles parentheses, postfix operators (!!), etc.
   */
  private fun unwrapExpression(expression: KtExpression?): KtExpression? {
    return when (expression) {
      is KtParenthesizedExpression -> unwrapExpression(expression.expression)
      is KtPostfixExpression -> unwrapExpression(expression.baseExpression)
      else -> expression
    }
  }
}
