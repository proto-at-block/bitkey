package build.wallet.ksp.util

import com.google.devtools.ksp.symbol.KSAnnotation
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ksp.toClassName

/**
 * Returns `true` if [this] annotation is of type [T].
 */
inline fun <reified T> KSAnnotation.isOfType(): Boolean {
  return annotationType.resolve().declaration.qualifiedName!!.asString() == T::class.qualifiedName
}

fun KSAnnotation.toClassName(): ClassName = annotationType.resolve().toClassName()
