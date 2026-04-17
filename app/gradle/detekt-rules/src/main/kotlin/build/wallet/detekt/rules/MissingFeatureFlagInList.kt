package build.wallet.detekt.rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

class MissingFeatureFlagInList(
  config: Config = Config.empty,
) : Rule(config) {
  override val issue: Issue = Issue(
    id = "MissingFeatureFlagInList",
    severity = Severity.Defect,
    description =
      "FeatureFlagsComponent.featureFlags() must include all provided FeatureFlag bindings.",
    debt = Debt.FIVE_MINS
  )

  override fun visitKtFile(file: org.jetbrains.kotlin.psi.KtFile) {
    val component =
      file.declarations
        .filterIsInstance<KtClass>()
        .firstOrNull { it.name == "FeatureFlagsComponent" }
        ?: return

    val functions = component.declarations.filterIsInstance<KtNamedFunction>()
    val featureFlagsFunction = functions.firstOrNull { it.name == "featureFlags" } ?: return

    val providedTypes =
      functions
        .filter { it.name != "featureFlags" && it.hasAnnotation("Provides") }
        .mapNotNull { it.returnedTypeName() }
        .filter { it.endsWith("FeatureFlag") }
        .toSet()

    if (providedTypes.isEmpty()) return

    val paramTypesByName =
      featureFlagsFunction.valueParameters
        .mapNotNull { param ->
          val name = param.name ?: return@mapNotNull null
          val typeName = param.typeReference?.text?.substringAfterLast('.') ?: return@mapNotNull null
          name to typeName
        }
        .toMap()

    val listParamNames = featureFlagsFunction.listOfArgumentNames() ?: return

    val listedTypes = listParamNames.mapNotNull { paramTypesByName[it] }.toSet()
    val missingTypes = providedTypes - listedTypes

    if (missingTypes.isNotEmpty()) {
      val message =
        "featureFlags() is missing: ${missingTypes.sorted().joinToString(", ")}"
      report(CodeSmell(issue, Entity.from(featureFlagsFunction), message))
    }
  }
}

private fun KtNamedFunction.hasAnnotation(simpleName: String): Boolean {
  return annotationEntries.any { it.shortName?.asString() == simpleName }
}

private fun KtNamedFunction.returnedTypeName(): String? {
  val explicitType = typeReference?.text?.substringAfterLast('.')
  if (explicitType != null) return explicitType

  val callExpression = bodyExpression?.asCallExpression()
  return callExpression?.calleeExpression?.text?.substringAfterLast('.')
}

private fun KtNamedFunction.listOfArgumentNames(): Set<String>? {
  val listCall =
    bodyExpression
      ?.collectDescendantsOfType<KtCallExpression> { call ->
        call.calleeExpression?.text == "listOf"
      }
      ?.firstOrNull()
      ?: return null

  return listCall.valueArguments
    .mapNotNull { argument ->
      (argument.getArgumentExpression() as? KtNameReferenceExpression)?.getReferencedName()
    }
    .toSet()
}

private fun KtExpression.asCallExpression(): KtCallExpression? {
  return when (this) {
    is KtCallExpression -> this
    is KtDotQualifiedExpression -> selectorExpression as? KtCallExpression
    is KtReturnExpression -> returnedExpression?.asCallExpression()
    is KtBlockExpression -> statements.lastOrNull()?.asCallExpression()
    else -> null
  }
}
