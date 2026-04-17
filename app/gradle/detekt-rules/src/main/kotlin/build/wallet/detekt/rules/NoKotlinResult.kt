package build.wallet.detekt.rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtImportDirective

/**
 * Detects imports of `kotlin.Result` which should not be used in the Bitkey codebase.
 *
 * Use `com.github.michaelbull.result.Result` instead. Kotlin's built-in Result does not
 * support custom failure types and is not coroutine-friendly (swallows CancellationException).
 *
 * <noncompliant>
 * import kotlin.Result
 * </noncompliant>
 *
 * <compliant>
 * import com.github.michaelbull.result.Result
 * </compliant>
 */
class NoKotlinResult(config: Config = Config.empty) : Rule(config) {
  override val issue = Issue(
    id = javaClass.simpleName,
    severity = Severity.Defect,
    description = "Use com.github.michaelbull.result.Result instead of kotlin.Result.",
    debt = Debt.TEN_MINS
  )

  override fun visitImportDirective(importDirective: KtImportDirective) {
    super.visitImportDirective(importDirective)

    val importPath = importDirective.importPath?.pathStr ?: return

    if (importPath == "kotlin.Result") {
      report(
        CodeSmell(
          issue = issue,
          entity = Entity.from(importDirective),
          message = "Use com.github.michaelbull.result.Result instead of kotlin.Result."
        )
      )
    }
  }
}
