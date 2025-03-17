package build.wallet.di

import software.amazon.lastmile.kotlin.inject.anvil.extend.ContributingAnnotation
import kotlin.reflect.KClass

/**
 * Generates a component interface for the annotated class and contributes a binding method to
 * the specified [scope] as a singleton. Also, binds all supertypes of the class
 * unless [boundTypes] is explicitly provided.
 *
 * The [BitkeyInject] annotation is the preferred way to bind implementations,
 * acting as a shorthand for combining [SingleIn] and [ContributesBinding].
 *
 * Key benefits of this annotation:
 * - Singleton implementations are common in our codebase,
 *   but it’s easy to forget to bind them as singletons. This annotation ensures all implementations are
 *   automatically bound as singletons.
 * - Reduces boilerplate when binding multiple types.
 * - Easier binding of fake implementations: see [Impl] and [Fake] qualifiers.
 *
 * This:
 * ```kotlin
 * @BitkeyInject(AppScope::class)
 * class BaseImpl : Base1, Base2
 * ```
 *
 * Is equivalent to:
 * ```kotlin
 * @Inject
 * @SingleIn(AppScope::class)
 * @ContributesBinding(AppScope::class, boundType = Base1::class)
 * @ContributesBinding(AppScope::class, boundType = Base2::class)
 * class BaseImpl : Base1, Base2
 * ```
 *
 * To bind only specific type(s), use [boundTypes]:
 * ```kotlin
 * @BitkeyInject(AppScope::class, boundTypes = [Base1::class])
 * class BaseImpl : Base1, Base2
 * ```
 *
 * This will be equivalent to:
 * ```kotlin
 * @Inject
 * @SingleIn(AppScope::class)
 * @ContributesBinding(AppScope::class, boundType = Base1::class)
 * class BaseImpl : Base1, Base2
 * ```
 *
 * @param scope The scope to contribute the binding to as a singleton.
 * @param boundTypes The types to bind the class as. By default (if empty), all supertypes are bound.
 * Otherwise if set, only the specified types are bound.
 */
@ContributingAnnotation
@Target(AnnotationTarget.CLASS)
annotation class BitkeyInject(
  val scope: KClass<*>,
  val boundTypes: Array<KClass<*>> = [],
)
