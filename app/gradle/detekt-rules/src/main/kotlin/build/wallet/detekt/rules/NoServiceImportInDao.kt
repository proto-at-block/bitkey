package build.wallet.detekt.rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective

/**
 * Detects DAO classes that import from service packages.
 *
 * DAOs are data-access only and must not consume services. If a DAO needs service
 * logic, that logic should be moved to a service that orchestrates the DAO.
 *
 * Detection: Files containing classes with "Dao" in their name (or in packages
 * matching `*dao*`) that import from service packages.
 *
 * <noncompliant>
 * import build.wallet.transactions.TransactionService
 *
 * class TransactionDaoImpl { /* ... */ }
 * </noncompliant>
 *
 * <compliant>
 * import build.wallet.transactions.TransactionRepository
 * import build.wallet.database.SqlDriver
 *
 * class TransactionDaoImpl { /* ... */ }
 * </compliant>
 */
class NoServiceImportInDao(config: Config = Config.empty) : Rule(config) {
  override val issue = Issue(
    id = javaClass.simpleName,
    severity = Severity.Defect,
    description = "DAOs must not import service packages. " +
      "Move this logic to a service that orchestrates the DAO.",
    debt = Debt.TWENTY_MINS
  )

  private var isDaoFile = false

  override fun visitKtFile(file: KtFile) {
    isDaoFile = false

    // Check if the filename or package contains "dao" (case-insensitive)
    val packageName = file.packageFqName.asString().lowercase()
    val filePath = file.name.lowercase()
    if (packageName.contains("dao") || filePath.contains("dao")) {
      isDaoFile = true
    }

    // Also check class declarations for Dao in the name
    if (!isDaoFile) {
      isDaoFile = file.declarations
        .filterIsInstance<KtClass>()
        .any { it.name?.contains("Dao") == true }
    }

    super.visitKtFile(file)
  }

  override fun visitImportDirective(importDirective: KtImportDirective) {
    super.visitImportDirective(importDirective)

    if (!isDaoFile) return

    val importPath = importDirective.importPath?.pathStr ?: return

    // Check if the import references a service package or class
    if (isServiceImport(importPath)) {
      report(
        CodeSmell(
          issue = issue,
          entity = Entity.from(importDirective),
          message = "DAOs must not import service packages. " +
            "Import '$importPath' references a service. " +
            "Move this logic to a service that orchestrates the DAO."
        )
      )
    }
  }

  private fun isServiceImport(importPath: String): Boolean {
    // Match imports containing "Service" as a class name component
    // e.g., build.wallet.transactions.TransactionService
    // e.g., build.wallet.auth.AuthenticationServiceImpl
    val segments = importPath.split(".")
    return segments.any { segment ->
      segment.endsWith("Service") ||
        segment.endsWith("ServiceImpl") ||
        (segment.contains("Service") && segment[0].isUpperCase())
    }
  }
}
