package build.wallet.ksp.util

import com.google.devtools.ksp.symbol.ClassKind.CLASS
import com.google.devtools.ksp.symbol.ClassKind.INTERFACE
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier.ABSTRACT

/**
 * Returns true if this type is an interface.
 */
val KSType.isInterface: Boolean get() = (declaration as? KSClassDeclaration)?.classKind == INTERFACE

/**
 * Returns true if this type is an abstract class.
 */
val KSType.isAbstractClass: Boolean
  get() = (declaration as? KSClassDeclaration)?.classKind == CLASS &&
    declaration.modifiers.contains(ABSTRACT)

/**
 * Returns this if this type has parameters (ie uses generics).
 */
val KSType.hasTypeParameters: Boolean get() = declaration.typeParameters.isNotEmpty()
