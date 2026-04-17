package build.wallet.detekt.rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Detects AppWorker `runStrategy` declarations that combine `RunStrategy.Startup()` with an
 * `OnEvent` observer filtering for `AppSessionState.FOREGROUND`.
 *
 * This combination causes `executeWork()` to fire twice on app launch because
 * `AppSessionManagerImpl` initializes its state to `FOREGROUND`, so the `OnEvent` observer
 * immediately emits alongside the `Startup` observer.
 *
 * The fix is to remove `Startup()` — the `OnEvent(FOREGROUND)` already handles the initial run.
 *
 * <noncompliant>
 * override val runStrategy: Set<RunStrategy> = setOf(
 *   RunStrategy.Startup(),
 *   RunStrategy.OnEvent(
 *     observer = appSessionManager.appSessionState.filter { it == AppSessionState.FOREGROUND }
 *   )
 * )
 * </noncompliant>
 *
 * <compliant>
 * override val runStrategy: Set<RunStrategy> = setOf(
 *   RunStrategy.OnEvent(
 *     observer = appSessionManager.appSessionState.filter { it == AppSessionState.FOREGROUND }
 *   )
 * )
 * </compliant>
 */
class RedundantStartupWithForegroundEvent(config: Config = Config.empty) : Rule(config) {
  override val issue = Issue(
    id = javaClass.simpleName,
    severity = Severity.Defect,
    description = "runStrategy combines Startup() with an OnEvent(FOREGROUND) observer. " +
      "This causes executeWork() to fire twice on app launch. " +
      "Remove Startup() — the OnEvent(FOREGROUND) already handles the initial run.",
    debt = Debt.TEN_MINS
  )

  override fun visitProperty(property: KtProperty) {
    super.visitProperty(property)

    if (property.name != "runStrategy") return

    // Check both direct initializer and custom getter body
    val expression = property.initializer
      ?: property.getter?.bodyExpression
      ?: return

    val allRefs = expression.collectDescendantsOfType<KtNameReferenceExpression>()
    val hasStartup = allRefs.any { it.getReferencedName() == "Startup" }

    // Find OnEvent call expressions and check if FOREGROUND is within one
    val hasOnEventWithForeground = expression
      .collectDescendantsOfType<KtCallExpression>()
      .any { call ->
        val calleeNames = call.calleeExpression
          ?.collectDescendantsOfType<KtNameReferenceExpression>()
          ?.map { it.getReferencedName() }
          ?.toSet()
          ?: emptySet()
        "OnEvent" in calleeNames &&
          call.valueArgumentList
            ?.collectDescendantsOfType<KtNameReferenceExpression>()
            ?.any { it.getReferencedName() == "FOREGROUND" } == true
      }

    if (hasStartup && hasOnEventWithForeground) {
      report(
        CodeSmell(
          issue = issue,
          entity = Entity.from(property),
          message = "runStrategy combines Startup() with an OnEvent(FOREGROUND) observer, " +
            "causing executeWork() to fire twice on app launch. Remove Startup()."
        )
      )
    }
  }
}
