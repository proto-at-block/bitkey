package build.wallet.di.codegen.processor.util

import build.wallet.di.SingleIn
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.Annotatable
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
import software.amazon.lastmile.kotlin.inject.anvil.internal.Origin

/**
 * Adds an [Origin] annotation to the given [clazz].
 */
internal fun <T : Annotatable.Builder<T>> Annotatable.Builder<T>.addOriginAnnotation(
  clazz: KSClassDeclaration,
): T =
  addAnnotation(
    AnnotationSpec.builder(Origin::class)
      .addMember("value = %T::class", clazz.toClassName())
      .build()
  )

internal fun <T : Annotatable.Builder<T>> Annotatable.Builder<T>.addContributesToAnnotation(
  scope: KSType,
): T =
  addAnnotation(
    AnnotationSpec.builder(ContributesTo::class)
      .addMember("scope = %T::class", scope.toClassName())
      .build()
  )

internal fun <T : Annotatable.Builder<T>> Annotatable.Builder<T>.addSingleInAnnotation(
  scope: KSType,
): T =
  addAnnotation(
    AnnotationSpec.builder(SingleIn::class)
      .addMember("scope = %T::class", scope.toClassName())
      .build()
  )
