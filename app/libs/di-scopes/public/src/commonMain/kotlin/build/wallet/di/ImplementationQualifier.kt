package build.wallet.di

import me.tatarka.inject.annotations.Qualifier
import kotlin.annotation.AnnotationTarget.*

/**
 * DI qualifiers for providing real [Impl] and fake [Fake] implementations.
 *
 * These qualifier are useful when an implementation (fake or real) needs to be provided
 * dynamically based on some runtime configuration.
 *
 * Example:
 * ```kotlin
 * @Impl
 * @BitkeyInject(AppScope::class)
 * class RelationshipsF8eClientImpl(): RelationshipsF8eClient
 *
 * @Fake
 * @BitkeyInject(AppScope::class)
 * class RelationshipsF8eClientFake(): RelationshipsF8eClient
 *
 * @BitkeyInject(AppScope::class)
 * class RelationshipsF8eClientProviderImpl(
 *   @Impl private val relationshipsF8eClientImpl: RelationshipsF8eClient,
 *   @Fake private val relationshipsF8eClientFake: RelationshipsF8eClient,
 * ) : RelationshipsF8eClientProvider {
 *   override suspend fun get(): RelationshipsF8eClient {
 *     return if (isUsingSocRecFakes()) {
 *       relationshipsF8eClientFake
 *     } else {
 *       relationshipsF8eClientImpl
 *     }
 *   }
 * ```
 */

@Qualifier
@Target(CLASS, PROPERTY_GETTER, FUNCTION, VALUE_PARAMETER, TYPE)
annotation class Impl

@Qualifier
@Target(CLASS, PROPERTY_GETTER, FUNCTION, VALUE_PARAMETER, TYPE)
annotation class Fake

/**
 * Qualifier for W3 hardware-specific implementations.
 * Used when providing W3-specific behavior that differs from the default W1 implementation.
 */
@Qualifier
@Target(CLASS, PROPERTY_GETTER, FUNCTION, VALUE_PARAMETER, TYPE)
annotation class W3
